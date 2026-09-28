package com.dilaw.mohe.ums.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

/**
 * Creates and validates JWT tokens for UMS authentication.
 * Tokens contain userId, username, and jti (session id); used by JwtAuthFilter.
 */
@Service
public class JwtTokenService {

    private static final Logger log = LoggerFactory.getLogger(JwtTokenService.class);
    private static final String CLAIM_USER_ID = "userId";
    private static final String CLAIM_JTI = "jti";
    /** O-16: a token issued after the password and before the second factor. */
    private static final String CLAIM_MFA = "mfa";
    private static final String MFA_PENDING = "pending";

    private final SecretKey key;
    /** S-10: the secret before the last rotation, accepted for verification only; null when none. */
    private final SecretKey previousKey;
    private final long expirationMs;

    public JwtTokenService(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.expiration-seconds:86400}") long expirationSeconds) {
        this(secret, null, expirationSeconds);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public JwtTokenService(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.secret-previous:}") String previousSecret,
            @Value("${jwt.expiration-seconds:86400}") long expirationSeconds) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("jwt.secret must be set. Set the JWT_SECRET environment variable.");
        }
        if (secret.contains("only-for-local-dev") || secret.contains("dev-secret")) {
            log.warn("WARNING: jwt.secret appears to be a development default. Set JWT_SECRET env var for production.");
        }
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            // Refuse to start rather than silently zero-padding a weak secret: a padded
            // 8-byte secret signs HS256 with 8 bytes of entropy and is offline-forgeable.
            throw new IllegalStateException(
                    "jwt.secret must be at least 32 bytes (got " + keyBytes.length
                            + "). Set a longer JWT_SECRET.");
        }
        this.key = Keys.hmacShaKeyFor(keyBytes);
        if (previousSecret != null && !previousSecret.isBlank()) {
            byte[] previousBytes = previousSecret.getBytes(StandardCharsets.UTF_8);
            if (previousBytes.length < 32) {
                throw new IllegalStateException("jwt.secret-previous must be at least 32 bytes, like jwt.secret");
            }
            this.previousKey = previousSecret.equals(secret) ? null : Keys.hmacShaKeyFor(previousBytes);
            if (this.previousKey != null) {
                log.info("JWT_SECRET_PREVIOUS is set: tokens signed with the previous secret stay valid until they expire "
                        + "(at most jwt.expiration-seconds); unset it after that.");
            }
        } else {
            this.previousKey = null;
        }
        this.expirationMs = expirationSeconds * 1000L;
    }

    public String createToken(Long userId, String username) {
        return createToken(userId, username, UUID.randomUUID().toString());
    }

    public String createToken(Long userId, String username, String jti) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expirationMs);
        return Jwts.builder()
                .subject(username != null ? username : "")
                .claim(CLAIM_USER_ID, userId)
                .id(jti != null ? jti : UUID.randomUUID().toString())
                .issuedAt(now)
                .expiration(expiry)
                .signWith(key)
                .compact();
    }

    /**
     * The token a sign-in holds between the password and the second factor
     * (O-16): the account, a short life, and the {@code mfa=pending} claim that
     * makes {@code JwtAuthFilter} ignore it and {@link #refreshToken} refuse it.
     * It opens no session; only the MFA endpoints read it.
     */
    public String createMfaPendingToken(Long userId, String username, long ttlSeconds) {
        Date now = new Date();
        return Jwts.builder()
                .subject(username != null ? username : "")
                .claim(CLAIM_USER_ID, userId)
                .claim(CLAIM_MFA, MFA_PENDING)
                .id(UUID.randomUUID().toString())
                .issuedAt(now)
                .expiration(new Date(now.getTime() + ttlSeconds * 1000L))
                .signWith(key)
                .compact();
    }

    public static boolean isMfaPending(Claims claims) {
        return claims != null && MFA_PENDING.equals(claims.get(CLAIM_MFA));
    }

    /**
     * Parse and validate the JWT; returns claims or null if invalid/expired.
     */
    public Claims parseToken(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        try {
            return Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (ExpiredJwtException e) {
            log.debug("JWT expired: {}", e.getMessage());
            return null;
        } catch (io.jsonwebtoken.security.SignatureException e) {
            // S-10: a token minted before the secret was rotated.
            if (previousKey == null) {
                log.debug("Invalid JWT: {}", e.getMessage());
                return null;
            }
            try {
                Claims claims = Jwts.parser().verifyWith(previousKey).build().parseSignedClaims(token).getPayload();
                log.debug("JWT verified with the previous secret (rotation in progress)");
                return claims;
            } catch (ExpiredJwtException ex) {
                log.debug("JWT expired: {}", ex.getMessage());
                return null;
            } catch (JwtException ex) {
                log.debug("Invalid JWT: {}", ex.getMessage());
                return null;
            }
        } catch (JwtException e) {
            log.debug("Invalid JWT: {}", e.getMessage());
            return null;
        }
    }

    public static Long getUserId(Claims claims) {
        if (claims == null) return null;
        Object v = claims.get(CLAIM_USER_ID);
        if (v instanceof Number) return ((Number) v).longValue();
        if (v instanceof String) return Long.parseLong((String) v);
        return null;
    }

    public static String getUsername(Claims claims) {
        return claims != null ? claims.getSubject() : null;
    }

    public static String getJti(Claims claims) {
        if (claims == null) {
            return null;
        }
        String jti = claims.getId();
        if (jti != null && !jti.isBlank()) {
            return jti;
        }
        Object raw = claims.get(CLAIM_JTI);
        return raw != null ? raw.toString() : null;
    }

    /**
     * Re-issue a new token from an existing (possibly near-expiry) valid token.
     * Returns null if the existing token is invalid or already expired.
     */
    public String refreshToken(String existingToken) {
        Claims claims = parseToken(existingToken);
        if (claims == null) return null;
        // O-16: a pending token must not be refreshed into a full one.
        if (isMfaPending(claims)) return null;
        Long userId   = getUserId(claims);
        String username = getUsername(claims);
        if (userId == null) return null;
        return createToken(userId, username, UUID.randomUUID().toString());
    }
}
