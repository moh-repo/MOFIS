package com.dilaw.mohe.dto;

import lombok.Builder;
import lombok.Value;

import java.util.List;

/** The kinds of entitlement and the attachés the caller may report on. */
@Value
@Builder
public class StudentEntitlementsMetadataDto {

    List<KindOption> kinds;
    List<CountryOption> countries;

    @Value
    @Builder
    public static class KindOption {
        /** LOAN, BOOKS or TRAVEL — the value the filter sends back. */
        String code;
        /** Already localised, so the screen does not need its own copy of the names. */
        String name;
    }

    @Value
    @Builder
    public static class CountryOption {
        Integer id;
        String name;
    }
}
