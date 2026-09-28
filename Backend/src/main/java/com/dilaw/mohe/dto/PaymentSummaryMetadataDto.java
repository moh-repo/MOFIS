package com.dilaw.mohe.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentSummaryMetadataDto {
    private List<LookupOptionDto> statuses;
    private List<LookupOptionDto> types;
    private List<LookupOptionDto> quarters;
    private List<LookupOptionDto> years;
}

