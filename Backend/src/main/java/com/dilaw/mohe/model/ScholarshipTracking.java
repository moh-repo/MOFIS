package com.dilaw.mohe.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Immutable;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Scholarship tracking, reported from what the record actually holds —
 * {@code MOHECORE.V_Scholarship_Tracking} (V078; the view is in source control).
 *
 * <p>{@code scholarship_amount} is {@code STUDENT_DATA.STUDENT_AMOUNT}, the
 * student's recorded allowance, in the currency on the same record;
 * {@code amount_paid} sums the finance rows whose status is RECEIVED;
 * {@code end_date} is the start plus the recorded study length and is null when
 * that length is not recorded. There is no remaining balance: the allowance is a
 * periodic rate, not a total award, and this system records no total to subtract
 * from. There is no payment schedule: nothing records one. Both columns used to
 * be printed as empty placeholders and are gone (owner decision O1, 30 August 2026).
 */
@Getter
@Setter
@Entity
@Immutable
@Table(name = "V_Scholarship_Tracking", schema = "MOHECORE")
public class ScholarshipTracking {

    @Id
    @Column(name = "scholarship_id")
    private Long scholarshipId;

    @Column(name = "student_id")
    private Integer studentId;

    @Column(name = "student_name")
    private String studentName;

    @Column(name = "student_passport_number")
    private String studentPassportNumber;

    @Column(name = "scholarship_type")
    private String scholarshipType;
    
    /** The same name in English; resolved by BilingualRows for an EN render. */
    @Column(name = "scholarship_type_en")
    private String scholarshipTypeEn;

    @Column(name = "scholarship_provider")
    private String scholarshipProvider;
    
    /** The same name in English; resolved by BilingualRows for an EN render. */
    @Column(name = "scholarship_provider_en")
    private String scholarshipProviderEn;

    @Column(name = "scholarship_amount")
    private BigDecimal scholarshipAmount;

    @Column(name = "scholarship_currency")
    private String scholarshipCurrency;

    @Column(name = "allocated_date")
    private LocalDate allocatedDate;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "amount_paid")
    private BigDecimal amountPaid;

    @Column(name = "scholarship_status")
    private String scholarshipStatus;
    
    /** The same name in English; resolved by BilingualRows for an EN render. */
    @Column(name = "scholarship_status_en")
    private String scholarshipStatusEn;

    /** STUDENT_STUDY_STATUS.CODE — the key the screen colours its tag by. */
    @Column(name = "scholarship_status_code")
    private String scholarshipStatusCode;

    @Column(name = "university_name")
    private String universityName;
    
    /** The same name in English; resolved by BilingualRows for an EN render. */
    @Column(name = "university_name_en")
    private String universityNameEn;

    @Column(name = "major_name")
    private String majorName;
    
    /** The same name in English; resolved by BilingualRows for an EN render. */
    @Column(name = "major_name_en")
    private String majorNameEn;
}
