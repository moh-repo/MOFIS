package com.dilaw.mohe.studentportal;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The student portal sign-in CAPTCHA: issued and checked here, never in the browser.
 *
 * <p>Why the portal has its own rather than sharing {@code LoginCaptchaService}.
 * The two are separate realms — STUDENTCORE and UMSCORE — and this keeps them
 * separate all the way down, so nothing an anonymous portal caller does can
 * reach the staff sign-in. That is not hypothetical: the staff service bounds
 * its map by clearing the whole of it once it passes a size limit, so a flood on
 * one surface would have invalidated every outstanding challenge on the other.
 * Here the bound is Caffeine's, which sheds individual entries, and there is a
 * per-address issuance budget in front of it so the bound is hard to reach at all.
 *
 * <p>The answer never leaves the server. A challenge is single use and short
 * lived, so a solved one cannot be replayed against a list of passwords, and a
 * request posted straight at {@code /auth/login} carrying none is refused before
 * the credentials are read.
 *
 * <p>State is per-process: a restart invalidates outstanding challenges, which
 * costs a student one refresh.
 */
@Service
public class StudentPortalCaptchaService {

    private static final int WIDTH = 200;
    private static final int HEIGHT = 80;
    private static final int LENGTH = 5;
    /** No 0/O/1/l/I: a challenge nobody can read is a challenge nobody passes. */
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int MAX_OUTSTANDING = 10_000;
    /** One minute, fixed: the window opens on the first issue and is forgotten a minute later. */
    private static final Duration ISSUE_WINDOW = Duration.ofMinutes(1);

    private final SecureRandom random = new SecureRandom();
    /**
     * Outstanding challenges. Caffeine rather than a plain map with a sweep: the
     * TTL is the eviction policy, and the size cap sheds individual entries
     * instead of emptying the map under load.
     */
    private final Cache<String, Challenge> outstanding;
    private final Cache<String, AtomicInteger> issuesPerIp =
            Caffeine.newBuilder().expireAfterWrite(ISSUE_WINDOW).build();
    private final Duration ttl;
    private final int maxIssuesPerMinute;

    public StudentPortalCaptchaService(
            @Value("${mohe.security.portal-captcha.ttl-minutes:3}") int ttlMinutes,
            @Value("${mohe.security.portal-captcha.max-per-minute:20}") int maxIssuesPerMinute) {
        this.ttl = Duration.ofMinutes(ttlMinutes);
        this.maxIssuesPerMinute = maxIssuesPerMinute;
        // At least a minute of retention even at a zero TTL, so the expiry decision
        // stays with solve() rather than with whether the entry survived to be read.
        this.outstanding = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofMinutes(Math.max(ttlMinutes, 1)))
                .maximumSize(MAX_OUTSTANDING)
                .build();
    }

    /** An issued challenge: its id, and the PNG the browser shows. */
    public record IssuedCaptcha(String id, byte[] png) {
    }

    /**
     * Draws a new challenge for {@code clientIp} and remembers its answer until it
     * is used or expires.
     *
     * <p>Budgeted per address. Issuing is the one anonymous call on this prefix
     * that allocates server-side state and spends CPU drawing an image, and
     * {@code LoginRateLimitFilter} counts only POSTs here, so without a budget of
     * its own a GET loop would be free. The budget is loose enough that a student
     * refreshing an unreadable image never meets it.
     *
     * @throws ResponseStatusException 429 once the address has spent its budget
     */
    public IssuedCaptcha issue(String clientIp) {
        String key = clientIp == null || clientIp.isBlank() ? "unknown" : clientIp;
        AtomicInteger issued = issuesPerIp.get(key, k -> new AtomicInteger());
        if (issued.incrementAndGet() > maxIssuesPerMinute) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Too many security codes requested. Please wait a moment and try again.");
        }

        StringBuilder text = new StringBuilder(LENGTH);
        for (int i = 0; i < LENGTH; i++) {
            text.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        String id = UUID.randomUUID().toString();
        outstanding.put(id, new Challenge(text.toString(), Instant.now().plus(ttl)));
        return new IssuedCaptcha(id, draw(text.toString()));
    }

    /**
     * Whether {@code answer} solves the challenge {@code id} names.
     *
     * <p>The id is consumed either way: a wrong answer costs a fresh challenge,
     * so one image cannot be used to walk an alphabet.
     */
    public boolean solve(String id, String answer) {
        if (id == null || id.isBlank() || answer == null || answer.isBlank()) {
            return false;
        }
        Challenge challenge = outstanding.asMap().remove(id);
        // Expired at expiresAt, not one instant after it: "valid until T" means
        // invalid at T. Caffeine expiry is not instantaneous, so the decision is
        // taken here rather than left to eviction.
        if (challenge == null || !Instant.now().isBefore(challenge.expiresAt())) {
            return false;
        }
        return challenge.text().equalsIgnoreCase(answer.trim());
    }

    /** Rotated glyphs in mixed colours over noise lines. */
    private byte[] draw(String text) {
        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(new Color(0xF8, 0xF9, 0xFA));
            g.fillRect(0, 0, WIDTH, HEIGHT);

            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 30));
            for (int i = 0; i < text.length(); i++) {
                g.setColor(glyphColour());
                AffineTransform saved = g.getTransform();
                g.translate(24 + i * 32, HEIGHT / 2 + 10);
                g.rotate((random.nextDouble() - 0.5) * 0.5);
                g.drawString(String.valueOf(text.charAt(i)), 0, 0);
                g.setTransform(saved);
            }

            g.setStroke(new BasicStroke(2f));
            for (int i = 0; i < 3; i++) {
                g.setColor(glyphColour());
                g.drawLine(random.nextInt(WIDTH), random.nextInt(HEIGHT),
                        random.nextInt(WIDTH), random.nextInt(HEIGHT));
            }
        } finally {
            g.dispose();
        }

        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not encode the CAPTCHA image", e);
        }
    }

    private Color glyphColour() {
        // Dark enough to read on the light plate, varied enough not to be one mask.
        return new Color(random.nextInt(120), random.nextInt(120), random.nextInt(120));
    }

    private record Challenge(String text, Instant expiresAt) {
    }
}
