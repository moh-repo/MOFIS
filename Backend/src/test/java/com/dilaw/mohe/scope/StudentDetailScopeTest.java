package com.dilaw.mohe.scope;

import com.dilaw.mohe.model.StudentDatum;
import com.dilaw.mohe.service.StudentAccessGuard;
import com.dilaw.mohe.service.StudentDataService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import com.dilaw.mohe.config.tenant.TenantContext;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * The detail endpoints behind a student, which the country scope did not reach.
 *
 * <p>The lists were scoped from the first round, so a country officer never sees
 * another attaché's student in a list. That hid the ids and nothing more: student
 * ids are small sequential integers, and every by-id endpoint —
 * timeline, year results, documents, the document files themselves, parents and
 * relatives — resolved a raw {@code studentId} through a service that had no
 * notion of a caller. Measured against the running server before the fix,
 * {@code jo_admin} downloaded an Egyptian student's document: 7,491 bytes of
 * {@code image/png}.
 *
 * <p>Every denial below is paired with a ministry positive control on the same
 * id, so a guard that simply refuses everything cannot pass as a working one.
 */
@Tag("integration")
@SpringBootTest
class StudentDetailScopeTest {

    @Autowired private StudentAccessGuard guard;
    @Autowired private StudentDataService studentDataService;
    @Autowired private JdbcTemplate jdbc;

    private static final int EGYPT = 1;

    /** An Egyptian student, which is every student in this database. */
    private Integer anEgyptianStudent() {
        return jdbc.queryForObject(
                "SELECT TOP 1 STUDENT_ID FROM MOHECORE.STUDENT_DATA WHERE STUDY_COUNTRY_IN = ? ORDER BY STUDENT_ID",
                Integer.class, EGYPT);
    }

    @Test
    @DisplayName("a country officer cannot reach a student outside their attaché")
    void officerCannotReachOutOfCountryStudent() {
        Integer studentId = anEgyptianStudent();
        assumeTrue(studentId != null, "no Egyptian student seeded");

        StudentDatum ministrySees = AsUser.call(AsUser.SUPERADMIN, () -> guard.requireStudent(studentId));
        assertThat(ministrySees)
                .as("positive control: the ministry must reach this student, or the denial below proves nothing")
                .isNotNull();

        ScopeDenial.assertRefused("a student",
                () -> AsUser.call(AsUser.JO_ADMIN, () -> guard.requireStudent(studentId)));

        assertThatThrownBy(() -> AsUser.call(AsUser.SA_ADMIN, () -> guard.requireStudent(studentId)))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    @DisplayName("a document resolves to its student, and carries that student's scope")
    void documentInheritsItsStudentsScope() {
        Integer documentId = jdbc.queryForObject("""
                SELECT TOP 1 d.DOCUMENT_ID
                  FROM MOHECORE.STUDENT_DOCUMENTS d
                  JOIN MOHECORE.STUDENT_DATA s ON s.STUDENT_ID = d.STUDENT_ID
                 WHERE s.STUDY_COUNTRY_IN = ?
                 ORDER BY d.DOCUMENT_ID
                """, Integer.class, EGYPT);
        assumeTrue(documentId != null, "no document on an Egyptian student");

        assertThat(AsUser.call(AsUser.SUPERADMIN, () -> guard.requireDocument(documentId)))
                .as("positive control: the ministry must be able to open the file")
                .isNotNull();

        // This is the one that mattered: /studentdata/document/download/{id} served
        // the decrypted bytes to any officer who named an id.
        ScopeDenial.assertRefused("a document",
                () -> AsUser.call(AsUser.JO_ADMIN, () -> guard.requireDocument(documentId)));
    }

    @Test
    @DisplayName("a missing student is 404 and a missing document is 404, not 403")
    void absentRecordsAreNotFound() {
        assertThatThrownBy(() -> AsUser.call(AsUser.SUPERADMIN, () -> guard.requireStudent(999_999_99)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);

        assertThatThrownBy(() -> AsUser.call(AsUser.SUPERADMIN, () -> guard.requireDocument(999_999_99)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("a document version resolves through its own studentId, not the live document")
    void historyResolvesIndependently() {
        /*
         * STUDENT_DOCUMENT_HISTORY has been empty on every database this suite has
         * run against, so until the third revision pass (27 Aug 2026) this test
         * assumed itself away and the endpoint it guards was never exercised. It
         * now archives one version of an Egyptian student's document itself —
         * inserted as SYSTEM so the row passes the tenant block predicate when the
         * policy is on — and removes it, and the audit rows the table's trigger
         * wrote for it, afterwards.
         */
        Integer documentId = jdbc.queryForObject("""
                SELECT TOP 1 d.DOCUMENT_ID
                  FROM MOHECORE.STUDENT_DOCUMENTS d
                  JOIN MOHECORE.STUDENT_DATA s ON s.STUDENT_ID = d.STUDENT_ID
                 WHERE s.STUDY_COUNTRY_IN = ?
                 ORDER BY d.DOCUMENT_ID
                """, Integer.class, EGYPT);
        assumeTrue(documentId != null, "no document on an Egyptian student");

        Integer historyId = TenantContext.runAsSystem(() -> jdbc.queryForObject("""
                INSERT INTO MOHECORE.STUDENT_DOCUMENT_HISTORY
                       (DOCUMENT_ID, STUDENT_ID, DOCUMENT_NAME, DOCUMENT_TYPE, FILE_PATH, ORIGINAL_FILENAME,
                        VERSION_NO, ACTION, CHANGED_BY, CHANGED_AT, STUDY_COUNTRY_IN_ID)
                SELECT d.DOCUMENT_ID, d.STUDENT_ID, 'scope-test', 'TEST', d.FILE_PATH, 'scope-test.bin',
                       999, 'TEST_ARCHIVE', ?, SYSUTCDATETIME(), ?
                  FROM MOHECORE.STUDENT_DOCUMENTS d WHERE d.DOCUMENT_ID = ?;
                SELECT CAST(SCOPE_IDENTITY() AS INT);
                """, Integer.class, AsUser.SUPERADMIN, EGYPT, documentId));
        assertThat(historyId).as("the fixture must have been archived").isNotNull();

        try {
            assertThat(AsUser.call(AsUser.SUPERADMIN, () -> guard.requireDocumentHistory(historyId)))
                    .as("positive control: the ministry must reach the archived version")
                    .isNotNull();
            // The version carries its student's country, and Jordan is not it.
            ScopeDenial.assertRefused("an archived document version",
                    () -> AsUser.call(AsUser.JO_ADMIN, () -> guard.requireDocumentHistory(historyId)));
        } finally {
            TenantContext.runAsSystem(() -> {
                jdbc.update("DELETE FROM MOHECORE.STUDENT_DOCUMENT_HISTORY WHERE HISTORY_ID = ?", historyId);
                jdbc.update("DELETE FROM AUDITLOG.AuditTrail WHERE TABLE_NAME = 'STUDENT_DOCUMENT_HISTORY' AND RECORD_ID = ?",
                        String.valueOf(historyId));
            });
        }
    }

    @Test
    @DisplayName("a student with no country on record is ministry-only, not everybody's")
    void nullCountryStudentIsMinistryOnly() {
        /*
         * Built by copying a real row rather than by listing values. STUDENT_DATA
         * carries twenty foreign keys and several of them default to a value the
         * parent table does not hold, so a hand-written INSERT fails one
         * constraint at a time. Copying an existing student inherits values that
         * are known good; only the two columns under test are overridden.
         */
        Integer template = anEgyptianStudent();
        assumeTrue(template != null, "no student to copy the shape from");

        /*
         * STUDENT_DATA is under audit, and this row has no country by design — so
         * inserting and deleting it leaves audit rows with a null COUNTRY_ID
         * behind, which is exactly what AuditTrailCountryTest fails on. Same
         * high-water-mark teardown that test uses on its own probe.
         */
        Long auditHighWater = jdbc.queryForObject(
                "SELECT ISNULL(MAX(LOG_ID), 0) FROM AUDITLOG.AuditTrail", Long.class);
        jdbc.update("""
                INSERT INTO MOHECORE.STUDENT_DATA
                       (STUDENT_NAME, RECORD_VISIBILITY, STUDY_COUNTRY_IN,
                        STUDENT_GENDER, STUDENT_COUNTRY_ID, STUDENT_GOV_ID, STUDENT_MUD_ID,
                        STUDENT_DEGREE_ID, STUDENT_MAJOR_ID, STUDENT_CURRENT_LEVEL_ID,
                        STUDENT_STUDY_STATUS_ID, STUDENT_AGENCY_TYPE_ID, STUDENT_MINISTRY_TYPE_ID,
                        STUDENT_STUDY_TYPE_ID, STUDENT_UNIVERSITY_TYPE_ID, STUDY_UNIVERSITY_IN,
                        STUDY_COLLEGE_IN, SYUDY_GOV_IN, STUDY_EDUCATION_IN, STUDY_MAJOR_IN,
                        STUDENT_PASSPORT_ISSUE_COUNTRY_ID)
                SELECT ?, 'ALL', NULL,
                       s.STUDENT_GENDER, s.STUDENT_COUNTRY_ID, s.STUDENT_GOV_ID, s.STUDENT_MUD_ID,
                       s.STUDENT_DEGREE_ID, s.STUDENT_MAJOR_ID, s.STUDENT_CURRENT_LEVEL_ID,
                       s.STUDENT_STUDY_STATUS_ID, s.STUDENT_AGENCY_TYPE_ID, s.STUDENT_MINISTRY_TYPE_ID,
                       s.STUDENT_STUDY_TYPE_ID, s.STUDENT_UNIVERSITY_TYPE_ID, s.STUDY_UNIVERSITY_IN,
                       s.STUDY_COLLEGE_IN, s.SYUDY_GOV_IN, s.STUDY_EDUCATION_IN, s.STUDY_MAJOR_IN,
                       s.STUDENT_PASSPORT_ISSUE_COUNTRY_ID
                  FROM MOHECORE.STUDENT_DATA s
                 WHERE s.STUDENT_ID = ?
                """, "scope probe — null country", template);
        Integer probeId = jdbc.queryForObject(
                "SELECT STUDENT_ID FROM MOHECORE.STUDENT_DATA WHERE STUDENT_NAME = ?",
                Integer.class, "scope probe — null country");
        try {
            assertThat(AsUser.call(AsUser.SUPERADMIN, () -> guard.requireStudent(probeId)))
                    .as("the ministry must still reach a record with no country, or it is unreachable by anyone")
                    .isNotNull();

            assertThatThrownBy(() -> AsUser.call(AsUser.JO_ADMIN, () -> guard.requireStudent(probeId)))
                    .as("""
                        canAccessCountry passes a null through as "no country to check". On a \
                        student record that reads as "visible to everyone", which is the opposite \
                        of what a missing country should mean here.""")
                    .isInstanceOf(ResponseStatusException.class)
                    .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                    .isEqualTo(HttpStatus.FORBIDDEN);
        } finally {
            // By name, not by a computed id: a failed run must not leave the row behind.
            jdbc.update("DELETE FROM MOHECORE.STUDENT_DATA WHERE STUDENT_NAME = ?", "scope probe — null country");
            jdbc.update("DELETE FROM AUDITLOG.AuditTrail WHERE LOG_ID > ?", auditHighWater);
        }
    }

    /** Jordan, the country AsUser.JO_ADMIN is scoped to. */
    private static final Integer JORDAN = 1004;

    @Test
    @DisplayName("the list is still scoped — the guard did not widen anything")
    void listsAreUnchanged() {
        long ministry = AsUser.call(AsUser.SUPERADMIN, () -> studentDataService.getStudentData().size());
        assumeTrue(ministry > 0, "no students seeded");

        var jordan = AsUser.call(AsUser.JO_ADMIN, () -> studentDataService.getStudentData());

        // Asserted as a rule rather than as a number. This used to read "Jordan
        // sees none", which was true only because every student on the database
        // was Egyptian -- it proved the scope worked the way an empty cupboard
        // proves a lock works. With students seeded under Jordan and Saudi
        // Arabia (DEC-23, seed_scope_test_students.sql) the real question can be
        // asked: Jordan sees its own and nobody else's. Both halves hold whether
        // or not that seed has been run.
        assertThat(jordan).allSatisfy(student ->
                assertThat(student.getStudyCountryIn())
                        .as("a student Jordan can see must be studying in Jordan")
                        .isEqualTo(JORDAN));
        assertThat((long) jordan.size())
                .as("and the ministry still sees more than any one attache")
                .isLessThan(ministry);
    }
}
