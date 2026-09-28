package com.dilaw.mohe.wafar.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Data
public class WafarSpendingDto {
    private Integer id;
    private WafarSpendingCategoryDto category;
    private BigDecimal amount;
    private LocalDate spendingDate;
    private String referenceNumber;
    private String descriptionAr;
    private String descriptionEn;
    private String payeeNameAr;
    private String payeeNameEn;
    private Integer studentId;
    private String studentName;
    private String beneficiaryType;
    /** The attaché that holds this Wafar. Null on rows written before it was recorded. */
    private Integer studyCountryInId;
    private String status;
    private String notes;
    private Integer createdBy;
    private Instant createdAt;
    private Integer approvedBy;
    private Instant approvedAt;
    private String rejectionReason;
}
