package com.dilaw.mohe.calendar.controller;

import com.dilaw.mohe.calendar.dto.CalendarDayDto;
import com.dilaw.mohe.calendar.dto.CalendarMonthDto;
import com.dilaw.mohe.calendar.service.CalendarAgendaService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * The topbar calendar's agenda.
 *
 * <p>Open to any signed-in user, because the endpoint itself discloses nothing:
 * {@link CalendarAgendaService} includes a kind only when the caller holds an
 * authority that opens the screen behind it, so a user with no permissions gets
 * an empty day rather than a 403. Gating the whole endpoint on one page code
 * would have meant choosing which of six screens the calendar "belongs" to.
 */
@RestController
@RequestMapping("api/v1/calendar")
@PreAuthorize("isAuthenticated()")
public class CalendarController {

    private final CalendarAgendaService agenda;

    public CalendarController(CalendarAgendaService agenda) {
        this.agenda = agenda;
    }

    /** What falls on {@code date} (ISO {@code yyyy-MM-dd}, in the application time zone). */
    @GetMapping("/day")
    public ResponseEntity<CalendarDayDto> day(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(agenda.day(date));
    }

    /** How many entries fall on each day of a month, so the picker can mark them. */
    @GetMapping("/month")
    public ResponseEntity<CalendarMonthDto> month(
            @RequestParam int year,
            @RequestParam int month) {
        return ResponseEntity.ok(agenda.month(year, month));
    }
}
