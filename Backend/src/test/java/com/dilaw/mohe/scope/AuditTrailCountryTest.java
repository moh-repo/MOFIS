package com.dilaw.mohe.scope;

import com.dilaw.mohe.audit.MoheAuditLog;
import com.dilaw.mohe.audit.MoheAuditLogService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * The audit log, which was the largest of the ten scope gaps.
 *
 * <p>{@code AUDITLOG.AuditTrail} holds 11,544 rows, <b>11,076 of them individual
 * {@code STUDENT_DATA} changes</b>. It had no country column, so every holder of
 * the {@code AUDIT} permission read every student's change history for every
 * country, and no amount of application code could have filtered it.
 *
 * <p>V047 adds the column, stamps it from the triggers, and backfills. These tests
 * cover both halves: that the reads are filtered, and that the database keeps
 * stamping without any application code asking it to.
 *
 * <p>Read-only apart from one rolled-back write.
 */
@Tag("integration")
@SpringBootTest
class AuditTrailCountryTest {

    @Autowired private MoheAuditLogService auditLogService;
    @Autowired private JdbcTemplate jdbc;

    private static final int JORDAN = 1004;

    @Test
    @DisplayName("an officer reads only their own attaché's audit history")
    void officerSeesOnlyTheirCountry() {
        long ministryRows = AsUser.call(AsUser.SUPERADMIN,
                () -> auditLogService.getAllAuditLogs(0, 1).getTotalElements());
        assumeTrue(ministryRows > 0, "no audit rows to check");

        long jordanRows = AsUser.call(AsUser.JO_ADMIN,
                () -> auditLogService.getAllAuditLogs(0, 1).getTotalElements());

        assertThat(jordanRows)
                .as("""
                    Every student in this database is in Egypt, so Jordan's officer should read \
                    almost nothing here. Seeing the ministry's %d rows would be the original \
                    defect.""", ministryRows)
                .isLessThan(ministryRows);

        List<MoheAuditLog> page = AsUser.call(AsUser.JO_ADMIN,
                () -> auditLogService.getAllAuditLogs(0, 200).getContent());
        assertThat(page)
                .as("every row returned must belong to the reader's own country")
                .allSatisfy(row -> assertThat(row.getCountryId()).isEqualTo(JORDAN));

        // Positive control. `admin` is not ministry — IsMinistryUser and
        // IsSuperAdmin are both 0 — so it runs the scoped query, and its 18
        // assignments cover every country. It must therefore see what the ministry
        // sees. Otherwise "Jordan sees nothing" would also be satisfied by a
        // filter that returns nothing to anybody.
        assertThat(AsUser.call(AsUser.ADMIN, () -> auditLogService.getAllAuditLogs(0, 1).getTotalElements()))
                .as("an officer assigned every country reads every row that has one")
                .isGreaterThan(0L);
    }

    @Test
    @DisplayName("a record's history cannot be read by asking for its id")
    void historyByRecordIdIsScoped() {
        String egyptianStudent = jdbc.queryForObject("""
                SELECT TOP 1 RECORD_ID FROM AUDITLOG.AuditTrail
                WHERE TABLE_NAME = N'STUDENT_DATA' AND COUNTRY_ID = 1
                """, String.class);
        assumeTrue(egyptianStudent != null, "no Egyptian student audit rows");

        assertThat(AsUser.call(AsUser.SUPERADMIN, () -> auditLogService.getAuditLogsByRecordId(egyptianStudent)))
                .as("the ministry can read it, so the row genuinely exists")
                .isNotEmpty();

        assertThat(AsUser.call(AsUser.JO_ADMIN, () -> auditLogService.getAuditLogsByRecordId(egyptianStudent)))
                .as("""
                    Asking by record id was the most direct route to another country's student. \
                    OLD_VALUES and NEW_VALUES carry the record itself.""")
                .isEmpty();

        // The positive control, and the reason it uses `admin` rather than a
        // ministry account: `admin` goes down the *scoped* path — IsMinistryUser
        // and IsSuperAdmin are both 0 — and has Egypt among its 18 assignments.
        // Without this, every assertion in this class would still pass if the
        // filter simply returned nothing to everyone.
        assertThat(AsUser.call(AsUser.ADMIN, () -> auditLogService.getAuditLogsByRecordId(egyptianStudent)))
                .as("an officer whose scope does include Egypt must still get the rows")
                .isNotEmpty();
    }

    @Test
    @DisplayName("rows with no country are ministry-only, not everyone's")
    void nullCountryRowsAreMinistryOnly() {
        Integer nullRows = jdbc.queryForObject(
                "SELECT COUNT(*) FROM AUDITLOG.AuditTrail WHERE COUNTRY_ID IS NULL", Integer.class);
        assumeTrue(nullRows != null && nullRows > 0, "nothing without a country");

        // These are UMSCORE.Users changes — a user account has no country, and that
        // is correct rather than a gap in the backfill. The same holds (S-03, 6 Sep
        // 2026) for the two ministry-level approval requests, a privilege change
        // and a system restore: V096 gives them no country on purpose, so their
        // audit rows are the ministry's and no attaché's.
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM AUDITLOG.AuditTrail
                WHERE COUNTRY_ID IS NULL AND TABLE_NAME <> N'Users'
                  AND NOT (TABLE_NAME = N'ApprovalRequests'
                           AND ISNULL(NEW_VALUES, OLD_VALUES) LIKE N'%"EntityType":"USER_PRIVILEGE_CHANGE"%')
                  AND NOT (TABLE_NAME = N'ApprovalRequests'
                           AND ISNULL(NEW_VALUES, OLD_VALUES) LIKE N'%"EntityType":"SYSTEM_RESTORE"%')
                """, Integer.class))
                .as("anything but Users without a country means the backfill missed something")
                .isZero();

        assertThat(AsUser.call(AsUser.JO_ADMIN, () -> auditLogService.getAuditLogsByTable("Users")))
                .as("""
                    The ministry's own administrative history is not one attaché's business. \
                    Treating an unknown country as 'visible to all' is how the leak comes back.""")
                .isEmpty();
        assertThat(AsUser.call(AsUser.SUPERADMIN, () -> auditLogService.getAuditLogsByTable("Users")))
                .as("the ministry still sees them")
                .isNotEmpty();
    }

    @Test
    @DisplayName("the filter dropdowns do not leak what exists elsewhere")
    void metadataListsAreScoped() {
        List<String> ministryTables = AsUser.call(AsUser.SUPERADMIN, () -> auditLogService.getDistinctTableNames());
        List<String> jordanTables = AsUser.call(AsUser.JO_ADMIN, () -> auditLogService.getDistinctTableNames());

        assertThat(ministryTables).isNotEmpty();
        assertThat(jordanTables)
                .as("""
                    A list of table names is a small leak, but it is still an answer about \
                    another attaché: which of their records have been changed at all.""")
                .isSubsetOf(ministryTables);
        assertThat(AsUser.call(AsUser.JO_ADMIN, () -> auditLogService.getDistinctChangedBy()))
                .as("and the same for who made the changes")
                .isSubsetOf(AsUser.call(AsUser.SUPERADMIN, () -> auditLogService.getDistinctChangedBy()));
    }

    @Test
    @DisplayName("fetching one row by id returns nothing when it is out of scope")
    void getByIdIsScoped() {
        Long egyptianRow = jdbc.queryForObject(
                "SELECT TOP 1 LOG_ID FROM AUDITLOG.AuditTrail WHERE COUNTRY_ID = 1", Long.class);
        assumeTrue(egyptianRow != null, "no Egyptian audit rows");

        assertThat(AsUser.call(AsUser.SUPERADMIN, () -> auditLogService.getById(egyptianRow))).isNotNull();
        assertThat(AsUser.call(AsUser.JO_ADMIN, () -> auditLogService.getById(egyptianRow)))
                .as("null, not the row — and null rather than a 403, which would confirm it exists")
                .isNull();
    }

    @Test
    @DisplayName("the database stamps the country itself, with no application code involved")
    void theDatabaseStampsTheCountry() {
        Integer studentId = jdbc.queryForObject(
                "SELECT TOP 1 STUDENT_ID FROM MOHECORE.STUDENT_DATA WHERE STUDY_COUNTRY_IN IS NOT NULL ORDER BY STUDENT_ID",
                Integer.class);
        assumeTrue(studentId != null, "no students");
        Integer expected = jdbc.queryForObject(
                "SELECT STUDY_COUNTRY_IN FROM MOHECORE.STUDENT_DATA WHERE STUDENT_ID = ?", Integer.class, studentId);

        // Raw JDBC in its own transaction, rolled back by hand: the point is what
        // the trigger does, so nothing here may set COUNTRY_ID.
        Long before = jdbc.queryForObject("SELECT ISNULL(MAX(LOG_ID), 0) FROM AUDITLOG.AuditTrail", Long.class);
        try {
            jdbc.update("UPDATE MOHECORE.STUDENT_DATA SET STUDENT_MAJOR_NAME = ISNULL(STUDENT_MAJOR_NAME, N'') "
                      + "WHERE STUDENT_ID = ?", studentId);
            jdbc.update("UPDATE MOHECORE.STUDENT_DATA SET STUDENT_MAJOR_NAME = ISNULL(STUDENT_MAJOR_NAME, N'') + N' ' "
                      + "WHERE STUDENT_ID = ?", studentId);

            Integer stamped = jdbc.queryForObject("""
                    SELECT TOP 1 COUNTRY_ID FROM AUDITLOG.AuditTrail
                    WHERE LOG_ID > ? AND TABLE_NAME = N'STUDENT_DATA' AND RECORD_ID = ?
                    ORDER BY LOG_ID DESC
                    """, Integer.class, before, String.valueOf(studentId));

            assertThat(stamped)
                    .as("""
                        TR_AuditTrail_ResolveCountry fills this after the row lands. If it ever \
                        stops, new audit rows go back to being invisible to their own country \
                        and visible to nobody but the ministry — silently.""")
                    .isEqualTo(expected);
        } finally {
            jdbc.update("UPDATE MOHECORE.STUDENT_DATA SET STUDENT_MAJOR_NAME = "
                      + "LEFT(STUDENT_MAJOR_NAME, LEN(STUDENT_MAJOR_NAME)) WHERE STUDENT_ID = ?", studentId);
            jdbc.update("DELETE FROM AUDITLOG.AuditTrail WHERE LOG_ID > ?", before);
        }
    }

    @Test
    @DisplayName("every table under audit either resolves a country or is declared ministry-level")
    void everyAuditedTableHasACountrySource() {
        List<String> unresolved = jdbc.queryForList("""
                SELECT DISTINCT [SCHEMA_NAME] + N'.' + TABLE_NAME
                FROM AUDITLOG.AuditTrail
                WHERE COUNTRY_ID IS NULL
                  AND TABLE_NAME NOT IN (N'Users')
                  AND NOT (TABLE_NAME = N'ApprovalRequests'
                           AND ISNULL(NEW_VALUES, OLD_VALUES) LIKE N'%"EntityType":"USER_PRIVILEGE_CHANGE"%')
                  AND NOT (TABLE_NAME = N'ApprovalRequests'
                           AND ISNULL(NEW_VALUES, OLD_VALUES) LIKE N'%"EntityType":"SYSTEM_RESTORE"%')
                """, String.class);

        assertThat(unresolved)
                .as("""
                    A table put under audit later will write rows with no country, and those are \
                    ministry-only — so its own attaché would not see its own history. Add it to \
                    TR_AuditTrail_ResolveCountry in V047, or list it here as ministry-level \
                    (as Users and the two ministry-level approval requests are): \
                    %s""", unresolved)
                .isEmpty();
    }
}
