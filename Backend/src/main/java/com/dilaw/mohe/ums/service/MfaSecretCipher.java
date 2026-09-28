package com.dilaw.mohe.ums.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;

/**
 * Keeps TOTP secrets and recovery codes unreadable in a database dump (O-16).
 *
 * <p>AES-256-GCM with a random 96-bit nonce per value; the key is the SHA-256
 * of {@code mohe.mfa.encryption-key} ({@code MOHE_MFA_KEY}). Recovery codes are
 * stored as HMAC-SHA256 under the same key: a dump without the key cannot be
 * searched for them.
 *
 * <p>Production refuses to start without {@code MOHE_MFA_KEY} (plan W5-04, S-10):
 * the fallback, a key derived from the JWT secret, ties the enrolments to that
 * secret, so rotating it invalidated every one. Elsewhere the fallback stays, with
 * a warning.
 *
 * <p>A value that opens only under an older key ({@code MOHE_MFA_KEY_PREVIOUS},
 * or the JWT-derived key it was written under before {@code MOHE_MFA_KEY} was
 * set) is still read, and {@link #needsRewrap} tells the caller to write it back
 * under the current key. Recovery codes are matched under every such key
 * ({@link #hmacCandidates}); being one-way, they cannot be moved, and are replaced
 * when the user generates new ones.
 */
@Component
public class MfaSecretCipher {

    private static final Logger log = LoggerFactory.getLogger(MfaSecretCipher.class);
    private static final int NONCE_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final SecretKeySpec key;
    /** Keys a stored value may still be under, tried in order after {@link #key} (S-10, W5-04). */
    private final List<SecretKeySpec> previousKeys;
    private final SecureRandom random = new SecureRandom();

    public MfaSecretCipher(String configuredKey, String jwtSecret) {
        this(configuredKey, jwtSecret, null);
    }

    public MfaSecretCipher(String configuredKey, String jwtSecret, String previousJwtSecret) {
        this(configuredKey, "", jwtSecret, previousJwtSecret, "");
    }

    @org.springframework.beans.factory.annotation.Autowired
    public MfaSecretCipher(@Value("${mohe.mfa.encryption-key:}") String configuredKey,
                           @Value("${mohe.mfa.encryption-key-previous:}") String previousConfiguredKey,
                           @Value("${jwt.secret}") String jwtSecret,
                           @Value("${jwt.secret-previous:}") String previousJwtSecret,
                           @Value("${spring.profiles.active:}") String activeProfiles) {
        List<String> previous = new ArrayList<>();
        String material;
        if (hasText(configuredKey)) {
            material = configuredKey;
            if (hasText(previousConfiguredKey) && !previousConfiguredKey.equals(configuredKey)) {
                previous.add(previousConfiguredKey);
            }
            // Written before MOHE_MFA_KEY was set: moved to it at the owner's next sign-in.
            previous.add(jwtSecret + "|mohe-mfa");
        } else {
            if (isProduction(activeProfiles)) {
                throw new IllegalStateException("MOHE_MFA_KEY is required in production (any long random string). "
                        + "Set it and keep JWT_SECRET as it is: enrolments made under the JWT-derived key are "
                        + "still read and move to MOHE_MFA_KEY at their owner's next sign-in.");
            }
            log.warn("MOHE_MFA_KEY is not set: MFA secrets are protected with a key derived from JWT_SECRET. "
                    + "Rotating JWT_SECRET will then invalidate every MFA enrolment once JWT_SECRET_PREVIOUS is unset; "
                    + "production refuses to start without MOHE_MFA_KEY.");
            material = jwtSecret + "|mohe-mfa";
        }
        if (hasText(previousJwtSecret) && !previousJwtSecret.equals(jwtSecret)) {
            previous.add(previousJwtSecret + "|mohe-mfa");
        }
        this.key = derive(material);
        this.previousKeys = previous.stream().distinct()
                .filter(m -> !m.equals(material))
                .map(MfaSecretCipher::derive)
                .toList();
    }

    private static boolean hasText(String s) {
        return s != null && !s.isBlank();
    }

    private static boolean isProduction(String activeProfiles) {
        return activeProfiles != null && Arrays.stream(activeProfiles.split(","))
                .map(String::trim)
                .anyMatch("prod"::equalsIgnoreCase);
    }

    private static SecretKeySpec derive(String material) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(material.getBytes(StandardCharsets.UTF_8));
            return new SecretKeySpec(digest, "AES");
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    public String encrypt(String plaintext) {
        try {
            byte[] nonce = new byte[NONCE_BYTES];
            random.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, nonce));
            byte[] ct = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] out = new byte[nonce.length + ct.length];
            System.arraycopy(nonce, 0, out, 0, nonce.length);
            System.arraycopy(ct, 0, out, nonce.length, ct.length);
            return Base64.getEncoder().encodeToString(out);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("MFA secret encryption failed", e);
        }
    }

    public String decrypt(String stored) {
        try {
            return decrypt(stored, key);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            for (SecretKeySpec older : previousKeys) {
                try {
                    return decrypt(stored, older);
                } catch (GeneralSecurityException | IllegalArgumentException ignored) {
                    // try the next one, then report the original failure
                }
            }
            throw new IllegalStateException("MFA secret cannot be decrypted (was the key changed?)", e);
        }
    }

    /** True when a stored value opens only under an older key: re-encrypt it (S-10, W5-04). */
    public boolean needsRewrap(String stored) {
        try {
            decrypt(stored, key);
            return false;
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            for (SecretKeySpec older : previousKeys) {
                try {
                    decrypt(stored, older);
                    return true;
                } catch (GeneralSecurityException | IllegalArgumentException ignored) {
                    // not under this one either
                }
            }
            return false;
        }
    }

    private static String decrypt(String stored, SecretKeySpec with) throws GeneralSecurityException {
        byte[] all = Base64.getDecoder().decode(stored);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, with, new GCMParameterSpec(TAG_BITS, all, 0, NONCE_BYTES));
        return new String(cipher.doFinal(all, NONCE_BYTES, all.length - NONCE_BYTES), StandardCharsets.UTF_8);
    }

    /** Keyed hash of a recovery code, hex, under the current key: what a new code is stored as. */
    public String hmac(String value) {
        return hmac(value, key);
    }

    /** The code's hash under the current key and every older one: what a stored code may match (W5-04). */
    public List<String> hmacCandidates(String value) {
        List<String> all = new ArrayList<>(1 + previousKeys.size());
        all.add(hmac(value, key));
        previousKeys.forEach(k -> all.add(hmac(value, k)));
        return all;
    }

    private static String hmac(String value, SecretKeySpec with) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(with.getEncoded(), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
