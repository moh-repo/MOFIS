package com.dilaw.mohe.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One option of a code list as the frontend receives it. {@code label} is
 * pre-selected for the requested language; both names travel too, with the
 * V072 presentation columns, so one payload serves both languages.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class EnumOptionDto {
    private String label;
    private String value;
    private String labelAr;
    private String labelEn;
    private Integer sortOrder;
    private String uiSeverity;
    private String icon;
    private Boolean isSystem;
    private Boolean active;
    /** The list's extra column, when it has one (hex colour, normal balance, lookup list). */
    private String extra;
    /**
     * The row's key, which the Code Lists screen edits, toggles and deletes by. It was
     * not sent, so every one of those actions had no row to address: the toggle sent
     * nothing, delete did nothing and edit posted a create (409) — live QA #3.
     */
    private Integer id;

    public EnumOptionDto(String label, String value, String labelAr, String labelEn) {
        this(label, value, labelAr, labelEn, null, null, null, null, Boolean.TRUE, null, null);
    }
}
