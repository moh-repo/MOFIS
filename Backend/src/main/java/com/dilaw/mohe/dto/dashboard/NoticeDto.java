package com.dilaw.mohe.dto.dashboard;

import com.dilaw.mohe.model.Alert;
import com.dilaw.mohe.service.codelist.CodeRow;

import java.time.Instant;
import java.util.Optional;

/**
 * A system notice with its type, priority and status resolved through the
 * code lists ({@code LU_ALERT_TYPE}, {@code LU_ALERT_SEVERITY},
 * {@code LU_NOTICE_STATUS}): names in both languages, colour and icon travel
 * with the row, so the client shows a name, not the code, and never invents a
 * default for a value the record does not carry.
 */
public record NoticeDto(
        Integer alertId,
        String alertTitle,
        String alertMessage,
        /** English, where the notice has one (V125); pick by language, falling back to the Arabic. */
        String alertTitleEn,
        String alertMessageEn,
        CodeRef type,
        CodeRef priority,
        CodeRef status,
        String createdBy,
        Instant createdDate,
        Instant expiresDate,
        Boolean isRead) {

    /** A code with how it is shown; {@code code} alone when the list does not know it. */
    public record CodeRef(String code, String nameAr, String nameEn, String uiSeverity, String icon) {
        public static CodeRef of(String code, Optional<CodeRow> row) {
            if (code == null) return null;
            return row.map(r -> new CodeRef(r.code(), r.nameAr(), r.nameEn(), r.uiSeverity(), r.icon()))
                    .orElse(new CodeRef(code, code, code, null, null));
        }
    }

    public static NoticeDto of(Alert a, CodeRef type, CodeRef priority, CodeRef status) {
        return new NoticeDto(a.getAlertId(), a.getAlertTitle(), a.getAlertMessage(),
                a.getAlertTitleEn(), a.getAlertMessageEn(), type, priority, status,
                a.getCreatedBy(), a.getCreatedDate(), a.getExpiresDate(), a.getIsRead());
    }
}
