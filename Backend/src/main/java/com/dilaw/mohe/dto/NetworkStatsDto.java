package com.dilaw.mohe.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class NetworkStatsDto {
    private Long totalNodes;
    private Long totalConnections;
    private Long employedCount;
    private Double avgConnectionStrength;
    private String mostCommonConnectionType;
    private Long totalUniversities;
    private Long totalMajors;
    private Long totalCountries;
}

