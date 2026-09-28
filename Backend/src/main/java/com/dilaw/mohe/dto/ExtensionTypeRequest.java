package com.dilaw.mohe.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ExtensionTypeRequest {

    @NotBlank(message = "Extension type code is required")
    @Size(max = 50, message = "Extension type code must not exceed 50 characters")
    private String extensionTypeCode;

    @NotBlank(message = "Extension type name (English) is required")
    @Size(max = 200, message = "Extension type name (English) must not exceed 200 characters")
    private String extensionTypeNameEn;

    @Size(max = 200, message = "Extension type name (Arabic) must not exceed 200 characters")
    private String extensionTypeNameAr;

    @Size(max = 500, message = "Description must not exceed 500 characters")
    private String description;

    private Boolean isActive;
    
    private Integer displayOrder;
}

