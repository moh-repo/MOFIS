package com.dilaw.mohe.service;

import com.dilaw.mohe.dto.QuerySpecDto;
import com.dilaw.mohe.util.SecurityContextHelper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/**
 * Holds an ad-hoc query spec long enough to render it as a PDF.
 *
 * <p>The PDF pipeline is entirely GET-based — the viewer uses an iframe and the
 * "open in new tab" path is a direct navigation, both to get past ad blockers
 * that refuse XHR to anything called a report. A spec with joins and filters is
 * far too big for a readable query string, so the page posts it once, gets a
 * ticket, and puts the ticket in the URL.
 *
 * <p>A ticket is a handle, not a capability. It carries no permission of its
 * own: redeeming one re-runs the spec through {@link QueryExecutionService},
 * which applies the country scope and the field masks of whoever is asking, and a
 * ticket can only be redeemed by the user who created it. So the worst a leaked
 * ticket can do is let its owner see what they could already see.
 *
 * <h2>Stateless (RPT-08)</h2>
 *
 * <p>Tickets used to live in an in-memory map on the node that issued them, and the
 * whole map was cleared once it held 500 entries. Behind a load balancer the PDF
 * request often reached another node and 404'd; under load, the 501st ticket wiped
 * out every other user's link between their preview and their download.
 *
 * <p>The ticket is now the spec itself: the owner, the expiry and the spec,
 * deflated, base64url-encoded and signed with an HMAC-SHA256 key derived from
 * {@code jwt.secret} (so every node that accepts the same sign-ins accepts the same
 * tickets, and nothing is held in memory). Nothing in it is secret — it is the
 * caller's own query — it only has to be unforgeable, which the signature makes
 * it: a spec cannot be altered, re-owned or given a longer life without the key.
 * Without a configured secret (a unit test, a bare local run) the key is random
 * per process, which is the old single-node behaviour minus the eviction.
 */
@Service
public class QueryReportTicketService {

    /**
     * How long a ticket lives.
     *
     * <p>Long enough to preview a report and then download it. Nothing depends on
     * a ticket surviving — the page still holds the spec and can ask for another.
     */
    static final Duration TICKET_TTL = Duration.ofMinutes(15);

    /** Bounds what is inflated from a URL, so a crafted ticket cannot be a zip bomb. */
    private static final int MAX_SPEC_BYTES = 256 * 1024;

    private static final String VERSION = "v1";
    private static final String KEY_LABEL = "mohe.query-report-ticket.v1";

    private static final ObjectMapper JSON = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private final byte[] key;
    private final Clock clock;

    /** What a ticket carries. Short names: it travels in a URL. */
    record Payload(String o, long e, QuerySpecDto s) {
    }

    @Autowired
    public QueryReportTicketService(@Value("${jwt.secret:}") String secret) {
        this(secret, Clock.systemUTC());
    }

    QueryReportTicketService(String secret, Clock clock) {
        this.key = deriveKey(secret);
        this.clock = clock;
    }

    /** Signs a spec and returns the handle the PDF endpoints accept. */
    public String issue(QuerySpecDto spec) {
        if (spec == null || spec.getSource() == null || spec.getSource().trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "A query with no source cannot be reported on");
        }
        Instant expires = clock.instant().plus(TICKET_TTL);
        byte[] json;
        try {
            json = JSON.writeValueAsBytes(new Payload(SecurityContextHelper.currentUsername(),
                    expires.getEpochSecond(), spec));
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "This query cannot be reported on");
        }
        String body = b64(deflate(json));
        return VERSION + "." + body + "." + b64(sign(VERSION + "." + body));
    }

    /**
     * The spec behind a ticket.
     *
     * <p>Deliberately does not consume the ticket: previewing a report and then
     * downloading it are two requests for the same document, and making the
     * second one fail would be a bug rather than a safeguard.
     */
    public QuerySpecDto redeem(String ticketId) {
        Payload payload = verify(ticketId);
        if (payload == null || !clock.instant().isBefore(Instant.ofEpochSecond(payload.e()))) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "This report link has expired. Run the query again to produce a new one.");
        }
        if (payload.o() == null || !payload.o().equals(SecurityContextHelper.currentUsername())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "This report link belongs to another user");
        }
        return payload.s();
    }

    /** The payload of a well-formed ticket carrying a valid signature, else null. */
    private Payload verify(String ticket) {
        if (ticket == null) return null;
        String[] parts = ticket.trim().split("\\.");
        if (parts.length != 3 || !VERSION.equals(parts[0])) return null;
        try {
            byte[] expected = sign(parts[0] + "." + parts[1]);
            byte[] given = Base64.getUrlDecoder().decode(parts[2]);
            if (!MessageDigest.isEqual(expected, given)) return null;
            Payload payload = JSON.readValue(inflate(Base64.getUrlDecoder().decode(parts[1])), Payload.class);
            return payload == null || payload.s() == null ? null : payload;
        } catch (IllegalArgumentException | IOException | DataFormatException e) {
            return null;
        }
    }

    private byte[] sign(String data) {
        return hmac(key, data.getBytes(StandardCharsets.US_ASCII));
    }

    private static byte[] deriveKey(String secret) {
        byte[] root;
        if (secret == null || secret.isBlank()) {
            root = new byte[32];
            new SecureRandom().nextBytes(root);
        } else {
            root = secret.getBytes(StandardCharsets.UTF_8);
        }
        // A key of its own, so a ticket signature is never a JWT signature.
        return hmac(root, KEY_LABEL.getBytes(StandardCharsets.US_ASCII));
    }

    private static byte[] hmac(byte[] key, byte[] data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(data);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 is unavailable", e);
        }
    }

    private static String b64(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static byte[] deflate(byte[] in) {
        Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION);
        try {
            deflater.setInput(in);
            deflater.finish();
            ByteArrayOutputStream out = new ByteArrayOutputStream(in.length / 2 + 16);
            byte[] buf = new byte[1024];
            while (!deflater.finished()) {
                out.write(buf, 0, deflater.deflate(buf));
            }
            return out.toByteArray();
        } finally {
            deflater.end();
        }
    }

    private static byte[] inflate(byte[] in) throws DataFormatException {
        Inflater inflater = new Inflater();
        try {
            inflater.setInput(in);
            ByteArrayOutputStream out = new ByteArrayOutputStream(in.length * 4 + 16);
            byte[] buf = new byte[1024];
            while (!inflater.finished()) {
                int n = inflater.inflate(buf);
                if (n == 0 && (inflater.needsInput() || inflater.needsDictionary())) {
                    throw new DataFormatException("truncated ticket");
                }
                out.write(buf, 0, n);
                if (out.size() > MAX_SPEC_BYTES) {
                    throw new DataFormatException("ticket too large");
                }
            }
            return out.toByteArray();
        } finally {
            inflater.end();
        }
    }
}
