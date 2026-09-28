package com.dilaw.mohe.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * Body of {@code POST /paymentAuth/bulk-authorize}: {@code { "paymentIds": [1, 2, 3] }}.
 *
 * <p>Was a raw {@code Map<String, List<Integer>>} under {@code @Valid}, which
 * validates nothing — a body with the wrong key reached the service as a null
 * list. Now a missing or empty list is a 400 before the service is entered
 * (plan item 1.2).
 */
public record PaymentAuthBulkAuthorizeRequest(
        @NotEmpty(message = "paymentIds must not be empty")
        List<@NotNull Integer> paymentIds) {
}
