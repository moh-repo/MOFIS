package com.dilaw.mohe.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class EdgeDto {
    private Integer id;
    private Integer from;
    private Integer to;
    private String label;
    private String title; // Tooltip
    private Integer width; // Line thickness based on strength
    private String color;
    private String connectionType;
    private Integer connectionStrength;
    private String dashes; // For different line styles
}

