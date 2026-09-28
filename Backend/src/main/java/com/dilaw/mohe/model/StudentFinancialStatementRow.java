package com.dilaw.mohe.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Immutable;

import java.time.Instant;

/**
 * Read-only mapping of V_STUDENT_FINANCIAL_STATEMENT (V016): archived quarter
 * finance rows plus the live current-quarter row per student, with lookup
 * names resolved. ROW_KEY is the view's synthetic unique key ("A-{archId}" /
 * "C-{studentId}").
 */
@Getter
@Setter
@Entity
@Immutable
@Table(name = "V_STUDENT_FINANCIAL_STATEMENT", schema = "MOHECORE")
public class StudentFinancialStatementRow {

    @Id
    @Column(name = "ROW_KEY")
    private String rowKey;

    @Column(name = "SOURCE_TYPE")
    private String sourceType;

    @Column(name = "STUDENT_ID")
    private Integer studentId;

    @Column(name = "MOHE_QUARTER_ID")
    private Integer moheQuarterId;

    @Column(name = "QUARTER_YEAR")
    private Integer quarterYear;

    @Column(name = "QUARTER_NO")
    private Integer quarterNo;

    @Column(name = "QUARTER_NAME_AR")
    private String quarterNameAr;

    @Column(name = "QUARTER_NAME_EN")
    private String quarterNameEn;

    @Column(name = "RECEIVED_YEAR")
    private Integer receivedYear;

    @Column(name = "RECEIVED_MONTH")
    private Integer receivedMonth;

    @Column(name = "RECEIVED_AMOUNT")
    private Integer receivedAmount;

    @Column(name = "WAFAR_AMOUNT")
    private Integer wafarAmount;

    @Column(name = "WAFAR_TYPE_ID")
    private Integer wafarTypeId;

    @Column(name = "WAFAR_TYPE_NAME_AR")
    private String wafarTypeNameAr;

    @Column(name = "WAFAR_TYPE_NAME_EN")
    private String wafarTypeNameEn;

    @Column(name = "NET_AMOUNT")
    private Integer netAmount;

    @Column(name = "CURRENCY_ID")
    private Integer currencyId;

    @Column(name = "CURRENCY_NAME_AR")
    private String currencyNameAr;

    @Column(name = "CURRENCY_NAME_EN")
    private String currencyNameEn;

    @Column(name = "BANK_ID")
    private Integer bankId;

    @Column(name = "BANK_NAME_AR")
    private String bankNameAr;

    @Column(name = "BANK_NAME_EN")
    private String bankNameEn;

    @Column(name = "BANK_BRANCH_ID")
    private Integer bankBranchId;

    @Column(name = "BANK_BRANCH_NAME_AR")
    private String bankBranchNameAr;

    @Column(name = "BANK_BRANCH_NAME_EN")
    private String bankBranchNameEn;

    @Column(name = "RECEIVED_STATUS_ID")
    private Integer receivedStatusId;

    @Column(name = "RECEIVED_STATUS_NAME_AR")
    private String receivedStatusNameAr;

    @Column(name = "RECEIVED_STATUS_NAME_EN")
    private String receivedStatusNameEn;

    @Column(name = "RECEIVED_WHOM_STATUS_ID")
    private Integer receivedWhomStatusId;

    @Column(name = "RECEIVED_WHOM_STATUS_NAME_AR")
    private String receivedWhomStatusNameAr;

    @Column(name = "RECEIVED_WHOM_STATUS_NAME_EN")
    private String receivedWhomStatusNameEn;

    @Column(name = "STUDY_CHECK_ID")
    private String studyCheckId;

    @Column(name = "STUDY_CHECK_DATE")
    private Instant studyCheckDate;

    @Column(name = "STUDY_CHECK_FORM_NUMBER")
    private String studyCheckFormNumber;
}
