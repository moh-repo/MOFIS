package com.dilaw.mohe.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** A selectable travel funding source; {@code name} is localized for the caller's language. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TravelFundingSourceDto {
    private Integer id;
    private String code;
    private String name;
    private String nameEn;
    private String nameAr;
}
