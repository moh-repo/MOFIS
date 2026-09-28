package com.dilaw.mohe.util.crypto;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.SecureRandom;
import java.util.function.IntFunction;

/**
 * AES-256-GCM file encryption.
 *
 * <p>Two header layouts (security plan S-10 added the second):
 * <pre>
 *   version 1:  MAGIC(4) | 0x01 | nonce(12) | ciphertext+tag        key id 1, implied
 *   version 2:  MAGIC(4) | 0x02 | keyId(1) | nonce(12) | ciphertext+tag
 * </pre>
 * A file written under key 1 keeps the version-1 header, so every file the
 * system wrote before key versioning is read back unchanged; any other key
 * writes the version-2 header carrying its id, and the reader picks the key
 * from the id. Key ids are 1..255.
 */
public final class FileCipher {
    public static final byte[] MAGIC = {'M', 'E', 'N', 'C'};
    public static final byte VERSION = 1;
    public static final byte VERSION_KEYED = 2;
    /** Header length of a version-1 file (the version-2 header is one byte longer). */
    public static final int HEADER_LENGTH = MAGIC.length + 1 + 12;
    public static final int LEGACY_KEY_ID = 1;
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private FileCipher() {}

    /** Encrypts under key id 1 with the version-1 header (the pre-S-10 layout). */
    public static void encrypt(InputStream plaintext, Path dest, SecretKey key) throws IOException {
        encrypt(plaintext, dest, key, LEGACY_KEY_ID);
    }

    /** Encrypts under {@code key}, recording {@code keyId} in the header when it is not 1. */
    public static void encrypt(InputStream plaintext, Path dest, SecretKey key, int keyId) throws IOException {
        if (keyId < 1 || keyId > 255) {
            throw new IllegalArgumentException("key id must be 1..255, got " + keyId);
        }
        Path parent = dest.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Path temp = Files.createTempFile(parent != null ? parent : dest.getRoot(), "menc-", ".tmp");
        try {
            byte[] nonce = new byte[GCM_IV_LENGTH];
            SECURE_RANDOM.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LENGTH, nonce));
            try (OutputStream out = Files.newOutputStream(temp)) {
                out.write(MAGIC);
                if (keyId == LEGACY_KEY_ID) {
                    out.write(VERSION);
                } else {
                    out.write(VERSION_KEYED);
                    out.write(keyId);
                }
                out.write(nonce);
                try (var cipherOut = new javax.crypto.CipherOutputStream(out, cipher)) {
                    plaintext.transferTo(cipherOut);
                }
            }
            Files.move(temp, dest, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (Exception e) {
            Files.deleteIfExists(temp);
            if (e instanceof IOException io) {
                throw io;
            }
            throw new IOException("Encryption failed", e);
        }
    }

    /** Decrypts a file written under one known key (a version-2 file under another id is refused). */
    public static byte[] decrypt(Path src, SecretKey key) throws IOException {
        return decrypt(src, id -> id == LEGACY_KEY_ID ? key : null);
    }

    /**
     * Decrypts with the key the header names, looked up through {@code keys};
     * a lookup returning null means the key is not configured.
     */
    public static byte[] decrypt(Path src, IntFunction<SecretKey> keys) throws IOException {
        if (!isEncrypted(src)) {
            return Files.readAllBytes(src);
        }
        try (InputStream in = Files.newInputStream(src)) {
            byte[] fixed = in.readNBytes(MAGIC.length + 1);
            if (fixed.length < MAGIC.length + 1 || !startsWithMagic(fixed)) {
                throw new IOException("Invalid encrypted file header");
            }
            byte version = fixed[MAGIC.length];
            int keyId;
            if (version == VERSION) {
                keyId = LEGACY_KEY_ID;
            } else if (version == VERSION_KEYED) {
                int id = in.read();
                if (id < 0) throw new IOException("Invalid encrypted file header");
                keyId = id;
            } else {
                throw new IOException("Unknown encrypted file version " + version);
            }
            SecretKey key = keys.apply(keyId);
            if (key == null) {
                throw new IOException("No file encryption key with id " + keyId + " is configured (MOHE_FILE_ENCRYPTION_KEYS)");
            }
            byte[] nonce = in.readNBytes(GCM_IV_LENGTH);
            if (nonce.length < GCM_IV_LENGTH) throw new IOException("Invalid encrypted file header");
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LENGTH, nonce));
            try (var cipherIn = new javax.crypto.CipherInputStream(in, cipher)) {
                return cipherIn.readAllBytes();
            }
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("Decryption failed - file may have been tampered with", e);
        }
    }

    /** The key id a file was written under, or -1 when the file is not one of ours. */
    public static int keyIdOf(Path src) throws IOException {
        if (!isEncrypted(src)) return -1;
        try (InputStream in = Files.newInputStream(src)) {
            byte[] fixed = in.readNBytes(MAGIC.length + 2);
            if (fixed.length < MAGIC.length + 1) return -1;
            byte version = fixed[MAGIC.length];
            if (version == VERSION) return LEGACY_KEY_ID;
            if (version == VERSION_KEYED && fixed.length >= MAGIC.length + 2) return fixed[MAGIC.length + 1] & 0xFF;
            return -1;
        }
    }

    public static boolean isEncrypted(Path src) throws IOException {
        if (!Files.isRegularFile(src) || Files.size(src) < HEADER_LENGTH) {
            return false;
        }
        try (InputStream in = Files.newInputStream(src)) {
            byte[] head = in.readNBytes(MAGIC.length);
            return startsWithMagic(head);
        }
    }

    private static boolean startsWithMagic(byte[] data) {
        if (data.length < MAGIC.length) {
            return false;
        }
        for (int i = 0; i < MAGIC.length; i++) {
            if (data[i] != MAGIC[i]) {
                return false;
            }
        }
        return true;
    }
}
