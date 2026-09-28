package com.dilaw.mohe.util;

import com.dilaw.mohe.model.StudentFinance;
import com.dilaw.mohe.model.StudentFinanceArch;
import com.dilaw.mohe.model.StudentFinancePayment;

public final class StudentFinanceLedgerMapper {

    private StudentFinanceLedgerMapper() {
    }

    public static StudentFinancePayment paymentFromFinance(StudentFinance f, Integer moheQuarterId) {
        if (f == null) {
            return null;
        }
        StudentFinancePayment p = new StudentFinancePayment();
        p.setStudentId(f.getId());
        p.setMoheQuarterId(moheQuarterId);
        p.setStudentFinId(f.getStudentFinId());
        p.setStudentFinName(f.getStudentFinName());
        p.setStudentFinRecievedYear(f.getStudentFinRecievedYear());
        p.setStudentFinRecievedMonth(f.getStudentFinRecievedMonth());
        p.setStudentFinRecievedYearMonth(f.getStudentFinRecievedYearMonth());
        p.setStudentFinRecievedAmount(f.getStudentFinRecievedAmount());
        p.setStudyCountryInId(f.getStudyCountryInId());
        p.setStudyGovInId(f.getStudyGovInId());
        p.setStudyFinanceBankeId(f.getStudyFinanceBankeId());
        p.setStudyFinanceBankBranchId(f.getStudyFinanceBankBranchId());
        p.setStudyFinanceDofId(f.getStudyFinanceDofId());
        p.setStudyFinRecievedStatus(f.getStudyFinRecievedStatus());
        p.setStudyReceivedWhomStatusId(f.getStudyReceivedWhomStatusId());
        p.setStudyReceivedWhomId(f.getStudyReceivedWhomId());
        p.setPaymentNotes(f.getStudyNote1());
        return p;
    }

    public static StudentFinanceArch archFromFinance(StudentFinance f, Integer moheQuarterId) {
        return copyFinanceToArch(f, moheQuarterId, new StudentFinanceArch());
    }

    /**
     * Copy a live finance row onto {@code a}, which may be a fresh entity or the archive
     * row that already exists for the same (student, year, quarter). Copying onto the
     * existing row keeps its FIN_ARCH_ENTRY_ID, so the snapshot is refreshed in place
     * instead of duplicated.
     *
     * <p>{@code moheQuarterId} only overwrites the stamp when it is non-null: the live
     * row carries a null MOHE_QUARTER_ID on every row today, and a refresh from quarter
     * create must not erase the stamp a quarter close put there.
     */
    public static StudentFinanceArch copyFinanceToArch(StudentFinance f, Integer moheQuarterId, StudentFinanceArch a) {
        if (f == null) {
            return a;
        }
        a.setStudentId(f.getId());
        a.setStudentFinId(f.getStudentFinId());
        a.setStudentFinName(f.getStudentFinName());
        a.setStudentFinRecievedYear(f.getStudentFinRecievedYear());
        a.setStudentFinRecievedMonth(f.getStudentFinRecievedMonth());
        a.setStudentFinRecievedYearMonth(f.getStudentFinRecievedYearMonth());
        a.setStudentFinRecievedAmount(f.getStudentFinRecievedAmount());
        a.setStudyCountryInId(f.getStudyCountryInId());
        a.setStudyGovInId(f.getStudyGovInId());
        a.setStudyFinanceBankeId(f.getStudyFinanceBankeId());
        a.setStudyFinanceBankBranchId(f.getStudyFinanceBankBranchId());
        a.setStudyFinanceDofId(f.getStudyFinanceDofId());
        a.setStudyCheckId(f.getStudyCheckId());
        a.setStudyCheckDate(f.getStudyCheckDate());
        a.setStudyCheckDetails(f.getStudyCheckDetails());
        a.setStudyCheckFormNumber(f.getStudyCheckFormNumber());
        a.setStudyFinRecievedStatus(f.getStudyFinRecievedStatus());
        a.setStudyNote1(f.getStudyNote1());
        a.setStudyReceivedWhomStatusId(f.getStudyReceivedWhomStatusId());
        a.setStudyReceivedWhomId(f.getStudyReceivedWhomId());
        a.setStudyFinanceMidate(f.getStudyFinanceMidate());
        a.setWafarAmount(f.getWafarAmount());
        a.setWafarTypeId(f.getWafarTypeId());
        a.setStudentBankId(f.getStudentBankId());
        a.setStudentAccountNumber(f.getStudentAccountNumber());
        a.setStudentAccountEnglishName(f.getStudentAccountEnglishName());
        a.setStudentAccountEndDate(f.getStudentAccountEndDate());
        a.setStudentBankNotes(f.getStudentBankNotes());
        if (moheQuarterId != null) {
            a.setMoheQuarterId(moheQuarterId);
        }
        return a;
    }
}
