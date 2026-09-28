package com.dilaw.mohe.dto;

import lombok.Builder;
import lombok.Value;

import java.util.List;

/** Fiscal years and attachés the caller may report on. */
@Value
@Builder
public class AccountingReportMetadataDto {

    List<FiscalYearOption> fiscalYears;
    List<CountryOption> countries;

    @Value
    @Builder
    public static class FiscalYearOption {
        Integer id;
        String name;
        /** OPEN or CLOSED — shown so a closed year is not mistaken for a live one. */
        String status;
    }

    @Value
    @Builder
    public static class CountryOption {
        Integer id;
        String name;
    }
}
