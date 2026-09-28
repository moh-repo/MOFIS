package com.dilaw.mohe.dto;

import com.dilaw.mohe.config.MaskedAs;
import com.dilaw.mohe.model.StudentDatum;
import lombok.Getter;
import lombok.Setter;

/**
 * Language-resolved flat bean consumed by the single-student Jasper documents
 * (ID card, registration form, enrollment certificate). One name per lookup:
 * the service picks the AR or EN column, so templates stay language-agnostic.
 * Dates are pre-formatted strings so templates need no pattern logic.
 *
 * <p>The properties are renamed and reformatted copies of {@code StudentDatum}
 * columns, so the ones it protects are marked {@link MaskedAs} to be masked by
 * the same rule when these documents are printed (plan W5-03). Printing is not
 * a way around a field the screen would have hidden.
 */
@Getter
@Setter
public class StudentProfileDocDto {

    private Integer studentId;
    private String studentName;

    // Personal
    private String genderName;
    @MaskedAs(type = StudentDatum.class, field = "studentBirthdate")
    private String birthDate;
    @MaskedAs(type = StudentDatum.class, field = "studentBirthdatePlace")
    private String birthPlace;
    private String age;

    // Home
    private String homeCountryName;
    private String homeGovName;
    private String homeMudName;

    // Passport
    @MaskedAs(type = StudentDatum.class, field = "studentPassportNumber")
    private String passportNumber;
    @MaskedAs(type = StudentDatum.class, field = "studentPassportIssueDate")
    private String passportIssueDate;
    @MaskedAs(type = StudentDatum.class, field = "studentPassportExpireDate")
    private String passportExpireDate;
    private String passportCountryName;

    // Academic
    private String degreeName;
    private String majorName;
    private String levelName;
    private String studyTypeName;
    private String studyStatusName;
    private String universityTypeName;
    private String agencyTypeName;
    private String ministryTypeName;
    private Integer studyNoOfYears;

    // Delegation
    private Integer delegateDecisionNo;
    private String delegationDecisionYear;

    // Study destination
    private String studyCountryName;
    private String studyGovName;
    private String studyUniversityName;
    private String studyCollegeName;
    private String studyEducationName;
    private String studyMajorInName;

    // Finance summary
    @MaskedAs(type = StudentDatum.class, field = "studentAmount")
    private Integer amount;
    private String currencyName;

    // Contacts
    @MaskedAs(type = StudentDatum.class, field = "studentEmail")
    private String email;
    @MaskedAs(type = StudentDatum.class, field = "studentCellphone")
    private String cellphone;
    @MaskedAs(type = StudentDatum.class, field = "studentHomeNumber")
    private String homeNumber;
    @MaskedAs(type = StudentDatum.class, field = "studentEmergencyContact")
    private String emergencyContact;

    // Quarters
    private String addedQuarterLabel;
    private String lastQuarterLabel;
    private String currentQuarterLabel;
}
