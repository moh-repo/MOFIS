package com.dilaw.mohe.calendar;

import com.dilaw.mohe.calendar.dto.CalendarDayDto;
import com.dilaw.mohe.calendar.dto.CalendarMonthDto;
import com.dilaw.mohe.calendar.service.CalendarAgendaService;
import com.dilaw.mohe.calendar.service.CalendarEntryKind;
import com.dilaw.mohe.notification.service.NotificationService;
import com.dilaw.mohe.scope.AsUser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

/**
 * One failing kind does not take the day with it (plan W6-07, QA C10 / N14).
 *
 * <p>The agenda gathers from seven places. It read them in a loop with nothing
 * between them and the caller, so a single failing query answered the whole
 * request with a 500 — and the reader, who cannot tell a broken agenda from an
 * empty one, saw a day with nothing on it and moved on. Now the kind that
 * failed is named in the answer and the rest is served.
 */
@Tag("integration")
@SpringBootTest
class CalendarResilienceTest {

    /** Every kind is permitted, so what is missing is missing because it failed. */
    private static final List<String> SEES_EVERYTHING =
            List.of("STUDENTS.VIEW", "NOTIFICATIONS.VIEW", "ALERTS.VIEW", "REPORTS.VIEW", "APPROVALS.VIEW");

    @Autowired private CalendarAgendaService agenda;

    /** The one kind made to fail; every other kind reads the real database. */
    @MockBean private NotificationService notificationService;

    private void notificationsAreBroken() {
        when(notificationService.getHistory(anyInt(), anyInt(), any(), any(), any(), any(), any(), any(), any()))
                .thenThrow(new IllegalStateException("the notification log is unreadable"));
    }

    @Test
    @DisplayName("a day whose notifications fail still answers, and says which kind is missing")
    void theDaySurvivesOneBrokenKind() {
        notificationsAreBroken();

        CalendarDayDto day = AsUser.callWithAuthorities(AsUser.SUPERADMIN, SEES_EVERYTHING,
                () -> agenda.day(LocalDate.now()));

        assertThat(day).isNotNull();
        assertThat(day.failed())
                .as("the reader is told what is missing rather than shown a quiet day")
                .contains(CalendarEntryKind.NOTIFICATION);
        assertThat(day.totals())
                .as("the kind that failed reports no total, which would read as none")
                .doesNotContainKey(CalendarEntryKind.NOTIFICATION);
    }

    @Test
    @DisplayName("and a month still marks the days it could count")
    void theMonthSurvivesOneBrokenKind() {
        notificationsAreBroken();

        LocalDate today = LocalDate.now();
        CalendarMonthDto month = AsUser.callWithAuthorities(AsUser.SUPERADMIN, SEES_EVERYTHING,
                () -> agenda.month(today.getYear(), today.getMonthValue()));

        assertThat(month).isNotNull();
        assertThat(month.failed()).contains(CalendarEntryKind.NOTIFICATION);
        assertThat(month.days()).as("the other kinds still counted").isNotNull();
    }

    @Test
    @DisplayName("nothing broken means nothing reported as broken")
    void aWorkingDayReportsNoFailures() {
        // The mock answers an empty page rather than throwing.
        when(notificationService.getHistory(anyInt(), anyInt(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(org.springframework.data.domain.Page.empty());

        CalendarDayDto day = AsUser.callWithAuthorities(AsUser.SUPERADMIN, SEES_EVERYTHING,
                () -> agenda.day(LocalDate.now()));

        assertThat(day.failed()).isEmpty();
    }

    @Test
    @DisplayName("the agenda never throws out of a kind that fails")
    void theEndpointDoesNotRaise() {
        notificationsAreBroken();
        LocalDate today = LocalDate.now();

        assertThatCode(() -> AsUser.callWithAuthorities(AsUser.SUPERADMIN, SEES_EVERYTHING, () -> {
            agenda.day(today);
            return agenda.month(today.getYear(), today.getMonthValue());
        })).doesNotThrowAnyException();
    }
}
