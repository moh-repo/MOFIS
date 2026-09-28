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
public class StudentQuickEditMetadataDto {
    private List<LookupOptionDto> genders;
    private List<LookupOptionDto> statuses;
    private List<LookupOptionDto> degrees;
    private List<LookupOptionDto> majors;
    private List<LookupOptionDto> levels;
    private List<LookupOptionDto> countries;
    private List<LookupOptionDto> governments;
    private List<LookupOptionDto> banks;
    private List<LookupOptionDto> bankBranches;
    private List<LookupOptionDto> dofs;
    private List<LookupOptionDto> universityFeeStatuses;
    private List<LookupOptionDto> finReceivedStatuses;
    private List<LookupOptionDto> receivedWhomStatuses;
    private List<LookupOptionDto> agencyTypes;
    private List<LookupOptionDto> ministryTypes;
    private List<LookupOptionDto> studyTypes;
    private List<LookupOptionDto> universityTypes;
    private List<LookupOptionDto> homeCountries;
    private List<LookupOptionDto> finReceivedWhoms;
}

