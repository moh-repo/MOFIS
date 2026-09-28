package com.dilaw.mohe.accounting.controller;

import com.dilaw.mohe.accounting.model.FxRate;
import com.dilaw.mohe.accounting.service.FxRateService;
import com.dilaw.mohe.config.MoheSecurityExpressions;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * Exchange rates. Reads for any accounting user; writes need {@code COA.EDIT},
 * because a rate is reference data of the same controlled kind as the chart.
 */
@RestController
@RequestMapping("api/v1/accounting/fx-rates")
@PreAuthorize(MoheSecurityExpressions.ACCOUNTING_VIEW)
public class FxRateController {

    @Autowired private FxRateService rateService;

    @GetMapping
    public ResponseEntity<List<FxRate>> list(
            @RequestParam(required = false) Integer fromCurrencyId,
            @RequestParam(required = false) Integer toCurrencyId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate on) {
        return ResponseEntity.ok(rateService.list(fromCurrencyId, toCurrencyId, on));
    }

    /** The rate in force on a day. 404 when none is recorded — never a guessed 1.0. */
    @GetMapping("/effective")
    public ResponseEntity<FxRate> effective(
            @RequestParam Integer fromCurrencyId,
            @RequestParam Integer toCurrencyId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate on) {
        return rateService.effective(fromCurrencyId, toCurrencyId, on)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping
    @PreAuthorize(MoheSecurityExpressions.COA_EDIT)
    public ResponseEntity<FxRate> create(@Valid @RequestBody FxRate rate) {
        return ResponseEntity.ok(rateService.create(rate));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(MoheSecurityExpressions.COA_EDIT)
    public ResponseEntity<Void> delete(@PathVariable Integer id) {
        rateService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
