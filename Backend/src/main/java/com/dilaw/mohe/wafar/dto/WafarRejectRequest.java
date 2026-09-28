package com.dilaw.mohe.wafar.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code PUT /wafar/transactions/{id}/reject}.
 *
 * <p>Was a raw {@code Map<String,String>} under {@code @Valid}, which validates
 * nothing: a rejection with no reason went through and was stored as an empty
 * string. A rejection is a decision someone has to answer for, so the reason is
 * required. Width follows {@code WAFAR_SPENDING.REJECTION_REASON}.
 */
public record WafarRejectRequest(
        @NotBlank(message = "A rejection must give a reason")
        @Size(max = 500)
        String reason) {
}
