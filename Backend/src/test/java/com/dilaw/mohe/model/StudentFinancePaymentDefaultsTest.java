package com.dilaw.mohe.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A payment row built from a finance row that has no status, bank or received-by
 * yet (every wizard-created student, and 940 of 941 finance rows on dev) must not
 * reach the INSERT with NULLs: the columns are NOT NULL, and their table default
 * of 0 never fires for a column Hibernate names. The database side of this is
 * {@code com.dilaw.mohe.approval.WizardStudentApprovalExecutesTest}.
 */
class StudentFinancePaymentDefaultsTest {

    @Test
    @DisplayName("NOT NULL columns left empty take the table's own defaults before insert")
    void emptyColumnsTakeTheTableDefaults() {
        StudentFinancePayment p = new StudentFinancePayment();
        p.setStudentId(1);
        p.setStudyCountryInId(1);
        p.setInsertedAt(null);

        p.applyColumnDefaults();

        assertThat(p.getStudentFinName()).isEmpty();
        assertThat(p.getStudyFinRecievedStatus()).isZero();
        assertThat(p.getStudyFinanceBankeId()).isZero();
        assertThat(p.getStudyFinanceBankBranchId()).isZero();
        assertThat(p.getStudyFinanceDofId()).isZero();
        assertThat(p.getStudyReceivedWhomStatusId()).isZero();
        assertThat(p.getStudyReceivedWhomId()).isZero();
        assertThat(p.getStudyGovInId()).isZero();
        assertThat(p.getStudentFinRecievedYear()).isZero();
        assertThat(p.getStudentFinRecievedMonth()).isZero();
        assertThat(p.getStudentFinRecievedYearMonth()).isZero();
        assertThat(p.getStudentFinRecievedAmount()).isZero();
        assertThat(p.getInsertedAt()).isNotNull();
    }

    @Test
    @DisplayName("values that are set are kept, and the country is never invented")
    void setValuesAreKept() {
        StudentFinancePayment p = new StudentFinancePayment();
        p.setStudentFinName("x");
        p.setStudyFinRecievedStatus(2);
        p.setStudyFinanceBankeId(7);

        p.applyColumnDefaults();

        assertThat(p.getStudentFinName()).isEqualTo("x");
        assertThat(p.getStudyFinRecievedStatus()).isEqualTo(2);
        assertThat(p.getStudyFinanceBankeId()).isEqualTo(7);
        assertThat(p.getStudyCountryInId())
                .as("STUDY_COUNTRY_IN_ID has a foreign key; 0 would only move the error")
                .isNull();
    }
}
