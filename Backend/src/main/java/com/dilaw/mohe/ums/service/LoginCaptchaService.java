package com.dilaw.mohe.ums.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

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
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The sign-in CAPTCHA, issued and checked here rather than in the browser.
 *
 * <p>It used to be drawn by {@code login.component.ts} on a canvas, with the
 * expected text kept in a component field and compared in the same file. That
 * stops nobody: the answer was readable in the debugger, and a request posted
 * straight to {@code /login} never met the challenge at all. It was the same
 * defect as the attempt counter the August review found next to it — a check
 * the caller performs on itself is not a check.
 *
 * <p>Now the server draws the image, keeps the answer, and refuses a login that
 * does not carry a matching one. Ids are single use and short-lived, so a
 * solved challenge cannot be replayed against a list of passwords.
 *
 * <p>State is a map with an expiry, following {@code StudentPortalOtpService} —
 * one idiom for short-lived secrets. It is per-process: a restart invalidates
 * outstanding challenges, which costs a user one refresh.
 */
@Service
public class LoginCaptchaService {

    private static final int WIDTH = 200;
    private static final int HEIGHT = 80;
    private static final int LENGTH = 5;
    /** No 0/O/1/l/I: a challenge nobody can read is a challenge nobody passes. */
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int MAX_OUTSTANDING = 10_000;

    private final SecureRandom random = new SecureRandom();
    private final Map<String, Challenge> outstanding = new ConcurrentHashMap<>();
    private final Duration ttl;

    public LoginCaptchaService(@Value("${mohe.security.captcha.ttl-minutes:3}") int ttlMinutes) {
        this.ttl = Duration.ofMinutes(ttlMinutes);
    }

    /** An issued challenge: its id, and the PNG the browser shows. */
    public record IssuedCaptcha(String id, byte[] png) {
    }

    /**
     * Draws a new challenge and remembers its answer until it is used or expires.
     */
    public IssuedCaptcha issue() {
        sweep();
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
     * so a single image cannot be used to walk an alphabet.
     */
    public boolean solve(String id, String answer) {
        if (id == null || id.isBlank() || answer == null || answer.isBlank()) {
            return false;
        }
        Challenge challenge = outstanding.remove(id);
        // Expired at expiresAt, not one instant after it. `isAfter` made a challenge
        // valid at the exact moment it expired, which sounds like nothing and is not:
        // with a zero TTL, expiresAt is the issuing instant, and `solve` called before
        // the clock ticked accepted a challenge that had already run out. That is what
        // made LoginCaptchaServiceTest.anExpiredChallengeIsRefused fail intermittently
        // — it passed or failed on whether the clock happened to advance between two
        // adjacent statements. At the real TTL the difference is a single instant, but
        // "valid until T" should mean invalid at T.
        if (challenge == null || !Instant.now().isBefore(challenge.expiresAt())) {
            return false;
        }
        return challenge.text().equalsIgnoreCase(answer.trim());
    }

    private void sweep() {
        if (outstanding.size() > MAX_OUTSTANDING) {
            outstanding.clear();
            return;
        }
        Instant now = Instant.now();
        // Same boundary as solve(): expired at expiresAt, not after it.
        outstanding.values().removeIf(c -> !now.isBefore(c.expiresAt()));
    }

    /** The same look the canvas had: rotated glyphs in mixed colours over noise. */
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
