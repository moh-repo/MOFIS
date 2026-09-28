package com.dilaw.mohe;

import com.dilaw.mohe.util.crypto.FileCipher;
import org.junit.jupiter.api.Test;

import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class FileCipherUnitTest {

    private static final byte[] KEY_BYTES = new byte[32];
    private static final SecretKeySpec KEY;

    static {
        for (int i = 0; i < KEY_BYTES.length; i++) {
            KEY_BYTES[i] = (byte) (i + 1);
        }
        KEY = new SecretKeySpec(KEY_BYTES, "AES");
    }

    @Test
    void encryptDecryptRoundTrip() throws Exception {
        byte[] plaintext = "MOHE test document content".getBytes();
        Path temp = Files.createTempFile("menc-test-", ".bin");
        try {
            FileCipher.encrypt(new ByteArrayInputStream(plaintext), temp, KEY);
            assertTrue(FileCipher.isEncrypted(temp));
            byte[] decrypted = FileCipher.decrypt(temp, KEY);
            assertArrayEquals(plaintext, decrypted);
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    @Test
    void tamperedFileFailsDecryption() throws Exception {
        byte[] plaintext = "tamper test".getBytes();
        Path temp = Files.createTempFile("menc-tamper-", ".bin");
        try {
            FileCipher.encrypt(new ByteArrayInputStream(plaintext), temp, KEY);
            byte[] bytes = Files.readAllBytes(temp);
            bytes[bytes.length - 1] ^= 0xFF;
            Files.write(temp, bytes);
            assertThrows(Exception.class, () -> FileCipher.decrypt(temp, KEY));
        } finally {
            Files.deleteIfExists(temp);
        }
    }
}
