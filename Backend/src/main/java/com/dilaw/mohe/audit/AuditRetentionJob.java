package com.dilaw.mohe.audit;

import com.dilaw.mohe.security.SecurityEventLog;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

/**
 * Retention for the three audit stores (security plan O-35).
 *
 * <p>Nightly, rows older than the retention period are exported to a
 * gzip-compressed NDJSON file under the archive directory and then deleted, in
 * batches, table by table. A batch is deleted only after its rows are on disk
 * and the file is flushed; if the export fails nothing is purged. Defaults:
 * three years for {@code UMSCORE.UserActions}, {@code AUDITLOG.AuditTrail} and
 * {@code MOHECORE.REPORT_ACCESS_LOG}; one year for {@code ACCESS_DENIED} rows,
 * which are the noisiest and the least valuable after a quarter. The run is
 * itself a security event ({@code AUDIT_RETENTION_PURGE}, per table with the
 * count and the archive file), so an operator can see that the record shrank
 * and by how much.
 *
 * <p>{@code ScheduledTenantScopeAspect} runs the scheduled entry under the
 * system scope; {@code AUDITLOG.AuditTrail} is under RLS (V047) and the job
 * must see every country's rows to age them out.
 */
@Component
public class AuditRetentionJob {

    private static final Logger log = LoggerFactory.getLogger(AuditRetentionJob.class);

    /** One purge rule: rows of {@code table} whose {@code timeColumn} is older than the cutoff (and match {@code filter}). */
    public record Rule(String name, String table, String idColumn, String timeColumn, String filter, int years) {}

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final SecurityEventLog securityEventLog;
    private final boolean enabled;
    private final int auditYears;
    private final int accessDeniedYears;
    private final Path archiveDir;
    private final int batchSize;

    public AuditRetentionJob(JdbcTemplate jdbc, ObjectMapper objectMapper, SecurityEventLog securityEventLog,
                             @Value("${mohe.audit.retention.enabled:true}") boolean enabled,
                             @Value("${mohe.audit.retention.audit-years:3}") int auditYears,
                             @Value("${mohe.audit.retention.access-denied-years:1}") int accessDeniedYears,
                             @Value("${mohe.audit.retention.archive-dir:archive/audit}") String archiveDir,
                             @Value("${mohe.audit.retention.batch-size:5000}") int batchSize) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.securityEventLog = securityEventLog;
        this.enabled = enabled;
        this.auditYears = auditYears;
        this.accessDeniedYears = accessDeniedYears;
        this.archiveDir = Paths.get(archiveDir);
        this.batchSize = Math.max(100, batchSize);
    }

    public List<Rule> rules() {
        return List.of(
                new Rule("UserActions.ACCESS_DENIED", "UMSCORE.UserActions", "Id", "CreatedAt",
                        "[Action] = N'ACCESS_DENIED'", accessDeniedYears),
                new Rule("UserActions", "UMSCORE.UserActions", "Id", "CreatedAt",
                        "[Action] <> N'ACCESS_DENIED'", auditYears),
                new Rule("AuditTrail", "AUDITLOG.AuditTrail", "LOG_ID", "CHANGED_AT", null, auditYears),
                new Rule("ReportAccessLog", "MOHECORE.REPORT_ACCESS_LOG", "LOG_ID", "ACCESSED_AT", null, auditYears));
    }

    @Scheduled(cron = "${mohe.audit.retention.cron:0 30 3 * * *}")
    public void nightly() {
        if (!enabled) {
            log.info("Audit retention is off (mohe.audit.retention.enabled=false); nothing purged");
            return;
        }
        run(LocalDate.now(), archiveDir);
    }

    /** Applies every rule as of {@code today}, archiving under {@code archive}. Returns rows purged per rule. */
    public Map<String, Integer> run(LocalDate today, Path archive) {
        Map<String, Integer> purged = new LinkedHashMap<>();
        for (Rule rule : rules()) {
            LocalDateTime cutoff = today.minusYears(rule.years()).atStartOfDay();
            purged.put(rule.name(), purge(rule, cutoff, archive, today));
        }
        return purged;
    }

    private int purge(Rule rule, LocalDateTime cutoff, Path archive, LocalDate today) {
        String where = rule.timeColumn() + " < ?" + (rule.filter() == null ? "" : " AND " + rule.filter());
        String select = "SELECT TOP " + batchSize + " * FROM " + rule.table() + " WHERE " + where
                + " ORDER BY " + rule.idColumn();
        int total = 0;
        Path file = null;
        Writer out = null;
        try {
            while (true) {
                List<Map<String, Object>> rows = jdbc.queryForList(select, Timestamp.valueOf(cutoff));
                if (rows.isEmpty()) break;
                if (out == null) {
                    file = archiveFile(archive, rule, today);
                    out = open(file);
                }
                List<Object> ids = new ArrayList<>(rows.size());
                for (Map<String, Object> row : rows) {
                    out.write(objectMapper.writeValueAsString(plain(row)));
                    out.write('\n');
                    ids.add(row.get(rule.idColumn()));
                }
                out.flush();
                total += deleteByIds(rule, ids);
                if (rows.size() < batchSize) break;
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Audit retention: archive of " + rule.name() + " failed; nothing more purged", e);
        } finally {
            if (out != null) {
                try { out.close(); } catch (IOException e) { log.warn("closing {}", file, e); }
            }
        }
        if (total > 0) {
            log.info("Audit retention: {} row(s) of {} older than {} archived to {} and purged", total, rule.name(), cutoff.toLocalDate(), file);
            securityEventLog.emit("AUDIT_RETENTION_PURGE", null, null, rule.name(), null,
                    total + " rows older than " + cutoff.toLocalDate() + " archived to " + file, null);
        }
        return total;
    }

    private int deleteByIds(Rule rule, List<Object> ids) {
        String marks = String.join(",", java.util.Collections.nCopies(ids.size(), "?"));
        return jdbc.update("DELETE FROM " + rule.table() + " WHERE " + rule.idColumn() + " IN (" + marks + ")", ids.toArray());
    }

    private static Path archiveFile(Path archive, Rule rule, LocalDate today) throws IOException {
        Files.createDirectories(archive);
        String stamp = today.format(DateTimeFormatter.BASIC_ISO_DATE) + "-" + System.currentTimeMillis();
        return archive.resolve(rule.name().replace('.', '_') + "-" + stamp + ".ndjson.gz");
    }

    private static Writer open(Path file) throws IOException {
        return new BufferedWriter(new OutputStreamWriter(new GZIPOutputStream(Files.newOutputStream(file)), StandardCharsets.UTF_8));
    }

    /** JDBC values as JSON-friendly scalars (driver types such as DateTimeOffset become their text form). */
    private static Map<String, Object> plain(Map<String, Object> row) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : row.entrySet()) {
            Object v = e.getValue();
            if (v == null || v instanceof Number || v instanceof String || v instanceof Boolean) out.put(e.getKey(), v);
            else if (v instanceof Timestamp ts) out.put(e.getKey(), ts.toLocalDateTime().toString());
            else if (v instanceof byte[] b) out.put(e.getKey(), java.util.Base64.getEncoder().encodeToString(b));
            else out.put(e.getKey(), v.toString());
        }
        return out;
    }
}
