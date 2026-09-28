package com.dilaw.mohe.ums.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The service that mints every staff credential in the system had no test of
 * its own until the third revision pass (27 Aug 2026): it was exercised only
 * through login flows that also depend on the database. These are the
 * properties the {@code JwtAuthFilter} and the refresh endpoint rely on, pinned
 * without a Spring context.
 */
class JwtTokenServiceTest {

    private static final String SECRET = "unit-test-secret-that-is-at-least-thirty-two-bytes-long";

    @Test
    @DisplayName("a minted token parses back to the same user, subject and session id")
    void roundTrip() {
        JwtTokenService service = new JwtTokenService(SECRET, 3600);

        String token = service.createToken(42L, "officer", "session-1");
        Claims claims = service.parseToken(token);

        assertThat(claims).isNotNull();
        assertThat(JwtTokenService.getUserId(claims)).isEqualTo(42L);
        assertThat(JwtTokenService.getUsername(claims)).isEqualTo("officer");
        assertThat(JwtTokenService.getJti(claims)).isEqualTo("session-1");
        assertThat(claims.getExpiration()).isAfter(new Date());
    }

    @Test
    @DisplayName("a token past its expiry is rejected, not parsed with a stale user")
    void expiredTokenIsNull() {
        // A negative lifetime mints a token that expired before it was issued.
        JwtTokenService service = new JwtTokenService(SECRET, -60);

        String token = service.createToken(42L, "officer");

        assertThat(service.parseToken(token)).isNull();
        assertThat(service.refreshToken(token)).as("nor can it be refreshed").isNull();
    }

    @Test
    @DisplayName("a token signed with another key, a tampered token and a blank token are all rejected")
    void foreignOrTamperedTokensAreNull() {
        JwtTokenService service = new JwtTokenService(SECRET, 3600);
        String foreign = Jwts.builder()
                .subject("intruder")
                .claim("userId", 1L)
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(Keys.hmacShaKeyFor("another-secret-that-is-also-thirty-two-bytes!!".getBytes(StandardCharsets.UTF_8)))
                .compact();
        String genuine = service.createToken(42L, "officer");
        String tampered = genuine.substring(0, genuine.lastIndexOf('.') + 1) + "AAAA";

        assertThat(service.parseToken(foreign)).isNull();
        assertThat(service.parseToken(tampered)).isNull();
        assertThat(service.parseToken("")).isNull();
        assertThat(service.parseToken(null)).isNull();
    }

    @Test
    @DisplayName("refresh keeps the user and subject but issues a new session id")
    void refreshRotatesTheSessionId() {
        JwtTokenService service = new JwtTokenService(SECRET, 3600);
        String first = service.createToken(42L, "officer", "session-1");

        String refreshed = service.refreshToken(first);
        Claims claims = service.parseToken(refreshed);

        assertThat(claims).isNotNull();
        assertThat(JwtTokenService.getUserId(claims)).isEqualTo(42L);
        assertThat(JwtTokenService.getUsername(claims)).isEqualTo("officer");
        assertThat(JwtTokenService.getJti(claims))
                .as("the session id is what the session table is keyed on; a refresh must rotate it")
                .isNotEqualTo("session-1")
                .isNotBlank();
    }

    @Test
    @DisplayName("a blank secret refuses to start, and so does one under 32 bytes")
    void secretHandling() {
        assertThatThrownBy(() -> new JwtTokenService(" ", 3600))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("jwt.secret");

        // Short secrets used to be zero-padded to 32 bytes, which let a production
        // deploy sign HS256 with a handful of bytes of entropy. Now it fails fast.
        assertThatThrownBy(() -> new JwtTokenService("short", 3600))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32 bytes");

        assertThatThrownBy(() -> new JwtTokenService("exactly-31-bytes-long-secret-xx", 3600))
                .as("31 bytes is still under the floor")
                .isInstanceOf(IllegalStateException.class);

        assertThat(new JwtTokenService("exactly-32-bytes-long-secret-xxx", 3600)
                .createToken(7L, "x"))
                .as("32 bytes is accepted")
                .isNotBlank();
    }

    @Test
    @DisplayName("the user id claim is read whether it was serialised as a number or a string")
    void userIdClaimShapes() {
        JwtTokenService service = new JwtTokenService(SECRET, 3600);
        String numeric = service.createToken(42L, "officer");
        assertThat(JwtTokenService.getUserId(service.parseToken(numeric))).isEqualTo(42L);

        String asString = Jwts.builder()
                .subject("officer")
                .claim("userId", "43")
                .id("s")
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();
        assertThat(JwtTokenService.getUserId(service.parseToken(asString))).isEqualTo(43L);
        assertThat(JwtTokenService.getUserId(null)).isNull();
        assertThat(JwtTokenService.getJti(null)).isNull();
    }
}
