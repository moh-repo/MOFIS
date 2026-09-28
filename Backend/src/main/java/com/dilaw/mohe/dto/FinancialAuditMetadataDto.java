package com.dilaw.mohe.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FinancialAuditMetadataDto {

    private List<LookupOptionDto> statuses;
    private List<LookupOptionDto> categories;
    private List<LookupOptionDto> banks;
    private List<LookupOptionDto> auditors;
    private List<LookupOptionDto> currencies;
    private List<LookupOptionDto> years;
    private List<LookupOptionDto> quarters;
}


