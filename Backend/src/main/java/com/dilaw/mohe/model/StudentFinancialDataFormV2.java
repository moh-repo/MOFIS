package com.dilaw.mohe.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Immutable;

/**
 * One row of the quarterly financial assistance statement
 * ("كشوفات المساعدة المالية الربعية"), read from
 * {@code MOHECORE.Q_Stud_Financial_Data_Form_V2} (V057).
 *
 * <p>This is the second financial data form, laid out like the ministry's own
 * statement; the first ({@link StudentFinancialDataForm}) is unchanged. The
 * view resolves what the printed form needs — the sponsoring entity through
 * the university → ministry → agency fallback, the level as the number the form
 * prints, and the country id for scoping — so the template prints names, never
 * foreign keys.
 */
@Getter
@Setter
@Entity
@Immutable
@Table(name = "Q_Stud_Financial_Data_Form_V2", schema = "MOHECORE")
public class StudentFinancialDataFormV2 {

    @Id
    @Column(name = "STUDENT_ID")
    private Integer studentId;

    @Column(name = "STUDENT_NAME")
    private String studentName;

    /** الجهة — university type, else ministry type, else agency type. */
    @Column(name = "ENTITY_NAME")
    private String entityName;
    
    /** The same name in English; resolved by BilingualRows for an EN render. */
    @Column(name = "ENTITY_NAME_EN")
    private String entityNameEn;

    @Column(name = "STUDENT_AGENCY_TYPE_NAME")
    private String studentAgencyTypeName;
    
    /** The same name in English; resolved by BilingualRows for an EN render. */
    @Column(name = "STUDENT_AGENCY_TYPE_NAME_EN")
    private String studentAgencyTypeNameEn;

    @Column(name = "STUDENT_MINISTRY_TYPE_NAME")
    private String studentMinistryTypeName;
    
    /** The same name in English; resolved by BilingualRows for an EN render. */
    @Column(name = "STUDENT_MINISTRY_TYPE_NAME_EN")
    private String studentMinistryTypeNameEn;

    @Column(name = "STUDENT_UNIVERSITY_TYPE_NAME")
    private String studentUniversityTypeName;
    
    /** The same name in English; resolved by BilingualRows for an EN render. */
    @Column(name = "STUDENT_UNIVERSITY_TYPE_NAME_EN")
    private String studentUniversityTypeNameEn;

    @Column(name = "STUDY_COUNTRY_IN_ID")
    private Integer studyCountryInId;

    /** الدولة — the country of study on the finance row. */
    @Column(name = "STUDY_COUNTRY_IN_NAME")
    private String studyCountryInName;
    
    /** The same name in English; resolved by BilingualRows for an EN render. */
    @Column(name = "STUDY_COUNTRY_IN_NAME_EN")
    private String studyCountryInNameEn;

    @Column(name = "STUDENT_COUNTRY_NAME")
    private String studentCountryName;
    
    /** The same name in English; resolved by BilingualRows for an EN render. */
    @Column(name = "STUDENT_COUNTRY_NAME_EN")
    private String studentCountryNameEn;

    /** عام الإيفاد. */
    @Column(name = "STUDENT_DELEGATION_DECISION_YEAR")
    private String studentDelegationDecisionYear;

    @Column(name = "STUDENT_DEGREE_NAME")
    private String studentDegreeName;
    
    /** The same name in English; resolved by BilingualRows for an EN render. */
    @Column(name = "STUDENT_DEGREE_NAME_EN")
    private String studentDegreeNameEn;

    @Column(name = "STUDENT_MAJOR_NAME")
    private String studentMajorName;
    
    /** The same name in English; resolved by BilingualRows for an EN render. */
    @Column(name = "STUDENT_MAJOR_NAME_EN")
    private String studentMajorNameEn;

    /** رقم القرار. */
    @Column(name = "STUDENT_DELEGATE_DECISION_NO")
    private Integer studentDelegateDecisionNo;

    /** مدة الدراسة. */
    @Column(name = "STUDENT_STUDY_NO_OF_YEARS")
    private Integer studentStudyNoOfYears;

    @Column(name = "STUDENT_CURRENT_LEVEL_ID")
    private Integer studentCurrentLevelId;

    @Column(name = "CURRENT_LEVEL_NAME")
    private String currentLevelName;

    @Column(name = "CURRENT_LEVEL_NAME_EN")
    private String currentLevelNameEn;

    @Column(name = "CURRENT_LEVEL_ORDER_NO")
    private Integer currentLevelOrderNo;

    /** المستوى الحالي as printed: "7" for a study year, else the level name. */
    @Column(name = "CURRENT_LEVEL_LABEL")
    private String currentLevelLabel;

    /** أول مصرف — the first quarter paid, e.g. 20173. */
    @Column(name = "STUDENT_FIRST_RECEIPT_OF_DUE")
    private Integer studentFirstReceiptOfDue;

    /** المبلغ — the student's quarterly allowance. */
    @Column(name = "STUDENT_AMOUNT")
    private Integer studentAmount;

    @Column(name = "STUDENT_FIN_RECIEVED_AMOUNT")
    private Integer studentFinReceivedAmount;

    /** ملاحظات. */
    @Column(name = "STUDENT_NOTE_1")
    private String studentNote1;

    /** Printed in the "توقيع الطالب" cell when present (payment-stop flags). */
    @Column(name = "STUDENT_NOTE_2")
    private String studentNote2;

    @Column(name = "STUDENT_STUDY_STATUS_NAME")
    private String studentStudyStatusName;
    
    /** The same name in English; resolved by BilingualRows for an EN render. */
    @Column(name = "STUDENT_STUDY_STATUS_NAME_EN")
    private String studentStudyStatusNameEn;

    @Column(name = "STUDENT_FIN_RECIEVED_YEAR")
    private Integer studentFinReceivedYear;

    /** Holds the quarter number (1–4) despite the column's name. */
    @Column(name = "STUDENT_FIN_RECIEVED_MONTH")
    private Integer studentFinReceivedMonth;

    @Column(name = "MOHE_QUARTER_ID")
    private Integer moheQuarterId;
}
