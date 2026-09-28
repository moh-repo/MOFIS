package com.dilaw.mohe.util;

import com.dilaw.mohe.model.StudentDatum;
import com.dilaw.mohe.model.StudentFinance;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Live QA H7 (25 Sep 2026): the dashboard trend drew a year "20251". The student's
 * first receipt of due is a YYYYQ key, and the finance draft copied it into the
 * received year. It is now split into year and quarter, as the seed SQL does.
 */
class StudentFinanceQuarterKeyTest {

    private static StudentDatum student(Integer firstDue) {
        StudentDatum s = new StudentDatum();
        s.setStudentId(10116);
        s.setStudentName("QA");
        s.setStudentFirstReceiptOfDue(firstDue);
        s.setStudentDelegationDecisionYear("2024");
        return s;
    }

    @Test
    @DisplayName("a YYYYQ first receipt of due becomes year 2025, quarter 1")
    void quarterKeyIsSplit() {
        StudentFinance f = StudentFinanceDraftMapper.fromStudentDatum(student(20251));
        assertThat(f.getStudentFinRecievedYear()).isEqualTo(2025);
        assertThat(f.getStudentFinRecievedMonth()).isEqualTo(1);
        assertThat(f.getStudentFinRecievedYearMonth()).isEqualTo(20251);
    }

    @Test
    @DisplayName("a plain year stays the year; none falls back to the delegation year")
    void plainYearAndFallback() {
        assertThat(StudentFinanceDraftMapper.fromStudentDatum(student(2025)).getStudentFinRecievedYear()).isEqualTo(2025);
        assertThat(StudentFinanceDraftMapper.fromStudentDatum(student(null)).getStudentFinRecievedYear()).isEqualTo(2024);
    }

    @Test
    @DisplayName("a wizard draft that still carries the key as the year is split on merge")
    void draftKeyIsSplitOnMerge() {
        StudentFinance draft = new StudentFinance();
        draft.setStudentFinRecievedYear(20251);
        draft.setStudentFinRecievedMonth(0);
        StudentFinance merged = StudentFinanceDraftMapper.mergeDraft(
                StudentFinanceDraftMapper.fromStudentDatum(student(null)), draft);
        assertThat(merged.getStudentFinRecievedYear()).isEqualTo(2025);
        assertThat(merged.getStudentFinRecievedMonth()).isEqualTo(1);
    }

    @Test
    @DisplayName("only five digits ending in 1-4 are a quarter key")
    void whatIsAQuarterKey() {
        assertThat(StudentFinanceDraftMapper.isQuarterKey(20251)).isTrue();
        assertThat(StudentFinanceDraftMapper.isQuarterKey(20244)).isTrue();
        assertThat(StudentFinanceDraftMapper.isQuarterKey(20250)).isFalse();
        assertThat(StudentFinanceDraftMapper.isQuarterKey(2025)).isFalse();
    }
}
