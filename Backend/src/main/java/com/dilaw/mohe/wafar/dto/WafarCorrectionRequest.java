package com.dilaw.mohe.wafar.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * A correction the ministry proposes to a Wafar spend (N-04.e). Only the
 * fields given are changed; {@code cancel} cancels a still-pending row.
 * An amount, when given, is positive (FIN-01).
 */
public record WafarCorrectionRequest(@Positive BigDecimal amount, LocalDate spendingDate, String descriptionAr,
                                     String descriptionEn, String notes, Boolean cancel,
                                     @NotBlank String reason) {

    public boolean changesNothing() {
        return amount == null && spendingDate == null && descriptionAr == null && descriptionEn == null
                && notes == null && !Boolean.TRUE.equals(cancel);
    }
}
