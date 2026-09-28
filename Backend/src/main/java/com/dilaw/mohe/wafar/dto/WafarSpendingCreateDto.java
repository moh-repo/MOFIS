package com.dilaw.mohe.wafar.dto;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class WafarSpendingCreateDto {
    private Integer categoryId;

    /**
     * FIN-01: a spend is money leaving the attaché, so it is strictly positive. A
     * negative one used to be recorded and approved, and approving it raised the
     * balance. The service and {@code CK_WAFAR_SPENDING_AMOUNT_POSITIVE} refuse it too.
     */
    @NotNull
    @Positive
    private BigDecimal amount;
    private LocalDate spendingDate;
    private String referenceNumber;
    private String descriptionAr;
    private String descriptionEn;
    private String payeeNameAr;
    private String payeeNameEn;
    private Integer studentId;

    /**
     * The attaché whose Wafar is being spent. Optional when a student is named —
     * the student's own country wins — and when the caller has exactly one
     * country. Required otherwise, because a spend with no attaché is what left
     * 11 of the 17 existing rows unattributable.
     */
    private Integer studyCountryInId;

    private String beneficiaryType;
    private String academicYear;
    private String notes;

    /**
     * Accepts the spend as GET returns it (round-5 QA R5-18): there the category is an
     * object, {@code "category": {"id": 3, ...}}, and a PUT of that body arrived with no
     * categoryId and answered 500. An explicit categoryId wins.
     */
    @JsonSetter("category")
    public void setCategory(JsonNode category) {
        if (categoryId == null && category != null && category.hasNonNull("id")) {
            categoryId = category.get("id").asInt();
        }
    }
}
