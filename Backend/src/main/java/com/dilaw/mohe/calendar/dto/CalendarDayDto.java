package com.dilaw.mohe.calendar.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * What falls on one day, for the topbar calendar.
 *
 * <p>{@code totals} counts every entry of each kind that the caller may see —
 * including the ones past {@code entries}' cap, so "12 notifications" can be
 * shown honestly beside the three that are listed. A kind the caller has no
 * permission for is absent from both, rather than present with a zero: the
 * agenda shows what you can act on, and an empty section you may not open is
 * noise.
 *
 * <p>{@code failed} names the kinds that could not be read at all (QA C10 /
 * N14). One kind raising used to fail the whole request, so a broken
 * notification query emptied the reader's whole day; now the rest of the agenda
 * is answered and the client is told which parts are missing, so it can say so
 * and offer to try again instead of showing an empty day that looks settled.
 *
 * @param date    the day, in the application time zone
 * @param entries the listed entries, newest first within each kind
 * @param totals  entry count per kind, before the per-kind cap
 * @param failed  kinds that could not be read; empty when the whole agenda loaded
 */
public record CalendarDayDto(
        LocalDate date,
        List<CalendarEntryDto> entries,
        Map<String, Long> totals,
        List<String> failed) {
}
