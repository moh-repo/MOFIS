package com.dilaw.mohe.ums.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/** O-16: the code generator agrees with RFC 6238's published vectors, and a code is good once. */
class TotpServiceTest {

    private final TotpService totp = new TotpService();
    /** RFC 6238 appendix B seed, "12345678901234567890", as Base32. */
    private static final String RFC_SECRET = TotpService.base32Encode("12345678901234567890".getBytes(StandardCharsets.US_ASCII));

    @Test
    @DisplayName("RFC 6238 SHA-1 vectors (last six digits of the eight-digit reference values)")
    void rfcVectors() {
        assertThat(RFC_SECRET).isEqualTo("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ");
        assertThat(totp.code(RFC_SECRET, 59L / 30)).isEqualTo("287082");           // T=59         -> 94287082
        assertThat(totp.code(RFC_SECRET, 1111111109L / 30)).isEqualTo("081804");   // T=1111111109 -> 07081804
        assertThat(totp.code(RFC_SECRET, 1234567890L / 30)).isEqualTo("005924");   // T=1234567890 -> 89005924
        assertThat(totp.code(RFC_SECRET, 20000000000L / 30)).isEqualTo("353130");  // T=20000000000-> 65353130
    }

    @Test
    @DisplayName("a code is accepted one step either side of now, and reports the step it matched")
    void windowAndStep() {
        long now = 1_700_000_000L / 30;
        String secret = totp.generateSecret();
        assertThat(secret).hasSize(32).matches("[A-Z2-7]+");
        assertThat(totp.verify(secret, totp.code(secret, now), now, 1)).isEqualTo(now);
        assertThat(totp.verify(secret, totp.code(secret, now - 1), now, 1)).isEqualTo(now - 1);
        assertThat(totp.verify(secret, totp.code(secret, now + 1), now, 1)).isEqualTo(now + 1);
        assertThat(totp.verify(secret, totp.code(secret, now + 2), now, 1)).isEqualTo(-1);
        assertThat(totp.verify(secret, "12345", now, 1)).isEqualTo(-1);
        assertThat(totp.verify(secret, null, now, 1)).isEqualTo(-1);
        assertThat(totp.verify(secret, "123 456", now, 1)).as("spaces are tolerated, the digits decide").isIn(-1L, now - 1, now, now + 1);
    }

    @Test
    @DisplayName("Base32 round-trips and the otpauth URI carries what the app needs")
    void base32AndUri() {
        byte[] raw = new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20};
        assertThat(TotpService.base32Decode(TotpService.base32Encode(raw))).isEqualTo(raw);
        String uri = totp.otpauthUri("MOHE", "jo admin", RFC_SECRET);
        assertThat(uri).startsWith("otpauth://totp/MOHE:jo%20admin?secret=" + RFC_SECRET)
                .contains("issuer=MOHE").contains("digits=6").contains("period=30");
        byte[] png = totp.qrPng(uri, 120);
        assertThat(png).as("a PNG").startsWith((byte) 0x89, (byte) 'P', (byte) 'N', (byte) 'G');
    }
}
