package com.dilaw.mohe.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class NodeDto {
    private Integer id;
    private Integer studentId;
    private String label;
    private String title; // Tooltip
    private String group; // For grouping/coloring
    private String color;
    private Integer size;
    private String shape; // dot, square, triangle, diamond, star
    private BigDecimal x; // For fixed positioning
    private BigDecimal y;
    
    // Additional data for details panel
    private String university;
    private String major;
    private Integer graduationYear;
    private String degreeLevel;
    private String country;
    private String employmentStatus;
    private String currentPosition;
    private String currentCompany;
    private String email;
    private String phone;
    private String linkedinUrl;
    private Long connectionCount;
}

