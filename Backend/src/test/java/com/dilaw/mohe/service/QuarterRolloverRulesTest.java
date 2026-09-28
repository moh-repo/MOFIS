package com.dilaw.mohe.service;

import com.dilaw.mohe.model.MoheQuarter;
import com.dilaw.mohe.model.StudentCurrentLevel;
import com.dilaw.mohe.model.StudentDatum;
import com.dilaw.mohe.model.StudentFinance;
import com.dilaw.mohe.model.StudentStudyStatus;
import com.dilaw.mohe.model.StudyFinReceivedStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The rules of the ministry's period roll.
 *
 * <p>Adding a quarter used to archive both tables and advance four columns on the
 * finance row, and stop there. The student's own period pointer
 * ({@code CURRENT_QUARTER}, text, "20242" on all 941 rows) was never moved, a student
 * entered as NEW stayed NEW for ever, nobody's year of study ever turned over, and last
 * quarter's recipient, received status, wafar and cheque were carried into a quarter
 * nobody had been paid for yet — so the new period read as already settled.
 *
 * <p>No Spring and no database here on purpose: these are the rules themselves, and
 * they are worth reading without either.
 */
class QuarterRolloverRulesTest {

    private static final int NEW = 2;
    private static final int ACTIVE = 1;
    private static final int SUSPENDED = 4;

    /** STUDENT_STUDY_STATUS as the live table holds it: the ids differ from the codes. */
    private static List<StudentStudyStatus> statuses() {
        return List.of(status(ACTIVE, "ACTIVE"), status(NEW, "NEW"),
                status(SUSPENDED, "SUSPENDED"), status(99, "NOT_SPECIFIED"));
    }

    private static StudentStudyStatus status(int id, String code) {
        StudentStudyStatus s = new StudentStudyStatus();
        s.setId(id);
        s.setCode(code);
        return s;
    }

    /** STUDY_FIN_RECEIVED_STATUS as V072 seeded it: NOT_SPECIFIED is 99, not 0. */
    private static final int FIN_NOT_SPECIFIED = 99;

    private static List<StudyFinReceivedStatus> finStatuses() {
        return List.of(finStatus(1, "RECEIVED"), finStatus(2, "NOT_RECEIVED"),
                finStatus(FIN_NOT_SPECIFIED, "NOT_SPECIFIED"), finStatus(100, "DISBURSED"));
    }

    private static StudyFinReceivedStatus finStatus(int id, String code) {
        StudyFinReceivedStatus s = new StudyFinReceivedStatus();
        s.setId(id);
        s.setCode(code);
        return s;
    }

    /**
     * STUDENT_CURRENT_LEVEL as the live table holds it: two families. The legacy one
     * (status 0) runs 1–8 First..Eighth Year, then 9 and 10 for the two extensions and
     * 99 for Not Specified; the second (status 1) runs 100–106 with Graduated and
     * Suspended after it. Both are ordered by ORDER_NO, which is why the ids alone
     * cannot be added to.
     */
    private static List<StudentCurrentLevel> levels() {
        List<StudentCurrentLevel> all = new ArrayList<>();
        for (int i = 1; i <= 8; i++) {
            all.add(level(i, i, 0));
        }
        all.add(level(9, 9, 0));     // تمديد عادي / Regular Extension
        all.add(level(10, 10, 0));   // تمديد استثنائي / Exceptional Extension
        all.add(level(99, 99, 0));   // غير مبين / Not Specified
        for (int i = 0; i <= 6; i++) {
            all.add(level(100 + i, i + 1, 1));
        }
        all.add(level(107, 8, 1));   // متخرج / Graduated
        all.add(level(108, 9, 1));   // موقوف / Suspended
        return all;
    }

    private static StudentCurrentLevel level(int id, int order, int family) {
        StudentCurrentLevel l = new StudentCurrentLevel();
        l.setId(id);
        l.setStudentCurrentLevelOrderNo(order);
        l.setStudentCurrentLevelStatus(family);
        return l;
    }

    private static MoheQuarter quarter(int id, int year, int number) {
        MoheQuarter q = new MoheQuarter();
        q.setId(id);
        q.setQuarterYear(year);
        q.setQuarterNumber(number);
        return q;
    }

    private static QuarterRolloverRules rules(int year, int number) {
        return QuarterRolloverRules.of(quarter(16, year, number), statuses(), levels(), finStatuses());
    }

    private static StudentDatum student(Integer statusId, Integer levelId, Integer years) {
        StudentDatum s = new StudentDatum();
        s.setStudentId(1);
        s.setCurrentQuarter("20253");
        s.setStudentStudyStatusId(statusId);
        s.setStudentCurrentLevelId(levelId);
        s.setStudentStudyNoOfYears(years);
        return s;
    }

    // ── The period pointer ───────────────────────────────────────────────────

    @Test
    @DisplayName("the student's own quarter moves to the new one, as the YYYYQ text the column holds")
    void currentQuarterIsAdvanced() {
        StudentDatum s = student(ACTIVE, null, 7);

        rules(2025, 4).advance(s);

        assertThat(s.getCurrentQuarter()).isEqualTo("20254");
    }

    @Test
    @DisplayName("the year turns over in the code, not just the number: 2025 Q4 is followed by 2026 Q1")
    void currentQuarterCrossesTheYear() {
        StudentDatum s = student(ACTIVE, null, 7);

        rules(2026, 1).advance(s);

        assertThat(s.getCurrentQuarter()).isEqualTo("20261");
    }

    // ── Study status ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("study status")
    class Status {

        @Test
        @DisplayName("a student entered as new is active once a quarter has passed over them")
        void newBecomesActive() {
            StudentDatum s = student(NEW, null, 4);

            rules(2025, 4).advance(s);

            assertThat(s.getStudentStudyStatusId()).isEqualTo(ACTIVE);
        }

        @Test
        @DisplayName("every other status is somebody's decision and is left alone")
        void otherStatusesAreUntouched() {
            StudentDatum suspended = student(SUSPENDED, null, 4);
            StudentDatum active = student(ACTIVE, null, 4);
            StudentDatum none = student(null, null, 4);

            QuarterRolloverRules rules = rules(2025, 4);
            rules.advance(suspended);
            rules.advance(active);
            rules.advance(none);

            assertThat(suspended.getStudentStudyStatusId()).isEqualTo(SUSPENDED);
            assertThat(active.getStudentStudyStatusId()).isEqualTo(ACTIVE);
            assertThat(none.getStudentStudyStatusId()).isNull();
        }

        @Test
        @DisplayName("the two statuses are found by code, not by the ids they happen to carry")
        void statusesAreResolvedByCode() {
            // Same two codes, different ids: nothing may be keyed on 2 and 1.
            List<StudentStudyStatus> renumbered = List.of(status(41, "ACTIVE"), status(42, "NEW"));
            StudentDatum s = student(42, null, 4);

            QuarterRolloverRules.of(quarter(16, 2025, 4), renumbered, levels(), finStatuses()).advance(s);

            assertThat(s.getStudentStudyStatusId()).isEqualTo(41);
        }

        @Test
        @DisplayName("a code list missing either row leaves the status alone rather than guessing")
        void missingCodeLeavesStatusAlone() {
            StudentDatum s = student(NEW, null, 4);

            QuarterRolloverRules.of(quarter(16, 2025, 4), List.of(status(ACTIVE, "ACTIVE")), levels(), finStatuses())
                    .advance(s);

            assertThat(s.getStudentStudyStatusId()).isEqualTo(NEW);
        }
    }

    // ── Year of study ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("year of study")
    class Level {

        @Test
        @DisplayName("the third quarter is the one the academic year turns on")
        void onlyTheThirdQuarterAdvancesTheLevel() {
            assertThat(rules(2026, 3).advancesLevel()).isTrue();
            assertThat(rules(2026, 1).advancesLevel()).isFalse();
            assertThat(rules(2026, 2).advancesLevel()).isFalse();
            assertThat(rules(2026, 4).advancesLevel()).isFalse();
        }

        @Test
        @DisplayName("a second-year student on a seven-year programme moves to third year")
        void secondYearMovesToThird() {
            StudentDatum s = student(ACTIVE, 2, 7);

            rules(2026, 3).advance(s);

            assertThat(s.getStudentCurrentLevelId()).isEqualTo(3);
        }

        @Test
        @DisplayName("the level does not move on any quarter but the third")
        void levelHoldsOutsideTheThirdQuarter() {
            StudentDatum s = student(ACTIVE, 2, 7);

            rules(2026, 4).advance(s);

            assertThat(s.getStudentCurrentLevelId()).isEqualTo(2);
        }

        @Test
        @DisplayName("a student in their final year stays there rather than being pushed into an extension")
        void finalYearIsNotAdvanced() {
            // Fourth year of a four-year programme. Adding one to the id would make
            // this student a fifth-year; adding one to an eighth-year's id would make
            // them "Regular Extension".
            StudentDatum s = student(ACTIVE, 4, 4);

            rules(2026, 3).advance(s);

            assertThat(s.getStudentCurrentLevelId()).isEqualTo(4);
        }

        @Test
        @DisplayName("an eighth-year student does not become Regular Extension")
        void eighthYearDoesNotBecomeAnExtension() {
            StudentDatum s = student(ACTIVE, 8, 8);

            rules(2026, 3).advance(s);

            assertThat(s.getStudentCurrentLevelId()).isEqualTo(8);
        }

        @Test
        @DisplayName("a student already in an extension, or Not Specified, is left where they are")
        void extensionsAndNotSpecifiedAreLeft() {
            StudentDatum extension = student(ACTIVE, 9, 7);
            StudentDatum notSpecified = student(ACTIVE, 99, 7);

            QuarterRolloverRules rules = rules(2026, 3);
            rules.advance(extension);
            rules.advance(notSpecified);

            assertThat(extension.getStudentCurrentLevelId()).isEqualTo(9);
            assertThat(notSpecified.getStudentCurrentLevelId()).isEqualTo(99);
        }

        @Test
        @DisplayName("no level recorded: there is nothing to add one to")
        void nullLevelStaysNull() {
            // 937 of the 941 live rows are in this state.
            StudentDatum s = student(ACTIVE, null, 7);

            rules(2026, 3).advance(s);

            assertThat(s.getStudentCurrentLevelId()).isNull();
        }

        @Test
        @DisplayName("no programme length recorded: the student stays where they are rather than being guessed for")
        void unknownProgrammeLengthDoesNotAdvance() {
            StudentDatum unknown = student(ACTIVE, 2, null);
            StudentDatum zero = student(ACTIVE, 2, 0);

            QuarterRolloverRules rules = rules(2026, 3);
            rules.advance(unknown);
            rules.advance(zero);

            assertThat(unknown.getStudentCurrentLevelId()).isEqualTo(2);
            assertThat(zero.getStudentCurrentLevelId()).isEqualTo(2);
        }

        @Test
        @DisplayName("the move stays inside the row's own family of levels")
        void theLadderDoesNotCrossFamilies() {
            // Third year of the 100-family, order 3. Order 4 exists in both families:
            // id 4 in the legacy one and 103 in this student's own.
            StudentDatum s = student(ACTIVE, 102, 7);

            rules(2026, 3).advance(s);

            assertThat(s.getStudentCurrentLevelId()).isEqualTo(103);
        }

        @Test
        @DisplayName("a level the lookup does not know is left alone")
        void unknownLevelIsLeft() {
            StudentDatum s = student(ACTIVE, 555, 7);

            rules(2026, 3).advance(s);

            assertThat(s.getStudentCurrentLevelId()).isEqualTo(555);
        }
    }

    // ── The finance row ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("the finance row")
    class Finance {

        private StudentFinance paid() {
            StudentFinance f = new StudentFinance();
            f.setId(1);
            f.setMoheQuarterId(15);
            f.setStudentFinRecievedYear(2025);
            f.setStudentFinRecievedMonth(3);
            f.setStudentFinRecievedYearMonth(20253);
            f.setStudentFinRecievedAmount(1500);
            f.setStudyReceivedWhomId(7);
            f.setStudyReceivedWhomStatusId(1);
            f.setStudyFinRecievedStatus(99);
            f.setWafarAmount(1500);
            f.setWafarTypeId(1);
            f.setStudyCheckId("CHK-9001");
            f.setStudyCheckDate(Instant.parse("2025-08-01T00:00:00Z"));
            f.setStudyCheckDetails("paid in full");
            f.setStudyCheckFormNumber("F-12");
            return f;
        }

        @Test
        @DisplayName("the row is moved to the new quarter, YYYYQ key included")
        void financeIsAdvanced() {
            StudentFinance f = paid();

            // The quarter's row id is 16 and its period is 20254. MOHE_QUARTER_ID takes
            // the period: FK_STUDENT_FINANCE_QUARTER references MOHE_QUARTER.QUARTER,
            // the table's real primary key, so a row id here is refused outright.
            rules(2025, 4).advance(f);

            assertThat(f.getMoheQuarterId()).isEqualTo(20254);
            assertThat(f.getStudentFinRecievedYear()).isEqualTo(2025);
            // The column is named MONTH and holds the quarter number.
            assertThat(f.getStudentFinRecievedMonth()).isEqualTo(4);
            assertThat(f.getStudentFinRecievedYearMonth()).isEqualTo(20254);
        }

        @Test
        @DisplayName("who collected last quarter's money is not who collects this quarter's")
        void recipientIsCleared() {
            StudentFinance f = paid();

            rules(2025, 4).advance(f);

            // 0 is what these two columns hold on all 941 live rows: their own default.
            assertThat(f.getStudyReceivedWhomId()).isZero();
            assertThat(f.getStudyReceivedWhomStatusId()).isZero();
        }

        @Test
        @DisplayName("a quarter nobody has been paid for yet does not read as received")
        void receivedStatusIsCleared() {
            StudentFinance f = paid();
            f.setStudyFinRecievedStatus(1);

            rules(2025, 4).advance(f);

            // NOT_SPECIFIED, not 0: 0 is no status at all, and a row carrying it could
            // not be saved back by the finance form (live QA H2, 23 Sep 2026).
            assertThat(f.getStudyFinRecievedStatus()).isEqualTo(FIN_NOT_SPECIFIED);
        }

        @Test
        @DisplayName("NOT_SPECIFIED is found by code, not by the id it happens to carry")
        void notSpecifiedIsResolvedByCode() {
            StudentFinance f = paid();
            List<StudyFinReceivedStatus> renumbered = List.of(finStatus(1, "RECEIVED"), finStatus(77, "NOT_SPECIFIED"));

            QuarterRolloverRules.of(quarter(16, 2025, 4), statuses(), levels(), renumbered).advance(f);

            assertThat(f.getStudyFinRecievedStatus()).isEqualTo(77);
        }

        @Test
        @DisplayName("a lookup with no NOT_SPECIFIED row falls back to the id V072 seeded, never to 0")
        void missingNotSpecifiedFallsBackToTheSeededId() {
            StudentFinance f = paid();

            QuarterRolloverRules.of(quarter(16, 2025, 4), statuses(), levels(), List.of(finStatus(1, "RECEIVED")))
                    .advance(f);

            assertThat(f.getStudyFinRecievedStatus()).isEqualTo(StudyFinReceivedStatusService.NOT_SPECIFIED_ID_FALLBACK);
        }

        @Test
        @DisplayName("last quarter's wafar does not follow the row forward")
        void wafarIsCleared() {
            StudentFinance f = paid();

            rules(2025, 4).advance(f);

            // NULL, not 0, is this pair's empty: 918 of the 941 live rows hold it.
            assertThat(f.getWafarAmount()).isNull();
            assertThat(f.getWafarTypeId()).isNull();
        }

        @Test
        @DisplayName("the cheque that paid last quarter is not the cheque that pays this one")
        void checkInformationIsCleared() {
            StudentFinance f = paid();

            rules(2025, 4).advance(f);

            assertThat(f.getStudyCheckId()).isNull();
            assertThat(f.getStudyCheckDate()).isNull();
            assertThat(f.getStudyCheckDetails()).isNull();
            assertThat(f.getStudyCheckFormNumber()).isNull();
        }

        @Test
        @DisplayName("the entitlement itself is not cleared: it is what the new quarter is to pay")
        void theAmountSurvives() {
            StudentFinance f = paid();

            rules(2025, 4).advance(f);

            assertThat(f.getStudentFinRecievedAmount()).isEqualTo(1500);
        }
    }
}
