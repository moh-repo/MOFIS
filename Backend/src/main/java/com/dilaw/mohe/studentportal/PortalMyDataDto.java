package com.dilaw.mohe.studentportal;

import com.dilaw.mohe.codes.FinReceivedStatusCode;
import com.dilaw.mohe.model.StudentDatum;
import com.dilaw.mohe.model.StudentFinance;
import com.dilaw.mohe.model.StudyFinReceivedStatus;
import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.BeanUtils;

import java.time.Instant;

/**
 * What {@code GET /student-portal/me/data} returns: the fields the portal's
 * "my data" page binds, and nothing else.
 *
 * <p>The endpoint used to serialise the whole {@code StudentDatum} and
 * {@code StudentFinance} entities — the photo path on disk, the soft-delete flag,
 * the record-visibility and creator-role columns, the bank account fields — to a
 * browser that reads about forty of them. Property names are the entity's, so the
 * page's bindings are unchanged; the lookup names it shows are still resolved
 * client-side from {@code /lookups}.
 */
@Getter
@Setter
public class PortalMyDataDto {

    private Student student;
    private Finance finance;

    public static PortalMyDataDto of(StudentDatum student, StudentFinance finance) {
        PortalMyDataDto dto = new PortalMyDataDto();
        dto.student = Student.from(student);
        dto.finance = Finance.from(finance);
        return dto;
    }

    /** Copied by name from {@link StudentDatum}; a field is listed here only if the page reads it. */
    @Getter
    @Setter
    public static class Student {
        private Integer studentId;
        private String studentName;
        private Integer studentGender;
        private Instant studentBirthdate;
        private String studentBirthdatePlace;
        private String studentPassportNumber;
        private Instant studentPassportIssueDate;
        private Instant studentPassportExpireDate;
        private Integer studentPassportIssueCountryId;
        /**
         * The contact fields a student may ask to change (live QA L14, P21): the change
         * dialog shows each field's current value from this answer, and these four were
         * left out, so the dialog showed them empty although the record held them.
         */
        private String studentEmail;
        private String studentCellphone;
        private String studentHomeNumber;
        private String studentEmergencyContact;
        private Integer studentCountryId;
        private Integer studentGovId;
        private Integer studentMudId;
        private Integer studentDegreeId;
        private Integer studentMajorId;
        /**
         * The major's names from STUDENT_MAJOR when the id resolves — the record's own
         * free-text Arabic otherwise, and then no English (QA SP-004, live M7).
         */
        private String studentMajorName;
        private String studentMajorNameEn;
        private Integer studentDelegateDecisionNo;
        private Integer studentStudyNoOfYears;
        private Integer studentCurrentLevelId;
        private Integer studentStudyTypeId;
        private Integer studentStudyStatusId;
        private Integer studentAmount;
        private Integer amountCurrency;
        private Integer studentFirstReceiptOfDue;
        private String studentDelegationDecisionYear;
        private Integer studentAddedQuarter;
        private Integer studentLastQuarter;
        private String currentQuarter;
        private Integer studyUniversityFeeId;
        private Integer studyUniversityFeeStatusId;
        /**
         * Names for the two ids My Data printed as numbers: the allowance's currency
         * and the university-fee status (QA SP-004). Both languages; the page picks.
         */
        private String amountCurrencyName;
        private String amountCurrencyNameEn;
        private String studyUniversityFeeStatusName;
        private String studyUniversityFeeStatusNameEn;
        private Instant studentMidate;
        private Integer studentAgencyTypeId;
        private Integer studentMinistryTypeId;
        private Integer studentUniversityTypeId;
        private Integer studyCountryIn;
        private Integer syudyGovIn;
        private Integer studyUniversityIn;
        private Integer studyCollegeIn;
        private Integer studyEducationIn;
        private Integer studyMajorIn;
        private String studentNote1;
        private String studentNote2;

        static Student from(StudentDatum entity) {
            if (entity == null) {
                return null;
            }
            Student s = new Student();
            BeanUtils.copyProperties(entity, s);
            return s;
        }
    }

    /** Copied by name from {@link StudentFinance}; the bank-account and cheque detail columns are left out. */
    @Getter
    @Setter
    public static class Finance {
        private Integer id;
        private String studentFinId;
        private String studentFinName;
        private Integer studentFinRecievedAmount;
        private Integer studentFinRecievedYear;
        private Integer studentFinRecievedMonth;
        private Integer studentFinRecievedYearMonth;
        private Instant studyCheckDate;
        private Integer studyFinRecievedStatus;
        private Integer studyCountryInId;
        private Integer studyGovInId;
        private Integer studyCurrencyInId;
        private Integer moheQuarterId;
        /**
         * The status resolved on the server. The page used to call any non-zero id
         * "Received", so "Not received" (2) and every other status showed as received
         * (live QA #10). {@code received} is true for RECEIVED and DISBURSED only.
         */
        private String studyFinRecievedStatusCode;
        private String studyFinRecievedStatusName;
        private String studyFinRecievedStatusNameEn;
        private Boolean received;

        static Finance from(StudentFinance entity) {
            if (entity == null) {
                return null;
            }
            Finance f = new Finance();
            BeanUtils.copyProperties(entity, f);
            return f;
        }

        /** Names the status row this finance record points at; null when it points at none. */
        public void describeStatus(StudyFinReceivedStatus status) {
            String code = status == null ? null : status.getCode();
            studyFinRecievedStatusCode = code;
            studyFinRecievedStatusName = status == null ? null : status.getStudyFinReceivedStatusName();
            studyFinRecievedStatusNameEn = status == null ? null : status.getStudyFinReceivedStatusNameEn();
            received = FinReceivedStatusCode.RECEIVED.equals(code) || FinReceivedStatusCode.DISBURSED.equals(code);
        }
    }
}
