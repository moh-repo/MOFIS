package com.dilaw.mohe.alerteventcases.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * Counts of the caller's alert cases. Since V072 the legend is the table:
 * {@link #bySeverity} and {@link #byStatus} carry one row per active code of
 * {@code LU_ALERT_SEVERITY} / {@code LU_ALERT_STATUS} — name in both
 * languages, colour, icon and order — zero counts included, so a screen
 * lists exactly the codes the ministry has defined. The scalar totals stay
 * for the callers that read them.
 */
@Getter
@Setter
public class AlertEventCaseSummaryDto {

    private long totalOpen;
    private long totalAcknowledged;
    private long totalResolved;
    private long totalDismissed;
    private long totalAll;

    // Open by severity
    private long openCritical;
    private long openHigh;
    private long openMedium;
    private long openLow;

    /** Open cases per severity code, in the code list's order. */
    private List<CodeCount> bySeverity = new ArrayList<>();

    /** Cases per status code, in the code list's order. */
    private List<CodeCount> byStatus = new ArrayList<>();

    /** One legend row: a code, how it is shown, and its count. */
    public record CodeCount(String code, String nameAr, String nameEn, String uiSeverity, String icon,
                            int sortOrder, long count) {
    }
}
