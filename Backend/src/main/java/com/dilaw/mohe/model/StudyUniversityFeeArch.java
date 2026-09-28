package com.dilaw.mohe.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.Nationalized;

import java.time.Instant;

/**
 * A row of the fee log (سجل الرسوم).
 *
 * <p>The key is STUDY_UNIVERSITY_FEE_ID, as in the table (review F8). This used to map
 * STUDENT_ID as the @Id, so a student's second row was the "same" entity as the first:
 * edit and delete reached the wrong row or failed as non-unique, and every insert
 * carried key 0. The key now comes from SEQ_STUDY_UNIVERSITY_FEE_ARCH (V114).
 */
@Getter
@Setter
@Entity
@Table(name = "STUDY_UNIVERSITY_FEE_ARCH", schema = "MOHECORE")
public class StudyUniversityFeeArch {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "studyUniversityFeeArchSeq")
    @SequenceGenerator(name = "studyUniversityFeeArchSeq", schema = "MOHECORE",
            sequenceName = "SEQ_STUDY_UNIVERSITY_FEE_ARCH", allocationSize = 1)
    @Column(name = "STUDY_UNIVERSITY_FEE_ID", nullable = false)
    private Integer studyUniversityFeeId;

    @ColumnDefault("0")
    @Column(name = "STUDENT_ID", nullable = false)
    private Integer studentId;

    @Nationalized
    @Column(name = "STUDY_UNIVERSITY_FEE_STUDENT_NAME")
    private String studyUniversityFeeStudentName;

    @Nationalized
    @Column(name = "STUDY_UNIVERSITY_FEE_FIRST_YEAR")
    private String studyUniversityFeeFirstYear;

    @ColumnDefault("0")
    @Column(name = "STUDY_UNIVERSITY_FEE_AMOUNT")
    private Integer studyUniversityFeeAmount;

    @Nationalized
    @Column(name = "STUDY_UNIVERSITY_FEE_YEAR")
    private String studyUniversityFeeYear;

    @ColumnDefault("0")
    @Column(name = "STUDY_UNIVERSITY_FEE_DISCOUNT")
    private Integer studyUniversityFeeDiscount;

    @ColumnDefault("0")
    @Column(name = "STUDY_UNIVERSITY_REAL_FEE_AMOUNT")
    private Integer studyUniversityRealFeeAmount;

    @ColumnDefault("0")
    @Column(name = "STUDY_UNIVERSITY_FEE_CURRENCY")
    private Integer studyUniversityFeeCurrency;

    @Column(name = "STUDY_UNIVERSITY_FEE_PAY_DATE")
    private Instant studyUniversityFeePayDate;

    @ColumnDefault("0")
    @Column(name = "STUDY_UNIVERSITY_FEE_PAY_STATUS")
    private Integer studyUniversityFeePayStatus;

    /** 0=unset, 1 = student's name, 2 = university directly */
    @ColumnDefault("0")
    @Column(name = "STUDY_UNIVERSITY_CHECK_PAYOR_TYPE_ID")
    private Integer studyUniversityCheckPayorTypeId;

    @Nationalized
    @Column(name = "STUDY_UNIVERSITY_CHECK_NO")
    private String studyUniversityCheckNo;

    @Column(name = "STUDY_UNIVERSITY_CHECK_DATE")
    private Instant studyUniversityCheckDate;

    @Nationalized
    @Lob
    @Column(name = "STUDY_UNIVERSITY_CHECK_NOTES")
    private String studyUniversityCheckNotes;

    @Nationalized
    @Column(name = "STUDY_UNIVERSITY_CHECK_FORM_NUMBER")
    private String studyUniversityCheckFormNumber;

    @ColumnDefault("getdate()")
    @Column(name = "STUDY_UNIVERSITY_FEE_MIDATE")
    private Instant studyUniversityFeeMidate;

    @ColumnDefault("getdate()")
    @Column(name = "STUDY_UNIVERSITY_FEE_ARCH_DATE")
    private Instant studyUniversityFeeArchDate;

    /**
     * The attaché this fee was paid at. Populated on all four existing rows, and
     * the reason this table can be scoped on its own column rather than through
     * the student — an archived fee should keep the country it was paid in even if
     * the student later transfers.
     */
    @Column(name = "STUDY_COUNTRY_IN_ID")
    private Integer studyCountryInId;
}
