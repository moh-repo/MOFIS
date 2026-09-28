package com.dilaw.mohe.wafar.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WafarSpendingCategoryDto {
    private Integer id;
    private String categoryCode;
    private String categoryNameAr;
    private String categoryNameEn;
    private Boolean isActive;
}
