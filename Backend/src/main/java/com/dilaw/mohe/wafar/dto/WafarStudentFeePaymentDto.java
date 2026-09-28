package com.dilaw.mohe.wafar.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Data
public class WafarStudentFeePaymentDto {
    private Integer id;
    private Integer spendingId;
    private Integer studentId;
    private String academicYear;
    private String notes;
    private Instant createdAt;
    private BigDecimal amount;
    private LocalDate spendingDate;
    private String referenceNumber;
    private String status;
}
