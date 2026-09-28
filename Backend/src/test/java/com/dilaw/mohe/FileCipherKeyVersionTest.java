package com.dilaw.mohe;

import com.dilaw.mohe.config.FileEncryptionProperties;
import com.dilaw.mohe.service.FileEncryptionService;
import com.dilaw.mohe.util.crypto.FileCipher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * S-10: file keys carry an id, so a rotation is a new key beside the old one
 * rather than a re-encrypt of everything at once. Files written before key
 * versioning keep their header and read back under key 1.
 */
class FileCipherKeyVersionTest {

    private static final byte[] K1 = key((byte) 1);
    private static final byte[] K2 = key((byte) 2);
    private static final byte[] PLAINTEXT = "student passport scan".getBytes(StandardCharsets.UTF_8);

    private static byte[] key(byte fill) {
        byte[] k = new byte[32];
        java.util.Arrays.fill(k, fill);
        return k;
    }

    private static SecretKey aes(byte[] k) {
        return new SecretKeySpec(k, "AES");
    }

    private static FileEncryptionProperties props(String single, String keys, String active) {
        FileEncryptionProperties p = new FileEncryptionProperties();
        ReflectionTestUtils.setField(p, "keyBase64", single);
        ReflectionTestUtils.setField(p, "keysSpec", keys);
        ReflectionTestUtils.setField(p, "activeKeySpec", active);
        ReflectionTestUtils.invokeMethod(p, "validate");
        return p;
    }

    private static String b64(byte[] k) {
        return Base64.getEncoder().encodeToString(k);
    }

    @Test
    @DisplayName("key 1 writes the original header, so pre-S-10 readers and files agree")
    void legacyHeaderForKeyOne(@TempDir Path dir) throws IOException {
        Path f = dir.resolve("a.bin");
        FileCipher.encrypt(new ByteArrayInputStream(PLAINTEXT), f, aes(K1));
        byte[] raw = Files.readAllBytes(f);
        assertThat(raw[4]).isEqualTo(FileCipher.VERSION);
        assertThat(FileCipher.keyIdOf(f)).isEqualTo(1);
        assertThat(FileCipher.decrypt(f, aes(K1))).isEqualTo(PLAINTEXT);
    }

    @Test
    @DisplayName("another key writes the keyed header and reads back through the lookup")
    void keyedHeader(@TempDir Path dir) throws IOException {
        Path f = dir.resolve("b.bin");
        FileCipher.encrypt(new ByteArrayInputStream(PLAINTEXT), f, aes(K2), 2);
        byte[] raw = Files.readAllBytes(f);
        assertThat(raw[4]).isEqualTo(FileCipher.VERSION_KEYED);
        assertThat(raw[5]).isEqualTo((byte) 2);
        assertThat(FileCipher.keyIdOf(f)).isEqualTo(2);
        assertThat(FileCipher.decrypt(f, id -> id == 2 ? aes(K2) : null)).isEqualTo(PLAINTEXT);
        assertThatThrownBy(() -> FileCipher.decrypt(f, aes(K1)))
                .as("a single-key reader does not know key 2")
                .isInstanceOf(IOException.class).hasMessageContaining("id 2");
    }

    @Test
    @DisplayName("the service writes under the active key and still reads the older one; the job rotates")
    void serviceRotation(@TempDir Path dir) throws IOException {
        FileEncryptionService before = new FileEncryptionService(props(b64(K1), "", ""));
        assertThat(before.activeKeyId()).isEqualTo(1);
        Path old = dir.resolve("old.bin");
        before.encryptBytes(PLAINTEXT, old);

        FileEncryptionService after = new FileEncryptionService(props(b64(K1), "2:" + b64(K2), ""));
        assertThat(after.activeKeyId()).as("highest id is active by default").isEqualTo(2);
        assertThat(after.keyIds()).containsExactly(1, 2);
        assertThat(after.readDecrypted(old)).as("the old file is still readable").isEqualTo(PLAINTEXT);

        Path fresh = dir.resolve("new.bin");
        after.encryptBytes(PLAINTEXT, fresh);
        assertThat(after.keyIdOf(fresh)).isEqualTo(2);

        assertThat(after.reencryptIfStale(old)).isTrue();
        assertThat(after.keyIdOf(old)).isEqualTo(2);
        assertThat(after.readDecrypted(old)).isEqualTo(PLAINTEXT);
        assertThat(after.reencryptIfStale(old)).as("already current").isFalse();
        assertThat(after.reencryptIfStale(fresh)).isFalse();

        Path plain = dir.resolve("plain.txt");
        Files.write(plain, PLAINTEXT);
        assertThat(after.keyIdOf(plain)).isEqualTo(-1);
        assertThat(after.reencryptIfStale(plain)).as("plaintext is the other job's business").isFalse();

        // Once key 1 is dropped from the list, the rotated file still opens; an unrotated one would not.
        FileEncryptionService dropped = new FileEncryptionService(props("", "2:" + b64(K2), "2"));
        assertThat(dropped.readDecrypted(old)).isEqualTo(PLAINTEXT);
    }

    @Test
    @DisplayName("the active key must be one of the listed keys, and ids stay within a byte")
    void configurationErrors() {
        assertThatThrownBy(() -> props(b64(K1), "2:" + b64(K2), "3")).hasMessageContaining("not in the configured keys");
        assertThatThrownBy(() -> props("", "300:" + b64(K2), "")).hasMessageContaining("1..255");
        assertThatThrownBy(() -> props(b64(K1), "1:" + b64(K2), "")).hasMessageContaining("listed twice");
        assertThatThrownBy(() -> props("", "2:short", "")).hasMessageContaining("Base64");
        FileEncryptionProperties explicit = props(b64(K1), "2:" + b64(K2) + ", 3:" + b64(key((byte) 3)), "2");
        assertThat(explicit.getActiveKeyId()).isEqualTo(2);
        assertThat(explicit.getKeyIds()).containsExactly(1, 2, 3);
    }
}
