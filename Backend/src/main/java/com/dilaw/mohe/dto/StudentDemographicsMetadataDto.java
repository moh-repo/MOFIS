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
public class StudentDemographicsMetadataDto {
    private List<LookupOptionDto> countries;
    private List<LookupOptionDto> genders;
    private List<String> ageRanges;
    private List<LookupOptionDto> levels;
    private List<LookupOptionDto> studyTypes;
    private List<LookupOptionDto> degrees;
    private List<LookupOptionDto> majors;
    private List<LookupOptionDto> statuses;
}

