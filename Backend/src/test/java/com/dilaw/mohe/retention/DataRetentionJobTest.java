package com.dilaw.mohe.retention;

import com.dilaw.mohe.config.tenant.TenantContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * S-12 against the dev database: a withdrawn student untouched for eight
 * years is a candidate, a dry run only counts, and applying the schedule
 * clears what identifies the person while the record itself stays.
 */
@Tag("integration")
@SpringBootTest
@Transactional
class DataRetentionJobTest {

    @Autowired private DataRetentionJob job;
    @Autowired private JdbcTemplate jdbc;

    private static final int JORDAN = 1004;

    /**
     * A copy of an existing student (so every foreign key is satisfied) with the
     * identifying fields, status, country and modified date set for the test.
     */
    private int withdrawnStudent(String name, LocalDateTime modified) {
        return withdrawnStudent(name, modified, modified);
    }

    /**
     * PRT-01: {@code entered} is STUDENT_MIDATE; {@code withdrawnAt} is when the status was
     * set, which V150's trigger records as "now" on the insert and the test then backdates.
     */
    private int withdrawnStudent(String name, LocalDateTime entered, LocalDateTime withdrawnAt) {
        LocalDateTime modified = entered;
        return TenantContext.runAsSystem(() -> {
            Integer withdrawn = jdbc.queryForObject(
                    "SELECT STUDENT_STUDY_STATUS_ID FROM MOHECORE.STUDENT_STUDY_STATUS WHERE CODE = 'WITHDRAWN'", Integer.class);
            Integer template = jdbc.queryForObject(
                    "SELECT MIN(STUDENT_ID) FROM MOHECORE.STUDENT_DATA WHERE IS_DELETED = 0 AND STUDENT_NAME NOT LIKE 'Anonymised%'", Integer.class);
            List<String> cols = jdbc.queryForList(
                    "SELECT name FROM sys.columns WHERE object_id = OBJECT_ID('MOHECORE.STUDENT_DATA') AND is_identity = 0 AND is_computed = 0 ORDER BY column_id",
                    String.class);
            String unique = String.valueOf(System.nanoTime() % 1_000_000_000L);
            Map<String, String> over = new java.util.HashMap<>(Map.of(
                    "STUDENT_NAME", "N'" + name + "'",
                    "STUDENT_EMAIL", "'s12@example.invalid'",
                    "STUDENT_CELLPHONE", "'+962-000'",
                    "STUDENT_PASSPORT_NUMBER", "'P" + unique + "'",
                    "STUDENT_STUDY_STATUS_ID", String.valueOf(withdrawn),
                    "STUDY_COUNTRY_IN", String.valueOf(JORDAN),
                    "STUDENT_MIDATE", "'" + modified.toLocalDate() + "'",
                    "IS_DELETED", "0"));
            // Anything else unique on the template row must not be copied.
            over.put("StudentID", "NULL");
            over.put("StudentNumber", "NULL");
            over.put("NATIONAL_ID", "'N" + unique + "'");
            String targets = cols.stream().map(c -> "[" + c + "]").collect(java.util.stream.Collectors.joining(","));
            String sources = cols.stream().map(c -> over.getOrDefault(c, "[" + c + "]")).collect(java.util.stream.Collectors.joining(","));
            jdbc.update("INSERT INTO MOHECORE.STUDENT_DATA (" + targets + ") SELECT " + sources
                    + " FROM MOHECORE.STUDENT_DATA WHERE STUDENT_ID = ?", template);
            Integer id = jdbc.queryForObject("SELECT MAX(STUDENT_ID) FROM MOHECORE.STUDENT_DATA WHERE STUDENT_NAME = ?", Integer.class, name);
            jdbc.update("UPDATE MOHECORE.STUDENT_STUDY_STATUS_CHANGE SET CHANGED_AT = ? WHERE STUDENT_ID = ?",
                    java.sql.Timestamp.valueOf(withdrawnAt), id);
            // The parent table may be empty on dev: take the first row of whatever the relationship key references.
            List<Map<String, Object>> ref = jdbc.queryForList("""
                    SELECT OBJECT_SCHEMA_NAME(fk.referenced_object_id) + '.' + OBJECT_NAME(fk.referenced_object_id) AS t,
                           COL_NAME(fkc.referenced_object_id, fkc.referenced_column_id) AS c
                    FROM sys.foreign_keys fk JOIN sys.foreign_key_columns fkc ON fkc.constraint_object_id = fk.object_id
                    WHERE fk.parent_object_id = OBJECT_ID('MOHECORE.STUDENT_PARENT')
                      AND COL_NAME(fkc.parent_object_id, fkc.parent_column_id) = 'RELATIONSHIP_ID'
                    """);
            Integer relationship = ref.isEmpty() ? Integer.valueOf(1)
                    : jdbc.queryForObject("SELECT MIN([" + ref.get(0).get("c") + "]) FROM " + ref.get(0).get("t"), Integer.class);
            jdbc.update("INSERT INTO MOHECORE.STUDENT_PARENT (STUDENT_ID, RELATIONSHIP_ID, PARENT_NAME, PHONE, IS_ALIVE, IS_DELETED, STUDY_COUNTRY_IN_ID) "
                    + "VALUES (?, ?, N'Parent of ' + ?, '+962-111', 1, 0, ?)", id, relationship, name, JORDAN);
            jdbc.update("""
                    INSERT INTO STUDENTCORE.student_users (student_id, username, password_hash, status, failed_login_attempts, created_at, is_deleted, STUDY_COUNTRY_IN_ID)
                    VALUES (?, ?, 'hash', 'ACTIVE', 0, SYSUTCDATETIME(), 0, ?)
                    """, id, "s12-" + id + "@example.invalid", JORDAN);
            Integer userId = jdbc.queryForObject("SELECT student_user_id FROM STUDENTCORE.student_users WHERE student_id = ?", Integer.class, id);
            jdbc.update("""
                    INSERT INTO STUDENTCORE.student_sessions (student_user_id, student_id, token, expires_at, created_at, study_country_in_id)
                    VALUES (?, ?, ?, DATEADD(day, 1, SYSUTCDATETIME()), SYSUTCDATETIME(), ?)
                    """, userId, id, "s12-live-" + id, JORDAN);
            return id;
        });
    }

    private Map<String, Object> student(int id) {
        return TenantContext.runAsSystem(() -> jdbc.queryForMap(
                "SELECT STUDENT_NAME, STUDENT_EMAIL, STUDENT_CELLPHONE, STUDENT_PASSPORT_NUMBER, STUDENT_STUDY_STATUS_ID, STUDY_COUNTRY_IN FROM MOHECORE.STUDENT_DATA WHERE STUDENT_ID = ?", id));
    }

    @Test
    @DisplayName("only a terminal-status student past the period is a candidate; a dry run changes nothing")
    void candidatesAndDryRun() {
        String name = "S12 old " + System.nanoTime();
        int old = withdrawnStudent(name, LocalDateTime.now().minusYears(8));
        int recent = withdrawnStudent("S12 recent " + System.nanoTime(), LocalDateTime.now().minusYears(2));

        DataRetentionJob.Report report = job.preview(LocalDateTime.now());
        assertThat(report.dryRun()).isTrue();
        assertThat(report.studentIds()).contains(old).doesNotContain(recent);
        assertThat(report.classes()).extracting(DataRetentionJob.ClassResult::dataClass)
                .containsExactly("portal-sessions", "staff-sessions", "otps", "student-records", "audit");
        assertThat(report.totalAffected()).isZero();
        assertThat(student(old).get("STUDENT_NAME")).isEqualTo(name);
    }

    @Test
    @DisplayName("PRT-01: joined eight years ago, withdrew yesterday: not a candidate")
    void measuredFromTheStatusDate() {
        int recentlyWithdrawn = withdrawnStudent("S12 withdrew yesterday " + System.nanoTime(),
                LocalDateTime.now().minusYears(8), LocalDateTime.now().minusDays(1));
        int longWithdrawn = withdrawnStudent("S12 withdrew long ago " + System.nanoTime(),
                LocalDateTime.now().minusYears(9), LocalDateTime.now().minusYears(8));

        List<Integer> candidates = job.preview(LocalDateTime.now()).studentIds();
        assertThat(candidates).contains(longWithdrawn).doesNotContain(recentlyWithdrawn);

        // A student with no recorded status date is never a candidate.
        TenantContext.runAsSystem(() -> jdbc.update(
                "DELETE FROM MOHECORE.STUDENT_STUDY_STATUS_CHANGE WHERE STUDENT_ID = ?", longWithdrawn));
        assertThat(job.preview(LocalDateTime.now()).studentIds()).doesNotContain(longWithdrawn);
    }

    @Test
    @DisplayName("applying the schedule anonymises the person and keeps the record")
    void applyAnonymises() {
        String name = "S12 apply " + System.nanoTime();
        int id = withdrawnStudent(name, LocalDateTime.now().minusYears(8));
        TenantContext.runAsSystem(() -> jdbc.update(
                "INSERT INTO STUDENTCORE.student_sessions (student_user_id, student_id, token, expires_at, created_at, logged_out_at, study_country_in_id) "
                        + "SELECT student_user_id, ?, ?, DATEADD(day, -200, SYSUTCDATETIME()), DATEADD(day, -201, SYSUTCDATETIME()), DATEADD(day, -200, SYSUTCDATETIME()), ? "
                        + "FROM STUDENTCORE.student_users WHERE student_id = ?", id, "s12-stale-" + id, JORDAN, id));

        DataRetentionJob.Report report = job.apply(LocalDateTime.now());
        assertThat(report.dryRun()).isFalse();
        assertThat(report.studentIds()).contains(id);
        DataRetentionJob.ClassResult students = report.classes().stream()
                .filter(c -> c.dataClass().equals("student-records")).findFirst().orElseThrow();
        assertThat(students.affected()).isEqualTo(students.candidates()).isGreaterThanOrEqualTo(1);
        DataRetentionJob.ClassResult sessions = report.classes().get(0);
        assertThat(sessions.affected()).as("the stale portal session went").isGreaterThanOrEqualTo(1);

        Map<String, Object> row = student(id);
        assertThat(row.get("STUDENT_NAME")).isEqualTo("Anonymised student #" + id);
        assertThat(row.get("STUDENT_EMAIL")).isNull();
        assertThat(row.get("STUDENT_CELLPHONE")).isNull();
        assertThat(row.get("STUDENT_PASSPORT_NUMBER")).isNull();
        assertThat(row.get("STUDY_COUNTRY_IN")).as("the record keeps its attaché").isEqualTo(JORDAN);
        assertThat(row.get("STUDENT_STUDY_STATUS_ID")).isNotNull();

        TenantContext.runAsSystem(() -> {
            assertThat(jdbc.queryForObject("SELECT PARENT_NAME FROM MOHECORE.STUDENT_PARENT WHERE STUDENT_ID = ?", String.class, id))
                    .isEqualTo("Anonymised");
            assertThat(jdbc.queryForObject("SELECT PHONE FROM MOHECORE.STUDENT_PARENT WHERE STUDENT_ID = ?", String.class, id)).isNull();
            Map<String, Object> account = jdbc.queryForMap("SELECT username, status, is_deleted FROM STUDENTCORE.student_users WHERE student_id = ?", id);
            assertThat(account.get("username")).isEqualTo("anon-" + id + "@retired.invalid");
            assertThat(account.get("status")).isEqualTo("INACTIVE");
            assertThat(account.get("is_deleted")).isEqualTo(true);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM STUDENTCORE.student_sessions WHERE student_id = ?", Integer.class, id)).isZero();
            return null;
        });

        // Already anonymised: not a candidate the second time round.
        assertThat(job.preview(LocalDateTime.now()).studentIds()).doesNotContain(id);
    }

    @Test
    @DisplayName("the report is written as JSON where the operator can read it")
    void reportFile(@org.junit.jupiter.api.io.TempDir java.nio.file.Path dir) {
        DataRetentionJob.Report report = job.preview(LocalDateTime.now());
        java.nio.file.Path file = job.write(report, dir);
        assertThat(file).isNotNull().exists();
        assertThat(file.getFileName().toString()).startsWith("retention_").contains("dry-run").endsWith(".json");
        assertThat(List.of(job.schedule().keySet().toArray())).contains("studentYears", "sessionsDays");
    }
}
