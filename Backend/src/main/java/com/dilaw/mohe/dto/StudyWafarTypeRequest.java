package com.dilaw.mohe.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class StudyWafarTypeRequest {

    @NotBlank(message = "Wafar name is required")
    private String wafarName;

    private String wafarNameEn;

    /**
     * No initialiser: an edit that never mentions the flag must leave the stored
     * one alone, and an initialiser makes "absent" look like an explicit true.
     * {@code createFromRequest} defaults it instead.
     */
    private Boolean isActive;

    private String wafarNote1;
}
