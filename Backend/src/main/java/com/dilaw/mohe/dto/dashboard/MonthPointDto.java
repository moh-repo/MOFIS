package com.dilaw.mohe.dto.dashboard;

/**
 * One month of a monthly series: the calendar month as data, not as a label.
 * The client names the month in its own language ({@code Intl.DateTimeFormat});
 * until V072 the server sent English {@code MMM} strings.
 */
public record MonthPointDto(int year, int month, long count) {
}
