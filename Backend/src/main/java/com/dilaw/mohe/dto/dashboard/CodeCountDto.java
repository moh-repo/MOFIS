package com.dilaw.mohe.dto.dashboard;

import com.dilaw.mohe.service.codelist.CodeRow;

/**
 * A count keyed by a code, carrying how the code is shown: both names, the
 * PrimeNG severity, the icon and — for lists that own a colour — the hex
 * colour. {@code code} is null for rows the database could not attribute
 * (a student with no status, a country never set); the client shows
 * "unspecified" in its own language.
 */
public record CodeCountDto(
        String code,
        String nameAr,
        String nameEn,
        String uiSeverity,
        String icon,
        String uiColor,
        int sortOrder,
        double count) {

    public static CodeCountDto of(CodeRow row, double count) {
        return new CodeCountDto(row.code(), row.nameAr(), row.nameEn(), row.uiSeverity(), row.icon(),
                row.uiColor(), row.sortOrder(), count);
    }

    /** A row whose code is unknown to the list: the code itself stands as its name. */
    public static CodeCountDto unresolved(String code, int sortOrder, double count) {
        return new CodeCountDto(code, code, code, null, null, null, sortOrder, count);
    }
}
