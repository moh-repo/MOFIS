package com.dilaw.mohe.audit;

import com.dilaw.mohe.config.tenant.TenantContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * O-35: rows past their retention are archived, then purged; younger rows and
 * the rest of the record are untouched. Runs inside a rolled-back transaction,
 * so the probe rows it plants never outlive it.
 */
@Tag("integration")
@SpringBootTest
@Transactional
class AuditRetentionJobTest {

    @Autowired private AuditRetentionJob job;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private com.dilaw.mohe.service.FileEncryptionService fileEncryption;

    @TempDir Path archive;

    @Test
    @DisplayName("old rows are archived and deleted; recent rows and ACCESS_DENIED within a year stay")
    void archivesThenPurges() throws Exception {
        LocalDate today = LocalDate.of(2026, 9, 6);
        Timestamp fourYearsAgo = Timestamp.valueOf(LocalDateTime.of(2022, 6, 1, 12, 0));
        Timestamp twoYearsAgo = Timestamp.valueOf(LocalDateTime.of(2024, 6, 1, 12, 0));
        Timestamp lastMonth = Timestamp.valueOf(LocalDateTime.of(2026, 8, 1, 12, 0));

        // Probes: an ordinary action four years old (purged), a two-year-old ACCESS_DENIED
        // (purged: one year), a two-year-old ordinary action (kept: three years), and
        // a recent ACCESS_DENIED (kept). Plus an old AuditTrail row and an old report
        // access row.
        insertAction("RETENTION_PROBE_OLD", fourYearsAgo);
        insertAction("ACCESS_DENIED", twoYearsAgo);
        insertAction("RETENTION_PROBE_KEPT", twoYearsAgo);
        insertAction("ACCESS_DENIED", lastMonth);
        jdbc.update("""
                INSERT INTO AUDITLOG.AuditTrail (SCHEMA_NAME, TABLE_NAME, RECORD_ID, OPERATION, OLD_VALUES, NEW_VALUES,
                                                 CHANGED_BY, CHANGED_AT, APPLICATION_NAME, COUNTRY_ID)
                VALUES (N'PROBE', N'RetentionProbe', N'1', N'INSERT', NULL, N'{"probe":true}', N'retention-test', ?, N'TEST', 1)
                """, fourYearsAgo);
        jdbc.update("""
                INSERT INTO MOHECORE.REPORT_ACCESS_LOG (REPORT_CODE, EXPORT_FORMAT, USERNAME, FILTER_SUMMARY, ACCESSED_AT, IP_ADDRESS)
                VALUES (N'RETENTION_PROBE', N'PDF', N'retention-test', N'-', ?, N'127.0.0.1')
                """, fourYearsAgo);

        long recentDenied = count("UMSCORE.UserActions", "[Action] = N'ACCESS_DENIED' AND CreatedAt >= ?", Timestamp.valueOf(today.minusYears(1).atStartOfDay()));
        long recentActions = count("UMSCORE.UserActions", "CreatedAt >= ? AND [Action] <> N'ACCESS_DENIED'", Timestamp.valueOf(today.minusYears(3).atStartOfDay()));

        // The job reads AUDITLOG.AuditTrail, which is under RLS: the scheduled entry
        // runs under the system scope through ScheduledTenantScopeAspect, and this
        // direct call does the same.
        Map<String, Integer> purged = TenantContext.runAsSystem(() -> job.run(today, archive));

        assertThat(purged.get("UserActions")).isEqualTo(1);
        assertThat(purged.get("UserActions.ACCESS_DENIED")).isEqualTo(1);
        assertThat(purged.get("AuditTrail")).isEqualTo(1);
        assertThat(purged.get("ReportAccessLog")).isEqualTo(1);

        assertThat(count("UMSCORE.UserActions", "[Action] = N'RETENTION_PROBE_OLD'")).isZero();
        assertThat(count("UMSCORE.UserActions", "[Action] = N'RETENTION_PROBE_KEPT'")).isEqualTo(1);
        assertThat(count("UMSCORE.UserActions", "[Action] = N'ACCESS_DENIED' AND CreatedAt >= ?", Timestamp.valueOf(today.minusYears(1).atStartOfDay())))
                .as("ACCESS_DENIED rows within a year are kept").isEqualTo(recentDenied);
        assertThat(count("UMSCORE.UserActions", "CreatedAt >= ? AND [Action] <> N'ACCESS_DENIED'", Timestamp.valueOf(today.minusYears(3).atStartOfDay())))
                .as("nothing younger than the period is touched").isEqualTo(recentActions);
        assertThat(count("AUDITLOG.AuditTrail", "TABLE_NAME = N'RetentionProbe'")).isZero();
        assertThat(count("MOHECORE.REPORT_ACCESS_LOG", "REPORT_CODE = N'RETENTION_PROBE'")).isZero();

        // The archive holds what was deleted, one JSON object per line.
        List<Path> files;
        try (Stream<Path> s = Files.list(archive)) { files = s.toList(); }
        assertThat(files).hasSize(4);
        String userActions = files.stream().filter(f -> f.getFileName().toString().startsWith("UserActions-"))
                .map(this::readGzip).collect(Collectors.joining());
        assertThat(userActions).contains("RETENTION_PROBE_OLD").doesNotContain("RETENTION_PROBE_KEPT");
        String trail = files.stream().filter(f -> f.getFileName().toString().startsWith("AuditTrail-"))
                .map(this::readGzip).collect(Collectors.joining());
        assertThat(trail).contains("RetentionProbe").contains("probe");
    }

    private void insertAction(String action, Timestamp at) {
        jdbc.update("INSERT INTO UMSCORE.UserActions (UserId, [Action], ResourceType, CreatedAt) VALUES (1, ?, N'TEST', ?)", action, at);
    }

    private long count(String table, String where, Object... args) {
        Long n = jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE " + where, Long.class, args);
        return n == null ? 0 : n;
    }

    /** PRT-08: a finished archive is encrypted (.enc) with the document key, then gzip inside. */
    private String readGzip(Path file) {
        try (BufferedReader r = new BufferedReader(new InputStreamReader(new GZIPInputStream(
                file.getFileName().toString().endsWith(".enc")
                        ? new java.io.ByteArrayInputStream(fileEncryption.readDecrypted(file))
                        : Files.newInputStream(file)), StandardCharsets.UTF_8))) {
            return r.lines().collect(Collectors.joining("\n"));
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }
}
