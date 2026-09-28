package com.dilaw.mohe.util;

import com.dilaw.mohe.model.StudentDataArch;
import com.dilaw.mohe.model.StudentDatum;

import java.time.Instant;

/** Snapshot live student rows into STUDENT_DATA_ARCH for quarter-create / quarter-close. */
public final class QuarterArchiveMapper {

    private QuarterArchiveMapper() {
    }

    public static StudentDataArch datumToArch(StudentDatum src, Integer moheQuarterId) {
        return datumToArch(src, moheQuarterId, null);
    }

    /**
     * @param profileSnapshot full-profile JSON captured at archive time, or null when not available.
     */
    public static StudentDataArch datumToArch(StudentDatum src, Integer moheQuarterId, String profileSnapshot) {
        return copyDatumToArch(src, moheQuarterId, profileSnapshot, new StudentDataArch());
    }

    /**
     * Copies the snapshot into an archive row that may already exist, keeping its id.
     *
     * <p>Used by {@code StudentDataArchiveService} so a second archive of the same
     * student and period refreshes the row rather than adding a second copy of it —
     * the same upsert {@code StudentFinanceLedgerMapper.copyFinanceToArch} exists for.
     */
    public static StudentDataArch copyDatumToArch(StudentDatum src, Integer moheQuarterId,
                                                  String profileSnapshot, StudentDataArch a) {
        if (src == null) {
            return a;
        }
        a.setStudentId(src.getStudentId());
        a.setStudentName(src.getStudentName());
        a.setStudentCountryId(src.getStudentCountryId());
        a.setStudentGovId(src.getStudentGovId());
        a.setStudentMudId(src.getStudentMudId());
        a.setStudentDelegationDecisionYear(src.getStudentDelegationDecisionYear());
        a.setStudentDegreeId(src.getStudentDegreeId());
        a.setStudentMajorId(src.getStudentMajorId());
        a.setStudentMajorName(src.getStudentMajorName());
        a.setStudentDelegateDecisionNo(src.getStudentDelegateDecisionNo());
        a.setStudentStudyNoOfYears(src.getStudentStudyNoOfYears());
        a.setStudentCurrentLevelId(src.getStudentCurrentLevelId());
        a.setStudentFirstReceiptOfDue(src.getStudentFirstReceiptOfDue());
        a.setStudentAmount(src.getStudentAmount());
        a.setAmountCurrency(src.getAmountCurrency());
        a.setStudentNote1(src.getStudentNote1());
        a.setStudentNote2(src.getStudentNote2());
        a.setStudentStudyStatusId(src.getStudentStudyStatusId());
        a.setStudentAgencyTypeId(src.getStudentAgencyTypeId());
        a.setStudentMinistryTypeId(src.getStudentMinistryTypeId());
        a.setStudentStudyTypeId(src.getStudentStudyTypeId());
        a.setStudentUniversityTypeId(src.getStudentUniversityTypeId());
        a.setStudentAddedQuarter(src.getStudentAddedQuarter());
        a.setStudentLastQuarter(src.getStudentLastQuarter());
        a.setCurrentQuarter(src.getCurrentQuarter());
        a.setStudentAge(src.getStudentAge());
        a.setStudyUniversityIn(src.getStudyUniversityIn());
        a.setStudyCollegeIn(src.getStudyCollegeIn());
        a.setStudyCountryIn(src.getStudyCountryIn());
        a.setSyudyGovIn(src.getSyudyGovIn());
        a.setStudyEducationIn(src.getStudyEducationIn());
        a.setStudyMajorIn(src.getStudyMajorIn());
        a.setStudentBirthdate(src.getStudentBirthdate());
        a.setStudentBirthdatePlace(src.getStudentBirthdatePlace());
        a.setStudentGender(src.getStudentGender());
        a.setStudentPassportNumber(src.getStudentPassportNumber());
        a.setStudentPassportIssueDate(src.getStudentPassportIssueDate());
        a.setStudentPassportExpireDate(src.getStudentPassportExpireDate());
        a.setStudentPassportIssueCountryId(src.getStudentPassportIssueCountryId());
        a.setStudyUniversityFeeId(src.getStudyUniversityFeeId());
        a.setStudyUniversityFeeStatusId(src.getStudyUniversityFeeStatusId());
        a.setUniversityFeeStatusCurrency(src.getUniversityFeeStatusCurrency());
        a.setPicturePath(src.getPicturePath());
        a.setStudentEmail(src.getStudentEmail());
        a.setStudentCellphone(src.getStudentCellphone());
        a.setStudentHomeNumber(src.getStudentHomeNumber());
        a.setStudentEmergencyContact(src.getStudentEmergencyContact());
        a.setStudentMidate(src.getStudentMidate());
        a.setIsDeleted(src.getIsDeleted());
        a.setDeletedDate(src.getDeletedDate());
        a.setRecordVisibility(src.getRecordVisibility());
        a.setCreatedRoleLevel(src.getCreatedRoleLevel());
        Instant now = Instant.now();
        a.setArchiveDate(now);
        a.setInsertedToArchDate(now);
        a.setProfileSnapshot(profileSnapshot);
        if (moheQuarterId != null) {
            a.setQuerterIs(moheQuarterId);
        }
        return a;
    }
}
