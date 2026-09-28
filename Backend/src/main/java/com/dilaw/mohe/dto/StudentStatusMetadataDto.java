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
public class StudentStatusMetadataDto {
    private List<LookupOptionDto> statuses;
    private List<LookupOptionDto> countries;
    private List<LookupOptionDto> majors;
}


