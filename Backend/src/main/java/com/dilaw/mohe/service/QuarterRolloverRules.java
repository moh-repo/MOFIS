package com.dilaw.mohe.service;

import com.dilaw.mohe.codes.FinReceivedStatusCode;
import com.dilaw.mohe.codes.StudyStatusCode;
import com.dilaw.mohe.model.MoheQuarter;
import com.dilaw.mohe.model.StudentCurrentLevel;
import com.dilaw.mohe.model.StudentDatum;
import com.dilaw.mohe.model.StudentFinance;
import com.dilaw.mohe.model.StudentStudyStatus;
import com.dilaw.mohe.model.StudyFinReceivedStatus;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * What a student and their finance row become when a new quarter is added.
 *
 * <p>Built once per rollover — the two lookup tables behind it are read once, not once
 * per student — and then applied to each row in turn. Deliberately a plain object with
 * no Spring or repository of its own: these are the rules of the ministry's period
 * roll, and they are worth being able to read and to test without a database.
 *
 * <p>The archive is written before any of this runs, so everything cleared here is
 * still readable on the snapshot of the quarter being left.
 */
public final class QuarterRolloverRules {

    /** The quarter being moved to, as the {@code YYYYQ} text STUDENT_DATA holds. */
    private final String newQuarterCode;
    /**
     * The same period as a number. This, not {@code MOHE_QUARTER.ID}, is what points at
     * a quarter: {@code pk_MOHE_QUARTER} is on {@code QUARTER}, and five foreign keys
     * across the schema reference it — {@code STUDENT_FINANCE.MOHE_QUARTER_ID} among
     * them, despite its name.
     */
    private final Integer newQuarterKey;
    private final Integer quarterYear;
    private final Integer quarterNumber;

    /** Ids behind {@code StudyStatusCode.NEW} / {@code .ACTIVE}; null when the row is absent. */
    private final Integer newStatusId;
    private final Integer activeStatusId;

    /**
     * The id behind {@code FinReceivedStatusCode.NOT_SPECIFIED}: what a finance row
     * carries once its quarter's payment is cleared. It used to be written as 0, the
     * column's old default, which is what put every rolled row outside the form's own
     * rules (live QA H2, 23 Sep 2026); V135 moved the rows and the default onto
     * NOT_SPECIFIED. The V072 id stands in when the lookup has no such row.
     */
    private final int notSpecifiedFinStatusId;

    private final Map<Integer, StudentCurrentLevel> levelsById;
    private final List<StudentCurrentLevel> levels;

    private QuarterRolloverRules(MoheQuarter target,
                                 Integer newStatusId,
                                 Integer activeStatusId,
                                 Integer notSpecifiedFinStatusId,
                                 List<StudentCurrentLevel> levels) {
        this.quarterYear = target.getQuarterYear();
        this.quarterNumber = target.getQuarterNumber();
        this.newQuarterKey = quarterYear == null || quarterNumber == null
                ? null
                : QuarterFinanceTotalsService.quarterKey(quarterYear, quarterNumber);
        this.newQuarterCode = newQuarterKey == null ? null : String.valueOf(newQuarterKey);
        this.newStatusId = newStatusId;
        this.activeStatusId = activeStatusId;
        this.notSpecifiedFinStatusId = notSpecifiedFinStatusId == null
                ? StudyFinReceivedStatusService.NOT_SPECIFIED_ID_FALLBACK
                : notSpecifiedFinStatusId;
        this.levels = levels == null ? List.of() : levels;
        this.levelsById = this.levels.stream()
                .filter(l -> l.getId() != null)
                .collect(Collectors.toMap(StudentCurrentLevel::getId, Function.identity(), (a, b) -> a));
    }

    /**
     * @param statuses    every STUDENT_STUDY_STATUS row; NEW and ACTIVE are resolved by
     *                    {@code CODE} rather than by the ids they happen to carry (2 and 1),
     *                    which is what V028 added the column for.
     * @param levels      every STUDENT_CURRENT_LEVEL row, for the year-to-year ladder.
     * @param finStatuses every STUDY_FIN_RECEIVED_STATUS row; NOT_SPECIFIED is resolved
     *                    by {@code CODE} the same way.
     */
    public static QuarterRolloverRules of(MoheQuarter target,
                                          List<StudentStudyStatus> statuses,
                                          List<StudentCurrentLevel> levels,
                                          List<StudyFinReceivedStatus> finStatuses) {
        return new QuarterRolloverRules(target, idOf(statuses, StudyStatusCode.NEW),
                idOf(statuses, StudyStatusCode.ACTIVE), finStatusIdOf(finStatuses, FinReceivedStatusCode.NOT_SPECIFIED),
                levels);
    }

    private static Integer finStatusIdOf(List<StudyFinReceivedStatus> statuses, String code) {
        if (statuses == null) {
            return null;
        }
        return statuses.stream()
                .filter(s -> s.getCode() != null && code.equals(s.getCode().trim()))
                .map(StudyFinReceivedStatus::getId)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    private static Integer idOf(List<StudentStudyStatus> statuses, String code) {
        if (statuses == null) {
            return null;
        }
        return statuses.stream()
                .filter(s -> s.getCode() != null && s.getCode().equalsIgnoreCase(code))
                .map(StudentStudyStatus::getId)
                .findFirst()
                .orElse(null);
    }

    /** True on the third quarter, the one the academic year turns on. */
    public boolean advancesLevel() {
        return quarterNumber != null && quarterNumber == 3;
    }

    /**
     * Moves one student into the new quarter: their period pointer, and — where the
     * rules below apply — their status and their year of study.
     */
    public void advance(StudentDatum student) {
        if (student == null) {
            return;
        }
        if (newQuarterCode != null) {
            student.setCurrentQuarter(newQuarterCode);
        }
        // A student entered for the first time is no longer new once a quarter has
        // passed over them. Every other status is somebody's decision and is left.
        if (newStatusId != null && activeStatusId != null
                && newStatusId.equals(student.getStudentStudyStatusId())) {
            student.setStudentStudyStatusId(activeStatusId);
        }
        if (advancesLevel()) {
            Integer next = nextLevelId(student.getStudentCurrentLevelId(), student.getStudentStudyNoOfYears());
            if (next != null) {
                student.setStudentCurrentLevelId(next);
            }
        }
    }

    /**
     * The level one study year on, or null when there is no move to make.
     *
     * <p>Not {@code id + 1}: STUDENT_CURRENT_LEVEL holds two families of rows (1–8 the
     * study years, 9 and 10 the two extensions, 99 Not Specified; then a second set from
     * 100), so adding one to an eighth-year student lands on "Regular Extension" and
     * adding one to 99 lands in the other family. The move is by
     * {@code STUDENT_CURRENT_LEVEL_ORDER_NO} within the row's own family.
     *
     * <p>It stops at the length of the student's own programme. Without that a
     * final-year student would be pushed into an extension by the arithmetic alone, and
     * an unknown programme length is not a licence to guess — a student whose years are
     * not recorded stays where they are, as does one with no level at all.
     */
    Integer nextLevelId(Integer currentLevelId, Integer studyNoOfYears) {
        if (currentLevelId == null || studyNoOfYears == null || studyNoOfYears <= 0) {
            return null;
        }
        StudentCurrentLevel current = levelsById.get(currentLevelId);
        if (current == null || current.getStudentCurrentLevelOrderNo() == null) {
            return null;
        }
        int order = current.getStudentCurrentLevelOrderNo();
        if (order <= 0 || order >= studyNoOfYears) {
            // Already in the final year of the programme — or in an extension or
            // "Not Specified", whose order numbers sit far above any programme length.
            return null;
        }
        return levels.stream()
                .filter(l -> l.getId() != null && !Boolean.FALSE.equals(l.getIsActive()))
                .filter(l -> Objects.equals(l.getStudentCurrentLevelStatus(), current.getStudentCurrentLevelStatus()))
                .filter(l -> l.getStudentCurrentLevelOrderNo() != null
                        && l.getStudentCurrentLevelOrderNo() == order + 1)
                .min(Comparator.comparing(StudentCurrentLevel::getId))
                .map(StudentCurrentLevel::getId)
                .orElse(null);
    }

    /**
     * Moves one finance row into the new quarter and clears what belonged to the last
     * one: who collected the money, whether it was collected, the wafar it left behind
     * and the cheque it was paid by. None of that is true of a quarter nobody has been
     * paid for yet, and carrying it forward makes the new period read as already
     * settled.
     *
     * <p>The two kinds of empty are the table's own. The recipient and received-status
     * columns are {@code 0} on all but one of the 941 live rows — their column default —
     * while wafar and cheque are NULL. A row cleared this way is indistinguishable from
     * a freshly created one.
     *
     * <p>{@code NET_AMOUNT} is left alone: the database computes it as
     * {@code isnull(received,0) - isnull(wafar,0)}. The STUDENT_FINANCE_WAFAR row is
     * left too — it is the previous quarter's record, keyed by that quarter.
     */
    public void advance(StudentFinance finance) {
        if (finance == null) {
            return;
        }
        // The period key, not the quarter's row id. STUDENT_FINANCE.MOHE_QUARTER_ID is
        // FK_STUDENT_FINANCE_QUARTER against MOHE_QUARTER.QUARTER — the table's actual
        // primary key — so a row id here is refused by the database. The column is NULL
        // on all 941 live rows, which is what a roll that never landed leaves behind.
        finance.setMoheQuarterId(newQuarterKey);
        finance.setStudentFinRecievedYear(quarterYear);
        // The column is named MONTH and holds the quarter number 1-4; YEAR_MONTH is the
        // YYYYQ period key (20242 = 2024 Q2), not a calendar month. Every live row,
        // archive row and Wafar row carries that form, V038 verifies
        // YEAR_MONTH = YEAR*10 + MONTH, and ATTACHE_ALLOCATION joins on it.
        finance.setStudentFinRecievedMonth(quarterNumber);
        if (quarterYear != null && quarterNumber != null) {
            finance.setStudentFinRecievedYearMonth(
                    QuarterFinanceTotalsService.quarterKey(quarterYear, quarterNumber));
        }

        finance.setStudyReceivedWhomId(0);
        finance.setStudyReceivedWhomStatusId(0);
        // NOT_SPECIFIED by CODE, not 0: 0 is no status at all and the form refuses to
        // save a row that carries it (live QA H2).
        finance.setStudyFinRecievedStatus(notSpecifiedFinStatusId);

        finance.setWafarAmount(null);
        finance.setWafarTypeId(null);

        finance.setStudyCheckId(null);
        finance.setStudyCheckDate(null);
        finance.setStudyCheckDetails(null);
        finance.setStudyCheckFormNumber(null);
    }
}
