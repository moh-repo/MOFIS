package com.dilaw.mohe.notification.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class StudentFilterDto {
    private List<Integer> countryIds;
    private List<Integer> majorIds;
    private List<Integer> studyStatusIds;
    private List<Integer> universityTypeIds;
    private List<Integer> governorateIds;
}
