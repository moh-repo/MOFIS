package com.dilaw.mohe.calendar.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * How many entries fall on each day of a month, so the calendar can mark the
 * days worth opening.
 *
 * <p>Only days with at least one entry appear. A month with nothing in it
 * answers an empty list rather than thirty zeroes.
 *
 * <p>{@code failed} names the kinds that could not be counted (QA C10 / N14).
 * A month whose marks are incomplete says so, rather than showing fewer dots
 * and letting the reader conclude those days are free.
 */
public record CalendarMonthDto(int year, int month, List<DayCount> days, List<String> failed) {

    public record DayCount(LocalDate date, long count) {
    }
}
