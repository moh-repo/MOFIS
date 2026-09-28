package com.dilaw.mohe.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One entitlement owed to or paid for a student, whatever kind it is.
 *
 * <p>Loans, books allowance and travel tickets live in three tables with three
 * shapes and three small screens. From a student's point of view they are one
 * question — what has this student been given, and what is still owed — so the
 * report flattens all three into one line shape with a {@code kind} column
 * rather than printing three unrelated tables.
 *
 * <p>The cost of that choice is stated rather than hidden: {@code detail} carries
 * whatever the source row has that the common columns cannot hold — a route for
 * a ticket, a repayment note for a loan.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StudentEntitlementRowDto {

    /** LOAN, BOOKS or TRAVEL — already localised for the requested language. */
    private String kind;

    private Integer studentId;
    private String studentName;

    private Integer studyCountryInId;
    private String studyCountryName;

    private String studyYear;
    private LocalDate entitlementDate;

    private BigDecimal amount;
    private String currencyName;

    private String status;

    /** The code behind {@code status} — loan-status, books-allowance-pay-status or travel-type — for the screen's tag colour. */
    private String statusCode;

    /** Route, repayment note, or whatever else the source row carries. */
    private String detail;
}
