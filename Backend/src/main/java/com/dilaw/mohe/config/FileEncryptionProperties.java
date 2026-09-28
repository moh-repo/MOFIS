package com.dilaw.mohe.config;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * The file-at-rest keys (AES-256-GCM).
 *
 * <p>Before S-10 there was one key, {@code MOHE_FILE_ENCRYPTION_KEY}; it is
 * still honoured and is key id 1. {@code MOHE_FILE_ENCRYPTION_KEYS} lists any
 * number of keys as {@code id:base64[,id:base64...]} (ids 1..255), and
 * {@code MOHE_FILE_ENCRYPTION_ACTIVE_KEY} names the one new files are written
 * under (default: the highest id). Every listed key stays readable, so
 * rotation is: add the new key, make it active, run the re-encrypt job, then
 * drop the old key from the list once no file names it (KEY_ROTATION.md).
 */
@Component
public class FileEncryptionProperties {

    @Value("${app.file-encryption.key:}")
    private String keyBase64;

    @Value("${app.file-encryption.keys:}")
    private String keysSpec;

    @Value("${app.file-encryption.active-key:}")
    private String activeKeySpec;

    @Value("${spring.profiles.active:dev}")
    private String activeProfiles;

    private Map<Integer, byte[]> keys = Collections.emptyMap();
    private int activeKeyId = 1;

    @PostConstruct
    void validate() {
        Map<Integer, byte[]> parsed = new LinkedHashMap<>();
        if (keyBase64 != null && !keyBase64.isBlank()) {
            parsed.put(1, decode(keyBase64, "MOHE_FILE_ENCRYPTION_KEY"));
        }
        if (keysSpec != null && !keysSpec.isBlank()) {
            for (String entry : keysSpec.split(",")) {
                String e = entry.trim();
                if (e.isEmpty()) continue;
                int colon = e.indexOf(':');
                if (colon <= 0) {
                    throw new IllegalStateException("MOHE_FILE_ENCRYPTION_KEYS entries are id:base64, got " + e);
                }
                int id;
                try {
                    id = Integer.parseInt(e.substring(0, colon).trim());
                } catch (NumberFormatException ex) {
                    throw new IllegalStateException("MOHE_FILE_ENCRYPTION_KEYS: key id must be a number in " + e, ex);
                }
                if (id < 1 || id > 255) {
                    throw new IllegalStateException("MOHE_FILE_ENCRYPTION_KEYS: key id must be 1..255, got " + id);
                }
                byte[] bytes = decode(e.substring(colon + 1), "MOHE_FILE_ENCRYPTION_KEYS entry " + id);
                if (parsed.containsKey(id) && !Arrays.equals(parsed.get(id), bytes)) {
                    throw new IllegalStateException("MOHE_FILE_ENCRYPTION_KEYS: key id " + id
                            + " is listed twice with different material (or differs from MOHE_FILE_ENCRYPTION_KEY)");
                }
                parsed.put(id, bytes);
            }
        }
        if (parsed.isEmpty()) {
            if (isProdProfile()) {
                throw new IllegalStateException(
                        "MOHE_FILE_ENCRYPTION_KEY (or MOHE_FILE_ENCRYPTION_KEYS) is required in production (Base64-encoded 32-byte AES key)");
            }
            throw new IllegalStateException(
                    "app.file-encryption.key is not set. Configure MOHE_FILE_ENCRYPTION_KEY or use the dev profile default.");
        }
        int active;
        if (activeKeySpec != null && !activeKeySpec.isBlank()) {
            try {
                active = Integer.parseInt(activeKeySpec.trim());
            } catch (NumberFormatException ex) {
                throw new IllegalStateException("MOHE_FILE_ENCRYPTION_ACTIVE_KEY must be a key id", ex);
            }
            if (!parsed.containsKey(active)) {
                throw new IllegalStateException("MOHE_FILE_ENCRYPTION_ACTIVE_KEY names key " + active
                        + ", which is not in the configured keys " + parsed.keySet());
            }
        } else {
            active = parsed.keySet().stream().mapToInt(Integer::intValue).max().orElse(1);
        }
        this.keys = Collections.unmodifiableMap(parsed);
        this.activeKeyId = active;
    }

    private static byte[] decode(String base64, String what) {
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(base64.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(what + " must be valid Base64", e);
        }
        if (bytes.length != 32) {
            throw new IllegalStateException(what + " must decode to exactly 32 bytes (256-bit AES key), got " + bytes.length);
        }
        return bytes;
    }

    /** The active key's material (what {@code getKeyBytes} always meant). */
    public byte[] getKeyBytes() {
        return getKeyBytes(activeKeyId);
    }

    /** The material of one configured key, or null when the id is not configured. */
    public byte[] getKeyBytes(int keyId) {
        byte[] k = keys.get(keyId);
        return k == null ? null : Arrays.copyOf(k, k.length);
    }

    public int getActiveKeyId() {
        return activeKeyId;
    }

    public Set<Integer> getKeyIds() {
        return keys.keySet();
    }

    private boolean isProdProfile() {
        return activeProfiles != null && Arrays.stream(activeProfiles.split(","))
                .map(String::trim)
                .anyMatch("prod"::equalsIgnoreCase);
    }
}
