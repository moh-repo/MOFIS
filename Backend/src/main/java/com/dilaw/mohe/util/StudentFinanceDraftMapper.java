package com.dilaw.mohe.util;

import com.dilaw.mohe.model.StudentDatum;
import com.dilaw.mohe.model.StudentFinance;

import java.time.Instant;

/**
 * Maps student onboarding fields onto an initial STUDENT_FINANCE draft (approval INSERT).
 */
public final class StudentFinanceDraftMapper {

    private StudentFinanceDraftMapper() {}

    /** Build baseline finance row from student; id must be set to student id before finance approval EXECUTE. */
    public static StudentFinance fromStudentDatum(StudentDatum s) {
        StudentFinance f = new StudentFinance();
        f.setStudentFinName(s.getStudentName());
        if (s.getStudyCountryIn() != null && s.getStudyCountryIn() > 0) {
            f.setStudyCountryInId(s.getStudyCountryIn());
        }
        if (s.getSyudyGovIn() != null && s.getSyudyGovIn() > 0) {
            f.setStudyGovInId(s.getSyudyGovIn());
        }
        f.setStudyFinanceMidate(Instant.now());
        f.setStudentFinRecievedAmount(s.getStudentAmount() != null ? s.getStudentAmount() : 0);
        Integer firstDue = s.getStudentFirstReceiptOfDue();
        f.setStudentFinRecievedMonth(0);
        f.setStudentFinRecievedYearMonth(0);
        if (firstDue != null && isQuarterKey(firstDue)) {
            applyQuarterKey(f, firstDue);
        } else {
            f.setStudentFinRecievedYear(firstDue != null && firstDue > 0 ? firstDue : guessYearFromDelegation(s.getStudentDelegationDecisionYear()));
        }
        return f;
    }

    /**
     * Whether a value is a YYYYQ quarter key (20251 = 2025 Q1), the form
     * STUDENT_FIRST_RECEIPT_OF_DUE is stored in on 940 of 941 live students.
     *
     * <p>Live QA H7 (25 Sep 2026): both this mapper and the wizard's finance draft
     * copied the key into the received <em>year</em>, so a new student's finance row
     * carried the year 20251 and the dashboard trend drew a year "20251".
     */
    public static boolean isQuarterKey(int v) {
        return v >= 10000 && v <= 99999 && v % 10 >= 1 && v % 10 <= 4;
    }

    /** Year, quarter and key from a YYYYQ key, as the seed SQL and the finance form split it. */
    private static void applyQuarterKey(StudentFinance f, int key) {
        f.setStudentFinRecievedYear(key / 10);
        f.setStudentFinRecievedMonth(key % 10);
        f.setStudentFinRecievedYearMonth(key);
    }

    public static StudentFinance mergeDraft(StudentFinance baseFromStudent, StudentFinance draftFromClient) {
        if (draftFromClient == null) {
            return baseFromStudent;
        }
        if (draftFromClient.getStudentFinId() != null) {
            baseFromStudent.setStudentFinId(draftFromClient.getStudentFinId());
        }
        if (draftFromClient.getStudentFinName() != null && !draftFromClient.getStudentFinName().isBlank()) {
            baseFromStudent.setStudentFinName(draftFromClient.getStudentFinName());
        }
        if (draftFromClient.getStudyCountryInId() != null && draftFromClient.getStudyCountryInId() > 0) {
            baseFromStudent.setStudyCountryInId(draftFromClient.getStudyCountryInId());
        }
        if (draftFromClient.getStudyGovInId() != null && draftFromClient.getStudyGovInId() > 0) {
            baseFromStudent.setStudyGovInId(draftFromClient.getStudyGovInId());
        }
        if (draftFromClient.getStudyFinanceBankeId() != null && draftFromClient.getStudyFinanceBankeId() > 0) {
            baseFromStudent.setStudyFinanceBankeId(draftFromClient.getStudyFinanceBankeId());
        }
        if (draftFromClient.getStudyFinanceBankBranchId() != null && draftFromClient.getStudyFinanceBankBranchId() > 0) {
            baseFromStudent.setStudyFinanceBankBranchId(draftFromClient.getStudyFinanceBankBranchId());
        }
        if (draftFromClient.getStudyFinanceDofId() != null && draftFromClient.getStudyFinanceDofId() > 0) {
            baseFromStudent.setStudyFinanceDofId(draftFromClient.getStudyFinanceDofId());
        }
        if (draftFromClient.getStudentFinRecievedAmount() != null && draftFromClient.getStudentFinRecievedAmount() > 0) {
            baseFromStudent.setStudentFinRecievedAmount(draftFromClient.getStudentFinRecievedAmount());
        }
        if (draftFromClient.getStudyCurrencyInId() != null && draftFromClient.getStudyCurrencyInId() > 0) {
            baseFromStudent.setStudyCurrencyInId(draftFromClient.getStudyCurrencyInId());
        }
        Integer draftYear = draftFromClient.getStudentFinRecievedYear();
        if (draftYear != null && isQuarterKey(draftYear)) {
            // A draft raised before the wizard was fixed carries the key as the year (H7).
            applyQuarterKey(baseFromStudent, draftYear);
        } else {
            if (draftYear != null && draftYear > 0) {
                baseFromStudent.setStudentFinRecievedYear(draftYear);
            }
            if (draftFromClient.getStudentFinRecievedMonth() != null) {
                baseFromStudent.setStudentFinRecievedMonth(draftFromClient.getStudentFinRecievedMonth());
            }
        }
        if (draftFromClient.getStudyFinRecievedStatus() != null && draftFromClient.getStudyFinRecievedStatus() > 0) {
            baseFromStudent.setStudyFinRecievedStatus(draftFromClient.getStudyFinRecievedStatus());
        }
        if (draftFromClient.getStudyReceivedWhomStatusId() != null && draftFromClient.getStudyReceivedWhomStatusId() > 0) {
            baseFromStudent.setStudyReceivedWhomStatusId(draftFromClient.getStudyReceivedWhomStatusId());
        }
        if (draftFromClient.getStudyReceivedWhomId() != null && draftFromClient.getStudyReceivedWhomId() > 0) {
            baseFromStudent.setStudyReceivedWhomId(draftFromClient.getStudyReceivedWhomId());
        }
        if (draftFromClient.getStudentAccountNumber() != null) {
            baseFromStudent.setStudentAccountNumber(draftFromClient.getStudentAccountNumber());
        }
        if (draftFromClient.getStudentBankId() != null && draftFromClient.getStudentBankId() > 0) {
            baseFromStudent.setStudentBankId(draftFromClient.getStudentBankId());
        }
        return baseFromStudent;
    }

    private static int guessYearFromDelegation(String y) {
        if (y == null || y.isBlank())
            return 0;
        String trimmed = y.trim();
        try {
            if (trimmed.length() >= 4) {
                return Integer.parseInt(trimmed.substring(0, 4));
            }
        } catch (NumberFormatException ignored) {
        }
        return 0;
    }
}
