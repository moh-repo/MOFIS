package com.dilaw.mohe.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * One line of the student financial statement Jasper document: a finance row
 * (archived quarter or the live current quarter) with lookup names already
 * resolved to the requested language.
 */
@Getter
@Setter
public class StudentFinancialLineDto {

    private String rowKey;
    private String sourceType;
    private Integer studentId;

    private Integer quarterCode;
    private String quarterLabel;

    private Integer receivedYear;
    private Integer receivedMonth;
    private Integer receivedAmount;
    private Integer wafarAmount;
    private String wafarTypeName;
    private Integer netAmount;
    private String currencyName;
    private String bankName;
    private String bankBranchName;
    private String receivedStatusName;
    private String checkNo;
    private String checkDate;
    private String checkFormNumber;
}
