package com.dilaw.mohe.audit;

import com.dilaw.mohe.config.FileEncryptionProperties;
import com.dilaw.mohe.security.SecurityEventLog;
import com.dilaw.mohe.service.FileEncryptionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * System test PRT-08: the audit archive carries no credentials, and a finished archive
 * is encrypted with the document key.
 */
class AuditArchiveRedactionTest {

    private static final String HASH = "$2a$12$abcdefghijklmnopqrstuuM0xyzHashHashHashHashHashHashHa";

    @TempDir Path archive;

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);

    private AuditRetentionJob job() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("LOG_ID", 1L);
        row.put("TABLE_NAME", "Users");
        row.put("OLD_VALUES", "{\"UserName\":\"amira\",\"PasswordHash\":\"" + HASH + "\",\"MfaSecret\":\"JBSWY3DP\"}");
        row.put("NEW_VALUES", "{\"UserName\":\"amira\",\"PasswordHash\":\"" + HASH + "\"}");
        when(jdbc.queryForList(contains("AUDITLOG.AuditTrail"), any(Object[].class))).thenReturn(List.of(row));
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);
        return new AuditRetentionJob(jdbc, new ObjectMapper(), mock(SecurityEventLog.class), true, 3, 1,
                archive.toString(), 5000);
    }

    private static FileEncryptionService encryption() {
        FileEncryptionProperties props = new FileEncryptionProperties();
        ReflectionTestUtils.setField(props, "keyBase64", "AQIDBAUGBwgJCgsMDQ4PEBESExQVFhcYGRobHB0eHyA=");
        ReflectionTestUtils.invokeMethod(props, "validate");
        return new FileEncryptionService(props);
    }

    private static String gunzip(byte[] bytes) throws Exception {
        try (GZIPInputStream in = new GZIPInputStream(new ByteArrayInputStream(bytes))) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private List<Path> files() throws Exception {
        try (Stream<Path> s = Files.list(archive)) {
            return s.toList();
        }
    }

    @Test
    @DisplayName("the archived AuditTrail rows have their password hashes and secrets masked")
    void archiveIsRedacted() throws Exception {
        AuditRetentionJob job = job();
        Map<String, Integer> purged = job.run(LocalDate.of(2026, 9, 28), archive);

        assertThat(purged.get("AuditTrail")).isEqualTo(1);
        Path file = files().get(0);
        assertThat(file.getFileName().toString()).startsWith("AuditTrail-").endsWith(".ndjson.gz");
        String text = gunzip(Files.readAllBytes(file));
        assertThat(text).contains("amira").doesNotContain(HASH).doesNotContain("JBSWY3DP")
                .contains(AuditValueRedactor.MASK);
    }

    @Test
    @DisplayName("with the file key available, the archive is encrypted and named .enc; it decrypts to the redacted gzip")
    void archiveIsEncrypted() throws Exception {
        AuditRetentionJob job = job();
        FileEncryptionService encryption = encryption();
        job.setFileEncryption(encryption);
        job.run(LocalDate.of(2026, 9, 28), archive);

        Path file = files().get(0);
        assertThat(file.getFileName().toString()).endsWith(".ndjson.gz.enc");
        assertThat(encryption.isEncrypted(file)).isTrue();
        String text = gunzip(encryption.readDecrypted(file));
        assertThat(text).contains("amira").doesNotContain(HASH);
    }
}
