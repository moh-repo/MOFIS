package com.dilaw.mohe.dto;

import lombok.Builder;
import lombok.Value;

import java.util.List;

/** Queue states and attachés the caller may report on. */
@Value
@Builder
public class ApprovalsReportMetadataDto {

    List<StateOption> states;
    List<CountryOption> countries;

    @Value
    @Builder
    public static class StateOption {
        /** PENDING or COMPLETED — the value the filter sends back. */
        String code;
        String name;
    }

    @Value
    @Builder
    public static class CountryOption {
        Integer id;
        String name;
    }
}
