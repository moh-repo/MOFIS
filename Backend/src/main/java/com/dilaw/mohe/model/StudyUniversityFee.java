package com.dilaw.mohe.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.Nationalized;

import java.time.Instant;

/**
 * Bean constraints (plan item 2.6): every column but the key is nullable with a
 * default of 0, so the table itself requires nothing. A fee row with no student
 * is still meaningless — the finance form, the Wafar link and the archive all
 * find it by student — so {@code studentId} is required and the amounts cannot
 * be negative.
 */
@Getter
@Setter
@Entity
@Table(name = "STUDY_UNIVERSITY_FEE", schema = "MOHECORE")
public class StudyUniversityFee {
    /*
     * Drawn from MOHECORE.SEQ_STUDY_UNIVERSITY_FEE (V128). The column is a plain INT, not
     * an identity: mapped as IDENTITY, every new fee failed with "null identifier" — an
     * approved "add fee" request included (W4-10).
     */
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "studyUniversityFeeSeq")
    @SequenceGenerator(name = "studyUniversityFeeSeq", schema = "MOHECORE",
            sequenceName = "SEQ_STUDY_UNIVERSITY_FEE", allocationSize = 1)
    @ColumnDefault("0")
    @Column(name = "STUDY_UNIVERSITY_FEE_ID", nullable = false)
    private Integer id;

    @NotNull(message = "studentId is required")
    @ColumnDefault("0")
    @Column(name = "STUDENT_ID")
    private Integer studentId;

    @Nationalized
    @Column(name = "STUDY_UNIVERSITY_FEE_STUDENT_NAME")
    private String studyUniversityFeeStudentName;

    @Nationalized
    @Column(name = "STUDY_UNIVERSITY_FEE_FIRST_YEAR")
    private String studyUniversityFeeFirstYear;

    @PositiveOrZero
    @ColumnDefault("0")
    @Column(name = "STUDY_UNIVERSITY_FEE_AMOUNT")
    private Integer studyUniversityFeeAmount;

    @Nationalized
    @Column(name = "STUDY_UNIVERSITY_FEE_YEAR")
    private String studyUniversityFeeYear;

    @PositiveOrZero
    @ColumnDefault("0")
    @Column(name = "STUDY_UNIVERSITY_FEE_DISCOUNT")
    private Integer studyUniversityFeeDiscount;

    @PositiveOrZero
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

    /** 0=unset, 1=Wafar, 2=Ministry, 3=moved/reallocated from another field, 4=other university fee row */
    @ColumnDefault("0")
    @Column(name = "STUDY_UNIVERSITY_FEE_SOURCE_ID")
    private Integer studyUniversityFeeSourceId;

    /** 0=unset, 1=one semester, 2=two semesters, 3=three or more semesters */
    @ColumnDefault("0")
    @Column(name = "STUDY_UNIVERSITY_FEE_SEMESTER_SCOPE_ID")
    private Integer studyUniversityFeeSemesterScopeId;

    /** 0=unset, 1=single year, 2=multiple distinct years, 3=overlapping / cross-year span */
    @ColumnDefault("0")
    @Column(name = "STUDY_UNIVERSITY_FEE_YEAR_SCOPE_ID")
    private Integer studyUniversityFeeYearScopeId;

    /** Academic semester ordinal: 1=first semester, 2=second, etc. */
    @ColumnDefault("0")
    @Column(name = "STUDY_UNIVERSITY_FEE_SEMESTER_NUMBER")
    private Integer studyUniversityFeeSemesterNumber;

    /** 0=unset, 1 = payment/check in student's name, 2 = paid directly to university */
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

}