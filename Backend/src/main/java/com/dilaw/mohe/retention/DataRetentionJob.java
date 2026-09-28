package com.dilaw.mohe.retention;

import com.dilaw.mohe.config.tenant.TenantContext;
import com.dilaw.mohe.security.SecurityEventLog;
import com.dilaw.mohe.service.StudentFileStorageService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Data retention and minimisation (security plan S-12).
 *
 * <p>One schedule per data class, a dry run first. Nothing is anonymised
 * until an operator has read a dry-run report and switched
 * {@code MOHE_RETENTION_DRY_RUN} off (or called the apply endpoint), because
 * the periods are a legal decision the plan leaves to the ministry; the
 * defaults here are placeholders that err on the long side.
 *
 * <ul>
 *   <li><b>portal-sessions</b>: student portal sessions expired or logged out
 *       longer than {@code sessions-days} ago are deleted (both tables).</li>
 *   <li><b>staff-sessions</b>: inactive or expired {@code UserSessions} older
 *       than the same period are deleted.</li>
 *   <li><b>otps</b>: password-reset codes live in memory for ten minutes;
 *       nothing to do, reported for completeness.</li>
 *   <li><b>student-records</b>: a student who has been in a terminal study
 *       status ({@code student-terminal-statuses}, codes of the
 *       {@code study-status} list) for more than {@code student-years} is
 *       anonymised: identifying fields on the student row and its archive
 *       copies are cleared, parents and travelling family members renamed,
 *       the portal account retired, its sessions deleted, the student's own
 *       words in change requests, info-request answers and inquiries cleared,
 *       and the stored documents, their earlier versions and the photo removed
 *       from disk. The student id, country, programme and finance rows stay,
 *       so the totals reports keep adding up.
 *       <p>PRT-01: "for more than N years" is measured from the date the
 *       status was set ({@code MOHECORE.STUDENT_STUDY_STATUS_CHANGE}, V150),
 *       not from {@code STUDENT_MIDATE}, the date the record was entered. A
 *       student with no recorded date, or whose recorded status is not the
 *       current one, is never a candidate: deletion cannot be undone, so no
 *       evidence means no anonymisation.</li>
 *   <li><b>audit</b>: handled by {@code AuditRetentionJob} (O-35); listed in
 *       the report so one document describes the whole schedule.</li>
 * </ul>
 *
 * <p>Reports go to {@code report-dir} as JSON, and a summary is emitted on the
 * security event log as {@code DATA_RETENTION_APPLIED} / {@code _DRY_RUN}.
 */
@Component
public class DataRetentionJob {

    private static final Logger log = LoggerFactory.getLogger(DataRetentionJob.class);
    private static final String ANONYMISED_PREFIX = "Anonymised student #";

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final SecurityEventLog securityEventLog;
    private final StudentFileStorageService fileStorage;
    private final TransactionTemplate perStudentTx;
    private final boolean enabled;
    private final boolean dryRunByDefault;
    private final int sessionsDays;
    private final int studentYears;
    private final List<String> terminalStatuses;
    private final String reportDir;

    public DataRetentionJob(JdbcTemplate jdbc, ObjectMapper objectMapper, SecurityEventLog securityEventLog,
                            StudentFileStorageService fileStorage,
                            PlatformTransactionManager transactionManager,
                            @Value("${mohe.retention.enabled:true}") boolean enabled,
                            @Value("${mohe.retention.dry-run:true}") boolean dryRunByDefault,
                            @Value("${mohe.retention.sessions-days:90}") int sessionsDays,
                            @Value("${mohe.retention.student-years:7}") int studentYears,
                            @Value("${mohe.retention.student-terminal-statuses:WITHDRAWN,TRANSFERRED,DECEASED}") String terminalStatuses,
                            @Value("${mohe.retention.report-dir:archive/retention}") String reportDir) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.securityEventLog = securityEventLog;
        this.fileStorage = fileStorage;
        this.perStudentTx = new TransactionTemplate(transactionManager);
        this.enabled = enabled;
        this.dryRunByDefault = dryRunByDefault;
        this.sessionsDays = Math.max(1, sessionsDays);
        this.studentYears = Math.max(1, studentYears);
        this.terminalStatuses = Arrays.stream(terminalStatuses.split(",")).map(String::trim)
                .filter(s -> !s.isEmpty()).map(String::toUpperCase).toList();
        this.reportDir = reportDir;
    }

    /** What one class of data would lose, or lost. */
    public record ClassResult(String dataClass, String rule, int candidates, int affected, String note) {}

    public record Report(LocalDateTime ranAt, boolean dryRun, List<ClassResult> classes, List<Integer> studentIds) {
        public int totalAffected() {
            return classes.stream().mapToInt(ClassResult::affected).sum();
        }
    }

    @Scheduled(cron = "${mohe.retention.cron:0 45 3 * * *}")
    public void nightly() {
        if (!enabled) return;
        try {
            // runAsSystem: retention is about every country's rows by definition; the job runs
            // on nobody's behalf and its outcome is reported and audited, not returned to a caller.
            Report report = TenantContext.runAsSystem(() -> run(LocalDateTime.now(), dryRunByDefault));
            write(report, Paths.get(reportDir));
        } catch (Exception e) {
            log.error("Data retention run failed: {}", e.getMessage(), e);
        }
    }

    /** A dry run: counts only. */
    public Report preview(LocalDateTime now) {
        // runAsSystem: a dry run counts every country's rows; nothing is changed or returned but counts.
        return TenantContext.runAsSystem(() -> run(now, true));
    }

    /**
     * The real thing, whatever the property says; for the audited admin endpoint.
     *
     * <p>PRT-09: neither this nor {@link #run} is {@code @Transactional} any more. A
     * transaction opened here borrowed its connection before {@code runAsSystem} ran, so
     * the elevation stamped nothing (see {@code StudentUserAuthService.login}); and one
     * transaction around every student meant a failure part-way through a student was
     * caught, logged, and that student's half-done updates committed with everyone
     * else's. Each student is now its own transaction, opened inside the elevation.
     */
    public Report apply(LocalDateTime now) {
        // runAsSystem: applying the schedule touches every country; the controller behind it is
        // SYSTEM_ADMIN-only and the run is audited.
        return TenantContext.runAsSystem(() -> run(now, false));
    }

    public Report run(LocalDateTime now, boolean dryRun) {
        List<ClassResult> results = new ArrayList<>();
        LocalDateTime sessionCutoff = now.minusDays(sessionsDays);
        LocalDateTime studentCutoff = now.minusYears(studentYears);

        results.add(sessions(dryRun, sessionCutoff));
        results.add(staffSessions(dryRun, sessionCutoff));
        results.add(new ClassResult("otps", "in memory, 10 minutes", 0, 0, "nothing stored"));
        List<Integer> students = studentCandidates(studentCutoff);
        results.add(studentRecords(dryRun, students, studentCutoff));
        results.add(new ClassResult("audit", "AuditRetentionJob (O-35): 3 years, ACCESS_DENIED 1 year", 0, 0, "see mohe.audit.retention.*"));

        Report report = new Report(now, dryRun, results, students);
        String summary = results.stream()
                .map(r -> r.dataClass() + "=" + r.candidates() + (dryRun ? "" : "/" + r.affected()))
                .reduce((a, b) -> a + "," + b).orElse("");
        securityEventLog.emit(dryRun ? "DATA_RETENTION_DRY_RUN" : "DATA_RETENTION_APPLIED", null, "system",
                "Retention", null, summary, null);
        log.info("Data retention {}: {}", dryRun ? "dry run" : "applied", summary);
        return report;
    }

    private ClassResult sessions(boolean dryRun, LocalDateTime cutoff) {
        String rule = "expired or logged out before " + cutoff.toLocalDate();
        Integer a = jdbc.queryForObject("SELECT COUNT(*) FROM STUDENTCORE.student_sessions WHERE expires_at < ? OR logged_out_at < ?",
                Integer.class, cutoff, cutoff);
        Integer b = jdbc.queryForObject("SELECT COUNT(*) FROM MOHECORE.StudentPortalSession WHERE ExpiresAt < ?", Integer.class, cutoff);
        int candidates = (a == null ? 0 : a) + (b == null ? 0 : b);
        if (dryRun || candidates == 0) return new ClassResult("portal-sessions", rule, candidates, 0, null);
        int deleted = jdbc.update("DELETE FROM STUDENTCORE.student_sessions WHERE expires_at < ? OR logged_out_at < ?", cutoff, cutoff)
                + jdbc.update("DELETE FROM MOHECORE.StudentPortalSession WHERE ExpiresAt < ?", cutoff);
        return new ClassResult("portal-sessions", rule, candidates, deleted, null);
    }

    private ClassResult staffSessions(boolean dryRun, LocalDateTime cutoff) {
        String rule = "inactive, or expired before " + cutoff.toLocalDate() + ", and created before it";
        String where = "WHERE CreatedAt < ? AND (IsActive = 0 OR ExpiresAt < ?)";
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM UMSCORE.UserSessions " + where, Integer.class, cutoff, cutoff);
        int candidates = n == null ? 0 : n;
        if (dryRun || candidates == 0) return new ClassResult("staff-sessions", rule, candidates, 0, null);
        int deleted = jdbc.update("DELETE FROM UMSCORE.UserSessions " + where, cutoff, cutoff);
        return new ClassResult("staff-sessions", rule, candidates, deleted, null);
    }

    /** Where V150 records when each student's study status was last set. */
    static final String STATUS_CHANGE_TABLE = "MOHECORE.STUDENT_STUDY_STATUS_CHANGE";

    /**
     * Students who entered their terminal status before {@code cutoff}, not yet anonymised.
     *
     * <p>PRT-01: the date is when the status was set, from {@value #STATUS_CHANGE_TABLE}
     * (kept by a trigger on STUDENT_DATA, backfilled conservatively from the quarterly
     * archive; see V150). It used to be {@code STUDENT_MIDATE}, the date the record was
     * entered, so a student who joined eight years ago and withdrew yesterday was
     * anonymised — files deleted — on the next apply. The join requires the recorded
     * status to be the current one; a student with no row, or a row that disagrees, is
     * left out. Without the table (V150 not applied) nobody is a candidate.
     *
     * <p>Deleted students are included on purpose (FIX-PLAN P5-11): a delete is a soft delete,
     * so their personal data is still in the table, and retention exists to anonymise it by
     * age; leaving them out would keep it for ever.
     */
    List<Integer> studentCandidates(LocalDateTime cutoff) {
        if (terminalStatuses.isEmpty()) return List.of();
        Integer table = jdbc.queryForObject("SELECT OBJECT_ID(N'" + STATUS_CHANGE_TABLE + "', N'U')", Integer.class);
        if (table == null) {
            log.warn("Data retention: {} is missing (V150 not applied); no student is a candidate", STATUS_CHANGE_TABLE);
            return List.of();
        }
        String marks = String.join(",", java.util.Collections.nCopies(terminalStatuses.size(), "?"));
        List<Object> args = new ArrayList<>(terminalStatuses);
        args.add(cutoff);
        args.add(ANONYMISED_PREFIX + "%");
        return jdbc.queryForList("""
                SELECT d.STUDENT_ID FROM MOHECORE.STUDENT_DATA d
                JOIN MOHECORE.STUDENT_STUDY_STATUS s ON s.STUDENT_STUDY_STATUS_ID = d.STUDENT_STUDY_STATUS_ID
                JOIN %s c ON c.STUDENT_ID = d.STUDENT_ID
                       AND c.STUDENT_STUDY_STATUS_ID = d.STUDENT_STUDY_STATUS_ID
                WHERE s.CODE IN (%s) AND c.CHANGED_AT < ?
                  AND (d.STUDENT_NAME IS NULL OR d.STUDENT_NAME NOT LIKE ?)
                ORDER BY d.STUDENT_ID
                """.formatted(STATUS_CHANGE_TABLE, marks), Integer.class, args.toArray());
    }

    private ClassResult studentRecords(boolean dryRun, List<Integer> students, LocalDateTime cutoff) {
        String rule = "status in " + terminalStatuses + " since before " + cutoff.toLocalDate();
        if (dryRun || students.isEmpty()) {
            return new ClassResult("student-records", rule, students.size(), 0, null);
        }
        int done = 0;
        List<String> notes = new ArrayList<>();
        for (Integer id : students) {
            try {
                anonymiseInOwnTransaction(id, notes);
                done++;
            } catch (RuntimeException e) {
                log.error("Anonymising student {} failed: {}", id, e.getMessage());
                notes.add("student " + id + ": " + e.getMessage());
            }
        }
        return new ClassResult("student-records", rule, students.size(), done, notes.isEmpty() ? null : String.join("; ", notes));
    }

    /**
     * PRT-09: one student, one transaction. The rows are all changed or none are, and the
     * files are deleted only once that transaction has committed, so a rollback never
     * leaves a record whose files are already gone. (Joined to a caller's transaction, as
     * in the integration test, the files wait for that one's commit.)
     */
    void anonymiseInOwnTransaction(int id, List<String> notes) {
        perStudentTx.executeWithoutResult(status -> {
            List<String> files = anonymise(id);
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    deleteFiles(id, files, notes);
                }
            });
        });
    }

    /**
     * Clears what identifies student {@code id}; returns the stored paths of their files
     * (documents, earlier document versions, photo) for deletion after the commit.
     */
    private List<String> anonymise(int id) {
        String label = ANONYMISED_PREFIX + id;
        // Read before the rows below are rewritten.
        List<String> files = new ArrayList<>(jdbc.queryForList(
                "SELECT FILE_PATH FROM MOHECORE.STUDENT_DOCUMENTS WHERE STUDENT_ID = ?", String.class, id));
        // PRT-02: every earlier version of a document is a file of its own, and so is the photo.
        files.addAll(jdbc.queryForList(
                "SELECT FILE_PATH FROM MOHECORE.STUDENT_DOCUMENT_HISTORY WHERE STUDENT_ID = ?", String.class, id));
        files.addAll(jdbc.queryForList(
                "SELECT PICTURE_PATH FROM MOHECORE.STUDENT_DATA WHERE STUDENT_ID = ?", String.class, id));

        jdbc.update("""
                UPDATE MOHECORE.STUDENT_DATA SET STUDENT_NAME = ?, FirstName = NULL, LastName = NULL, STUDENT_EMAIL = NULL,
                  STUDENT_CELLPHONE = NULL, NATIONAL_ID = NULL, STUDENT_PASSPORT_NUMBER = NULL, STUDENT_PASSPORT_ISSUE_DATE = NULL,
                  STUDENT_PASSPORT_EXPIRE_DATE = NULL, STUDENT_BIRTHDATE = NULL, STUDENT_BIRTHDATE_PLACE = NULL,
                  STUDENT_NOTE_1 = NULL, STUDENT_NOTE_2 = NULL, PICTURE_PATH = NULL
                WHERE STUDENT_ID = ?
                """, label, id);
        jdbc.update("""
                UPDATE MOHECORE.STUDENT_DATA_ARCH SET STUDENT_NAME = ?, STUDENT_EMAIL = NULL, STUDENT_CELLPHONE = NULL,
                  STUDENT_PASSPORT_NUMBER = NULL, STUDENT_PASSPORT_ISSUE_DATE = NULL, STUDENT_PASSPORT_EXPIRE_DATE = NULL,
                  STUDENT_BIRTHDATE = NULL, STUDENT_BIRTHDATE_PLACE = NULL, STUDENT_NOTE_1 = NULL, STUDENT_NOTE_2 = NULL,
                  PICTURE_PATH = NULL, PROFILE_SNAPSHOT = NULL
                WHERE STUDENT_ID = ?
                """, label, id);
        jdbc.update("UPDATE MOHECORE.STUDENT_PARENT SET PARENT_NAME = N'Anonymised', NATIONAL_ID = NULL, PHONE = NULL, ADDRESS = NULL, NOTES = NULL WHERE STUDENT_ID = ?", id);
        jdbc.update("""
                UPDATE m SET m.MEMBER_NAME = N'Anonymised', m.PASSPORT_NUMBER = NULL, m.PASSPORT_EXPIRY_DATE = NULL
                FROM MOHECORE.STUDENT_TRAVEL_TICKET_FAMILY_MEMBER m
                JOIN MOHECORE.STUDENT_TRAVEL_TICKET t ON t.TRAVEL_TICKET_ID = m.TRAVEL_TICKET_ID
                WHERE t.STUDENT_ID = ?
                """, id);
        jdbc.update("UPDATE MOHECORE.STUDENT_DEATH_CASE SET NOTES = NULL WHERE STUDENT_ID = ?", id);

        // PRT-02: the student's own words elsewhere. A change request holds the name,
        // passport or phone as it was and as they asked for it; an info-request answer and
        // its staged data are their record again; an inquiry is free text in their words
        // and the reply to it. new_value and Title are NOT NULL.
        jdbc.update("""
                UPDATE STUDENTCORE.student_change_requests SET old_value = NULL, new_value = N'Anonymised',
                  applied_value = NULL, rejection_reason = NULL, employee_comment = NULL
                WHERE student_id = ?
                """, id);
        jdbc.update("""
                UPDATE s SET s.StudentDataJson = NULL, s.StudentFinanceJson = NULL
                FROM MOHECORE.StudentInfoRequestStaging s
                JOIN MOHECORE.StudentInfoRequest r ON r.Id = s.RequestId
                WHERE r.StudentId = ?
                """, id);
        jdbc.update("UPDATE MOHECORE.StudentInfoRequest SET ResponseText = NULL, RejectionReason = NULL WHERE StudentId = ?", id);
        jdbc.update("UPDATE MOHECORE.StudentInquiry SET Title = N'Anonymised', Description = NULL, Response = NULL WHERE StudentId = ?", id);

        // The portal account: retired, unusable, and its sessions gone.
        jdbc.update("""
                UPDATE STUDENTCORE.student_users SET username = ?, password_hash = '!', status = ?,
                  is_deleted = 1, updated_at = SYSUTCDATETIME() WHERE student_id = ?
                """, "anon-" + id + "@retired.invalid", com.dilaw.mohe.codes.PortalAccountStatus.INACTIVE, id);
        jdbc.update("DELETE FROM STUDENTCORE.student_sessions WHERE student_id = ?", id);
        jdbc.update("DELETE FROM MOHECORE.StudentPortalSession WHERE StudentId = ?", id);

        // Documents: the files go after the commit, the rows stay deactivated so the history is honest.
        jdbc.update("UPDATE MOHECORE.STUDENT_DOCUMENTS SET IS_ACTIVE = 0, ORIGINAL_FILENAME = NULL, FILE_PATH = 'retired' WHERE STUDENT_ID = ?", id);
        jdbc.update("UPDATE MOHECORE.STUDENT_DOCUMENT_HISTORY SET ORIGINAL_FILENAME = NULL, FILE_PATH = 'retired' WHERE STUDENT_ID = ?", id);
        return files;
    }

    /** Deletes an anonymised student's files; a file that will not go is noted, not fatal. */
    private void deleteFiles(int id, List<String> files, List<String> notes) {
        for (String path : files) {
            if (path == null || path.isBlank() || RETIRED_PATH.equals(path)) continue;
            try {
                Files.deleteIfExists(fileStorage.resolveStored(path));
            } catch (Exception e) {
                log.warn("Retention: a file of student {} was not removed: {}", id, e.getMessage());
                notes.add("student " + id + ": a file was not removed: " + e.getMessage());
            }
        }
    }

    private static final String RETIRED_PATH = "retired";

    /** Writes the report as JSON under {@code dir}; returns the file, or null when it could not be written. */
    public Path write(Report report, Path dir) {
        try {
            Files.createDirectories(dir);
            Path file = dir.resolve("retention_" + report.ranAt().toLocalDate() + (report.dryRun() ? "_dry-run" : "_applied")
                    + "_" + report.ranAt().toLocalTime().toString().replace(':', '-').replaceAll("\\..*$", "") + ".json");
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(file.toFile(), report);
            return file;
        } catch (IOException e) {
            log.warn("Retention report not written: {}", e.getMessage());
            return null;
        }
    }

    public boolean isDryRunByDefault() {
        return dryRunByDefault;
    }

    public Map<String, Object> schedule() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("enabled", enabled);
        m.put("dryRun", dryRunByDefault);
        m.put("sessionsDays", sessionsDays);
        m.put("studentYears", studentYears);
        m.put("studentTerminalStatuses", terminalStatuses);
        m.put("reportDir", reportDir);
        return m;
    }

    static boolean isAnonymised(String studentName) {
        return studentName != null && studentName.startsWith(ANONYMISED_PREFIX);
    }

    /** For the report: the first date a class would touch, for an operator reading the numbers. */
    public static LocalDate cutoffDate(LocalDateTime now, int days) {
        return now.minusDays(days).toLocalDate();
    }
}
