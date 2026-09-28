package com.dilaw.mohe.service;

import com.dilaw.mohe.config.FileEncryptionProperties;
import com.dilaw.mohe.exception.FileEncryptionException;
import com.dilaw.mohe.util.crypto.FileCipher;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Files at rest. Writes under the active key; reads under whichever configured
 * key the file's header names (security plan S-10: several keys may be live
 * while a rotation is in progress).
 */
@Service
public class FileEncryptionService {

    private final Map<Integer, SecretKey> keys;
    private final int activeKeyId;

    public FileEncryptionService(FileEncryptionProperties properties) {
        Map<Integer, SecretKey> map = new LinkedHashMap<>();
        for (Integer id : properties.getKeyIds()) {
            map.put(id, new SecretKeySpec(properties.getKeyBytes(id), "AES"));
        }
        this.keys = Collections.unmodifiableMap(map);
        this.activeKeyId = properties.getActiveKeyId();
    }

    public int activeKeyId() {
        return activeKeyId;
    }

    public Set<Integer> keyIds() {
        return keys.keySet();
    }

    private SecretKey keyFor(int id) {
        return keys.get(id);
    }

    public void encrypt(InputStream plaintext, Path dest) throws IOException {
        FileCipher.encrypt(plaintext, dest, keyFor(activeKeyId), activeKeyId);
    }

    public void encryptMultipartFile(MultipartFile file, Path dest) throws IOException {
        try (InputStream in = file.getInputStream()) {
            encrypt(in, dest);
        }
    }

    public void encryptBytes(byte[] plaintext, Path dest) throws IOException {
        try (InputStream in = new ByteArrayInputStream(plaintext)) {
            encrypt(in, dest);
        }
    }

    /**
     * Encrypt a plaintext file in place (writes via temp file, atomic replace).
     *
     * @return true if the file was encrypted, false if already encrypted
     */
    public boolean encryptFileInPlace(Path file) throws IOException {
        if (!Files.isRegularFile(file)) {
            return false;
        }
        if (FileCipher.isEncrypted(file)) {
            return false;
        }
        byte[] plaintext = Files.readAllBytes(file);
        Path temp = Files.createTempFile(file.getParent(), "menc-migrate-", ".tmp");
        try {
            encryptBytes(plaintext, temp);
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            return true;
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    /** The key id an encrypted file was written under; -1 for a plaintext file. */
    public int keyIdOf(Path file) throws IOException {
        return FileCipher.keyIdOf(file);
    }

    /**
     * Re-encrypts a file under the active key when it was written under another
     * (S-10 rotation). Plaintext files are left to {@link #encryptFileInPlace}.
     *
     * @return true when the file was rewritten
     */
    public boolean reencryptIfStale(Path file) throws IOException {
        if (!Files.isRegularFile(file)) return false;
        int current = FileCipher.keyIdOf(file);
        if (current < 0 || current == activeKeyId) return false;
        byte[] plaintext = decrypt(file);
        Path temp = Files.createTempFile(file.getParent(), "menc-rotate-", ".tmp");
        try {
            encryptBytes(plaintext, temp);
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            return true;
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    public byte[] decrypt(Path src) {
        try {
            return FileCipher.decrypt(src, this::keyFor);
        } catch (IOException e) {
            throw new FileEncryptionException("File integrity check failed - document may have been tampered with", e);
        }
    }

    public boolean isEncrypted(Path src) throws IOException {
        return FileCipher.isEncrypted(src);
    }

    /** Decrypt if encrypted; otherwise return raw bytes (supports pre-migration plaintext files). */
    public byte[] readDecrypted(Path src) {
        try {
            return decrypt(src);
        } catch (FileEncryptionException e) {
            throw e;
        }
    }

    public String sha256(byte[] plaintext) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(plaintext));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    public String sha256OfFile(Path path) {
        return sha256(readDecrypted(path));
    }
}
