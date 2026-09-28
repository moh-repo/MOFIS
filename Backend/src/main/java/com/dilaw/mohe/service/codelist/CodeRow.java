package com.dilaw.mohe.service.codelist;

/**
 * One row of a code list as the application reads it: the code, both names,
 * its order and how it is shown. {@code active} is false for a soft-deleted
 * row — kept in the list so a historical record still resolves its label.
 * {@code uiColor} is only set by lists that carry a hex colour (study status,
 * graph category).
 */
public record CodeRow(
        Integer id,
        String code,
        String nameAr,
        String nameEn,
        int sortOrder,
        String uiSeverity,
        String icon,
        boolean system,
        boolean active,
        String uiColor) {

    public String name(String lang) {
        boolean english = "en".equalsIgnoreCase(lang);
        String primary = english ? nameEn : nameAr;
        String fallback = english ? nameAr : nameEn;
        if (primary != null && !primary.isBlank()) return primary;
        if (fallback != null && !fallback.isBlank()) return fallback;
        return code;
    }
}
