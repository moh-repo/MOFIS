package com.dilaw.mohe.calendar.dto;

import java.time.Instant;

/**
 * One line of the topbar calendar's day agenda.
 *
 * <p>The record's own text travels in {@code title} and {@code detail} — a
 * document name, a scenario, a message subject, a quarter's name — because that
 * is data, and it is stored in whichever language it was entered in. What the
 * <em>kind</em> of entry is called is not data: {@code kind} is a key
 * ({@link com.dilaw.mohe.calendar.service.CalendarEntryKind}) that the browser
 * translates and turns into a route. Nothing here names a screen; the server has
 * no business knowing the client's URLs.
 *
 * @param kind        one of {@link com.dilaw.mohe.calendar.service.CalendarEntryKind}
 * @param referenceId the row the entry points at, or null when the entry is a count
 * @param title       the record's own text
 * @param detail      a second line — student, country, channel — or null
 * @param at          when on the day, or null for a row that carries a date only
 * @param uiSeverity  the PrimeNG severity of the record's own code, or null
 */
public record CalendarEntryDto(
        String kind,
        Integer referenceId,
        String title,
        String detail,
        Instant at,
        String uiSeverity) {
}
