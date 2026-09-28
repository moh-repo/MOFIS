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
public class DashboardOverviewDto {
    private Long totalStudents;
    private Long activeStudents;
    private Long newStudents;
    private Long suspendedStudents;
    private Double totalDisbursedAmount;
    private Double averageDisbursement;
    private List<SimpleDataPointDto> enrollmentTrend;
    private List<SimpleDataPointDto> financialTrend;
    private List<SimpleDataPointDto> topCountries;
}


