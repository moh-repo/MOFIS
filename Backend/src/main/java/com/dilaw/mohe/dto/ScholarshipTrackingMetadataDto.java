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
public class ScholarshipTrackingMetadataDto {
    private List<LookupOptionDto> types;
    private List<LookupOptionDto> providers;
    private List<LookupOptionDto> statuses;
}

