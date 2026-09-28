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
public class EnrollmentStatisticsMetadataDto {
    /** Raw MOHE quarter codes present in the view, e.g. 20251. Kept for callers that filter by code. */
    private List<Integer> quarters;
    /** The same codes carrying a readable label, for the quarter filter. */
    private List<LookupOptionDto> quarterOptions;
    private List<LookupOptionDto> countries;
    private List<LookupOptionDto> majors;
    private List<LookupOptionDto> degrees;
    private List<LookupOptionDto> studyTypes;
}

