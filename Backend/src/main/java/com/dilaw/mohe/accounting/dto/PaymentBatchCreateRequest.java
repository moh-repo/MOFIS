package com.dilaw.mohe.accounting.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

/**
 * What the screen sends to prepare a batch.
 *
 * <p>The lines are explicit. The screen may build them from the quarter's
 * finance rows (see {@code PaymentBatchService.candidates}) or by hand, but the
 * server is told exactly which students and which amounts — a batch is a
 * document someone signs, so what it contains must be what they saw.
 *
 * <p>{@code studyCountryInId} may be omitted by an officer holding a single
 * attaché; the server stamps theirs. The ministry must say which.
 *
 * <p>Bean constraints (plan item 2.6) say what the table requires — quarter,
 * currency, at least one line, and a student and entitled amount on each — so
 * a malformed body is a 400 that names the field. The service keeps its own
 * checks for the rules a constraint cannot express (the fiscal year, the
 * country scope, a disbursed amount that exceeds the entitlement).
 */
public record PaymentBatchCreateRequest(
        @NotNull(message = "moheQuarterKey is required") Integer moheQuarterKey,
        Integer studyCountryInId,
        Integer feeTypeId,
        @NotNull(message = "currencyId is required") Integer currencyId,
        @Size(max = 1000) String notes,
        @NotEmpty(message = "A batch must carry at least one line.") List<@Valid Line> lines) {

    /**
     * @param disbursedAmount what the student actually gets; null means paid in full
     * @param heldReason      why not, when {@code disbursedAmount} is short
     */
    public record Line(
            @NotNull(message = "studentId is required on every line") Integer studentId,
            @NotNull(message = "entitledAmount is required on every line")
            @PositiveOrZero BigDecimal entitledAmount,
            @PositiveOrZero BigDecimal disbursedAmount,
            @Size(max = 500) String heldReason) {
    }
}
