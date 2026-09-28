package com.dilaw.mohe.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExtensionTypeDto {
    private Integer extensionTypeId;
    private String extensionTypeCode;
    private String extensionTypeNameEn;
    private String extensionTypeNameAr;
    private String description;
    private Boolean isActive;
    private Integer displayOrder;
    private LocalDateTime createdDate;
    private String createdBy;
    private LocalDateTime modifiedDate;
    private String modifiedBy;
}

