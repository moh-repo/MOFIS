package com.dilaw.mohe.dto;

import lombok.Builder;
import lombok.Value;

import java.time.LocalDate;
import java.util.List;

/**
 * What the audit trail screen needs before it can ask for anything.
 *
 * <p>Carries the default window and the row cap so the screen can pre-fill the
 * dates and warn before a request that the server would refuse — the filters are
 * not a suggestion here, they are the only way the report is producible.
 */
@Value
@Builder
public class AuditTrailReportMetadataDto {

    /** Tables present in the caller's own scope, not every audited table. */
    List<String> tableNames;

    List<String> changedByOptions;
    List<String> operations;

    LocalDate defaultDateFrom;
    LocalDate defaultDateTo;

    /** Above this the server refuses rather than truncating. */
    int maxRows;

    List<CountryOption> countries;

    @Value
    @Builder
    public static class CountryOption {
        Integer id;
        String name;
    }
}
