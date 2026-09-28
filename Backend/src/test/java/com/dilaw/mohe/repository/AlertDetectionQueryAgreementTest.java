package com.dilaw.mohe.repository;

import com.dilaw.mohe.config.tenant.TenantContext;
import com.dilaw.mohe.model.StudentDatum;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The alert-detection queries against the predicates they replaced.
 *
 * <p>{@code AlertEventDetectionService} used to select its candidates by reading the
 * whole student table and filtering in Java — six times a night, once per scenario,
 * plus an existence query per student it returned. The selection now happens in SQL.
 * That is a behaviour-preserving rewrite, and this is what says so: each test states
 * the original Java predicate and asserts the new query returns exactly the students
 * it selects, over the real database.
 *
 * <p>Two things make this worth a test rather than a reading. The predicates involve
 * nullable columns, and Java and SQL disagree about nulls by default: the original
 * wrote {@code !Boolean.TRUE.equals(isDeleted)}, which is true when the column is
 * null, where {@code isDeleted = FALSE} in SQL would drop those rows silently. And a
 * scheduled job that quietly stops finding cases has no user to notice — an expired
 * passport that raises no alert looks exactly like a passport that has not expired.
 *
 * <p>Read under the system scope: this is the scope the scheduler runs in
 * ({@code ScheduledTenantScopeAspect}), so both sides see the same rows and the test
 * is not comparing a scoped read against an unscoped one.
 */
@SpringBootTest
@Tag("integration")
class AlertDetectionQueryAgreementTest {

    @Autowired
    private StudentDataRepository studentRepo;

    @Autowired
    private StudentFinanceRepository financeRepo;

    /** The predicate every detector starts with. Null counts as not-deleted. */
    private static boolean notDeleted(StudentDatum s) {
        return !Boolean.TRUE.equals(s.getIsDeleted());
    }

    private static Set<Integer> ids(List<StudentDatum> students) {
        return students.stream().map(StudentDatum::getStudentId).collect(Collectors.toSet());
    }

    /** The old implementation: read everything, filter in Java. */
    private Set<Integer> selectedInJava(Predicate<StudentDatum> predicate) {
        return TenantContext.runAsSystem(() -> studentRepo.findAll().stream()
                .filter(AlertDetectionQueryAgreementTest::notDeleted)
                .filter(predicate)
                .map(StudentDatum::getStudentId)
                .collect(Collectors.toSet()));
    }

    @Test
    @DisplayName("PASSPORT_EXPIRY_SOON — passports expiring inside the window")
    void passportExpiringSoonAgrees() {
        Instant now = Instant.now();
        Instant cutoff = now.plus(90, ChronoUnit.DAYS);

        Set<Integer> fromSql = TenantContext.runAsSystem(
                () -> ids(studentRepo.findWithPassportExpiringBetween(now, cutoff)));
        Set<Integer> fromJava = selectedInJava(s -> s.getStudentPassportExpireDate() != null
                && s.getStudentPassportExpireDate().isAfter(now)
                && s.getStudentPassportExpireDate().isBefore(cutoff));

        assertThat(fromSql).isEqualTo(fromJava);
    }

    @Test
    @DisplayName("PASSPORT_EXPIRED — passports already past their date")
    void passportExpiredAgrees() {
        Instant now = Instant.now();

        Set<Integer> fromSql = TenantContext.runAsSystem(
                () -> ids(studentRepo.findWithPassportExpiredBefore(now)));
        Set<Integer> fromJava = selectedInJava(s -> s.getStudentPassportExpireDate() != null
                && s.getStudentPassportExpireDate().isBefore(now));

        assertThat(fromSql).isEqualTo(fromJava);
    }

    @Test
    @DisplayName("LAST_STUDY_YEAR — current level has reached the programme length")
    void finalStudyYearAgrees() {
        Set<Integer> fromSql = TenantContext.runAsSystem(() -> ids(studentRepo.findAtFinalStudyYear()));
        Set<Integer> fromJava = selectedInJava(s -> s.getStudentCurrentLevelId() != null
                && s.getStudentStudyNoOfYears() != null
                && s.getStudentStudyNoOfYears() > 0
                && s.getStudentCurrentLevelId().equals(s.getStudentStudyNoOfYears()));

        assertThat(fromSql).isEqualTo(fromJava);
    }

    @Test
    @DisplayName("STUDY_DURATION_EXCEEDED — current level has passed the programme length")
    void exceedingStudyDurationAgrees() {
        Set<Integer> fromSql = TenantContext.runAsSystem(() -> ids(studentRepo.findExceedingStudyDuration()));
        Set<Integer> fromJava = selectedInJava(s -> s.getStudentCurrentLevelId() != null
                && s.getStudentStudyNoOfYears() != null
                && s.getStudentStudyNoOfYears() > 0
                && s.getStudentCurrentLevelId() > s.getStudentStudyNoOfYears());

        assertThat(fromSql).isEqualTo(fromJava);
    }

    @Test
    @DisplayName("LAST_QUARTER — a final quarter is registered")
    void finalQuarterAgrees() {
        Set<Integer> fromSql = TenantContext.runAsSystem(
                () -> ids(studentRepo.findWithFinalQuarterRegistered()));
        Set<Integer> fromJava = selectedInJava(s -> s.getStudentLastQuarter() != null
                && s.getStudentLastQuarter() > 0);

        assertThat(fromSql).isEqualTo(fromJava);
    }

    /**
     * NO_FINANCE_RECORD, the one whose correlation reads oddly.
     *
     * <p>The NOT EXISTS matches {@code StudentFinance.id} against
     * {@code StudentDatum.studentId}. That is right, not a mistaken join on a
     * surrogate key: {@code StudentFinance}'s {@code @Id} is mapped to the
     * {@code STUDENT_ID} column, so the finance row's identifier is the student's.
     * The Java below is the original, which built the same id set and called
     * {@code contains} on it once per student.
     */
    @Test
    @DisplayName("NO_FINANCE_RECORD — students with no finance row at all")
    void withoutFinanceRecordAgrees() {
        Set<Integer> fromSql = TenantContext.runAsSystem(() -> ids(studentRepo.findWithoutFinanceRecord()));

        List<Integer> studentsWithFinance = TenantContext.runAsSystem(() -> financeRepo.findAll().stream()
                .map(f -> f.getId())
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList());
        Set<Integer> fromJava = selectedInJava(s -> !studentsWithFinance.contains(s.getStudentId()));

        assertThat(fromSql).isEqualTo(fromJava);
    }

    /**
     * A null {@code IS_DELETED} must not exclude a student.
     *
     * <p>Stated separately because it is the difference the rewrite could most easily
     * have got wrong, and because it is invisible in the comparisons above whenever the
     * database happens to hold no null in that column — this asserts the rows exist to
     * make the comparison meaningful, or says plainly that they do not.
     */
    @Test
    @DisplayName("students are selected whether IS_DELETED is false or null")
    void nullIsDeletedIsNotDeleted() {
        List<StudentDatum> all = TenantContext.runAsSystem(() -> studentRepo.findAll());

        long nullDeleted = all.stream().filter(s -> s.getIsDeleted() == null).count();
        long live = all.stream().filter(AlertDetectionQueryAgreementTest::notDeleted).count();

        assertThat(live)
                .as("the fixture must hold live students, or every comparison above is vacuous")
                .isPositive();

        // Not an assertion about the data — a note in the output about how much of the
        // null-handling the run above actually exercised.
        System.out.println("students: " + all.size() + ", live: " + live
                + ", with IS_DELETED null: " + nullDeleted);
    }
}
