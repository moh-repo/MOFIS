package com.dilaw.mohe;

import com.dilaw.mohe.model.MoheQuarter;
import com.dilaw.mohe.model.StudentDataArch;
import com.dilaw.mohe.model.StudentDatum;
import com.dilaw.mohe.model.StudentFinance;
import com.dilaw.mohe.model.StudentFinanceArch;
import com.dilaw.mohe.repository.MoheQuarterRepository;
import com.dilaw.mohe.repository.StudentDataArchRepository;
import com.dilaw.mohe.repository.StudentDataRepository;
import com.dilaw.mohe.repository.StudentFinanceArchRepository;
import com.dilaw.mohe.repository.StudentFinanceRepository;
import com.dilaw.mohe.scope.AsUser;
import com.dilaw.mohe.service.MoheQuarterCreateService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The ministry's period roll, run for real against the live 941 students and then
 * rolled back.
 *
 * <p>{@code QuarterRolloverRulesTest} owns the rules and {@code MoheQuarterCreateServiceTest}
 * owns the loop, both without a database. What only the database can answer is whether
 * the roll <em>lands</em> — and it did not. Two things were found here that no unit
 * test could have reached:
 *
 * <ul>
 *   <li>{@code MOHE_QUARTER.QUARTER} is the table's primary key and the target of
 *       {@code FK_STUDENT_FINANCE_QUARTER}; {@code ID} is a plain identity column with a
 *       non-unique index. The roll was putting the row id into
 *       {@code STUDENT_FINANCE.MOHE_QUARTER_ID} and the foreign key refused it — which is
 *       why that column is NULL on all 941 live rows.</li>
 *   <li>Every student snapshot the roll takes is a <em>refresh</em> on this data, because
 *       the archive already holds the period being left. Before this change quarter close
 *       inserted unconditionally into a table with no unique index.</li>
 * </ul>
 *
 * <p>{@code @Transactional} on the class, so Spring rolls each method back: this runs the
 * whole rollover across every live student and leaves the database as it found it.
 * Two rolls only — each takes minutes, almost all of it in the per-student profile
 * snapshot (see the note on runtime in the review record).
 */
@SpringBootTest
@Tag("integration")
@Transactional
class QuarterRolloverIntegrationTest {

    @Autowired private MoheQuarterCreateService createService;
    @Autowired private MoheQuarterRepository quarterRepository;
    @Autowired private StudentDataRepository studentDataRepository;
    @Autowired private StudentFinanceRepository studentFinanceRepository;
    @Autowired private StudentDataArchRepository studentDataArchRepository;
    @Autowired private StudentFinanceArchRepository studentFinanceArchRepository;

    /**
     * A request for the earliest quarter with the given number that is still after the
     * latest one on record, so the ordering check passes whichever quarter the database
     * happens to be on when this runs.
     */
    private MoheQuarter nextQuarterRequest(int quarterNumber) {
        int max = quarterRepository.findMaxYearQuarter();
        int year = quarterNumber > max % 10 ? max / 10 : max / 10 + 1;
        MoheQuarter q = new MoheQuarter();
        q.setQuarterYear(year);
        q.setQuarterNumber(quarterNumber);
        q.setQuarterName("Rollover test " + year + "Q" + quarterNumber);
        return q;
    }

    /** 20244 is followed by 20251, not by 20245. */
    private static int nextYYYYQ(int current) {
        return current % 10 < 4 ? current + 1 : (current / 10 + 1) * 10 + 1;
    }

    /** How many student snapshots the archive holds for each period. */
    private Map<String, Long> studentSnapshotsByPeriod() {
        Map<String, Long> counts = new HashMap<>();
        for (StudentDataArch a : studentDataArchRepository.findAll()) {
            counts.merge(String.valueOf(a.getCurrentQuarter()), 1L, Long::sum);
        }
        return counts;
    }

    @Test
    @DisplayName("the defaults name the quarter that follows the latest one, the way the rows there are named")
    void defaultsFollowTheLatestQuarter() {
        int max = quarterRepository.findMaxYearQuarter();
        Map<String, Object> defaults = AsUser.call(AsUser.SUPERADMIN, () -> createService.nextQuarterDefaults());

        assertThat(defaults.get("quarter")).isEqualTo(nextYYYYQ(max));
        assertThat((String) defaults.get("quarterName"))
                .as("the lookup's own Arabic name, then the year")
                .endsWith(String.valueOf(defaults.get("quarterYear")));
        assertThat((String) defaults.get("quarterNameEn")).startsWith("Quarter ");
    }

    @Test
    @DisplayName("the whole ministry rolls over: quarter written, students and finance moved, last quarter cleared")
    void theWholeMinistryRollsOver() {
        List<StudentDatum> studentsBefore = studentDataRepository.findAll();
        int studentCount = studentsBefore.size();
        // Copied out, not held: these entities are managed and the roll mutates them.
        String periodBeingLeft = studentsBefore.get(0).getCurrentQuarter();
        Map<String, Long> snapshotsBefore = studentSnapshotsByPeriod();

        StudentFinance paid = studentFinanceRepository.findAll().stream()
                .filter(f -> f.getWafarAmount() != null && f.getWafarAmount() > 0)
                .findFirst()
                .orElseThrow(() -> new AssertionError("no live finance row carries a wafar amount to check"));
        Integer paidStudent = paid.getId();
        Integer paidWafar = paid.getWafarAmount();
        Integer paidYear = paid.getStudentFinRecievedYear();
        Integer paidNumber = paid.getStudentFinRecievedMonth();

        MoheQuarter request = nextQuarterRequest(4);
        int period = request.getQuarterYear() * 10 + 4;

        Map<String, Object> summary = AsUser.call(AsUser.SUPERADMIN,
                () -> createService.createWithSnapshot(request));

        // ── The quarter row itself ───────────────────────────────────────────
        // QUARTER is NOT NULL and the screen has never had a control for it, so before
        // this change the insert offered NULL and the create failed at the driver.
        MoheQuarter saved = quarterRepository.findById((Integer) summary.get("quarterId")).orElseThrow();
        assertThat(saved.getQuarter()).isEqualTo(period);
        assertThat(summary.get("studentSnapshotsArchived")).isEqualTo(studentCount);
        assertThat(summary.get("levelsAdvanced")).as("Q4 does not turn the academic year").isEqualTo(false);

        // ── Every student ────────────────────────────────────────────────────
        assertThat(studentDataRepository.findAll()).allSatisfy(s -> {
            assertThat(s.getCurrentQuarter()).isEqualTo(String.valueOf(period));
            assertThat(s.getStudentStudyStatusId()).as("nobody is left on NEW").isNotEqualTo(2);
        });

        // ── Every finance row of a live student ──────────────────────────────
        // A finance row whose student was since soft-deleted is not visited by the
        // roll (the roll pages live students), so it keeps last quarter's values;
        // three such rows have been on dev since the QA round of 23 Sep 2026.
        java.util.Set<Integer> liveStudents = studentsBefore.stream()
                .map(StudentDatum::getStudentId).collect(java.util.stream.Collectors.toSet());
        assertThat(studentFinanceRepository.findAll()).filteredOn(f -> liveStudents.contains(f.getId())).allSatisfy(f -> {
            // MOHE_QUARTER_ID is FK_STUDENT_FINANCE_QUARTER against MOHE_QUARTER.QUARTER,
            // so it holds the period. A row id here does not survive the flush.
            assertThat(f.getMoheQuarterId()).isEqualTo(period);
            assertThat(f.getStudentFinRecievedYearMonth()).isEqualTo(period);
            assertThat(f.getStudyReceivedWhomId()).isZero();
            assertThat(f.getStudyReceivedWhomStatusId()).isZero();
            // NOT_SPECIFIED (V072 seeded it as 99), not 0: a row on 0 could not be saved
            // back by the finance form (live QA H2, 23 Sep 2026).
            assertThat(f.getStudyFinRecievedStatus())
                    .isEqualTo(com.dilaw.mohe.service.StudyFinReceivedStatusService.NOT_SPECIFIED_ID_FALLBACK);
            assertThat(f.getWafarAmount()).isNull();
            assertThat(f.getStudyCheckId()).isNull();
        });

        // ── Nothing is lost ──────────────────────────────────────────────────
        assertThat(studentFinanceArchRepository
                .findByStudentIdAndStudentFinRecievedYearAndStudentFinRecievedMonth(
                        paidStudent, paidYear, paidNumber)
                .map(StudentFinanceArch::getWafarAmount))
                .as("the wafar cleared from the live row is on the snapshot of the quarter it belonged to")
                .contains(paidWafar);

        // ── And nobody is snapshotted twice ──────────────────────────────────
        // Before the upsert, a close after a create wrote a second copy of every
        // student.
        //
        // This used to also assert the count was unchanged from before the roll,
        // which said the same thing only while every student already had a
        // snapshot for the period. A student added since the archive was built
        // has none, so the roll writes its first — growth, not duplication.
        // Duplication has a different signature and the first assertion catches
        // it: it would put the count above one per student.
        Map<String, Long> snapshotsAfter = studentSnapshotsByPeriod();
        assertThat(snapshotsAfter.get(periodBeingLeft))
                .as("one snapshot per student for the period left, not two")
                .isEqualTo(studentCount);
        assertThat(snapshotsAfter.get(periodBeingLeft))
                .as("and the roll never loses a snapshot it already had")
                .isGreaterThanOrEqualTo(snapshotsBefore.get(periodBeingLeft));
        assertThat(snapshotsAfter.keySet())
                .as("the roll adds no period to the archive: it re-snapshots the one being left")
                .isEqualTo(snapshotsBefore.keySet());
    }

    @Test
    @DisplayName("the third quarter turns the academic year, and stops at each student's final year")
    void theThirdQuarterTurnsTheAcademicYear() {
        Map<Integer, Integer> levelsBefore = new HashMap<>();
        Map<Integer, Integer> yearsBefore = new HashMap<>();
        for (StudentDatum s : studentDataRepository.findAll()) {
            if (s.getStudentCurrentLevelId() != null) {
                levelsBefore.put(s.getStudentId(), s.getStudentCurrentLevelId());
                yearsBefore.put(s.getStudentId(),
                        s.getStudentStudyNoOfYears() == null ? 0 : s.getStudentStudyNoOfYears());
            }
        }
        assertThat(levelsBefore).as("no student has a level recorded, so there is nothing to check").isNotEmpty();

        Map<String, Object> summary = AsUser.call(AsUser.SUPERADMIN,
                () -> createService.createWithSnapshot(nextQuarterRequest(3)));

        assertThat(summary.get("levelsAdvanced")).isEqualTo(true);

        long moved = 0;
        for (StudentDatum s : studentDataRepository.findAll()) {
            Integer was = levelsBefore.get(s.getStudentId());
            if (was == null) {
                assertThat(s.getStudentCurrentLevelId())
                        .as("a student with no level has nothing to add one to").isNull();
                continue;
            }
            if (!was.equals(s.getStudentCurrentLevelId())) {
                assertThat(was)
                        .as("student %s was advanced out of their final year", s.getStudentId())
                        .isLessThan(yearsBefore.get(s.getStudentId()));
                moved++;
            }
        }
        assertThat(moved).as("the year turned for at least one student").isPositive();
    }
}
