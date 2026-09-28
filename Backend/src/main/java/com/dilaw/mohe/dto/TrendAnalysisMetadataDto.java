package com.dilaw.mohe.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TrendAnalysisMetadataDto {
    private List<LookupOptionDto> metricTypes;
    private List<LookupOptionDto> directions;
    private List<Integer> years;
}


