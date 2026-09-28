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
public class AcademicPerformanceMetadataDto {
    private List<LookupOptionDto> majors;
    private List<LookupOptionDto> levels;
    private List<LookupOptionDto> statuses;
    private List<LookupOptionDto> degrees;
    private List<LookupOptionDto> countries;
}

