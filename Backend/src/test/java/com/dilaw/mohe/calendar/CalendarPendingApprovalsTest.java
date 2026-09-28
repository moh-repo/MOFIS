package com.dilaw.mohe.calendar;

import com.dilaw.mohe.calendar.dto.CalendarDayDto;
import com.dilaw.mohe.calendar.dto.CalendarEntryDto;
import com.dilaw.mohe.calendar.dto.CalendarMonthDto;
import com.dilaw.mohe.calendar.service.CalendarAgendaService;
import com.dilaw.mohe.calendar.service.CalendarEntryKind;
import com.dilaw.mohe.config.AppTimeZone;
import com.dilaw.mohe.config.tenant.TenantContext;
import com.dilaw.mohe.scope.AsUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The agenda's {@code APPROVAL_PENDING} kind: an approval request raised on a day
 * and still undecided is on that day (review round 2, H1; plan W4-10).
 *
 * <p>The same three ways {@code CalendarAgendaTest} guards against for the other
 * kinds, applied to this one: the wrong day (the application's zone draws the
 * boundaries, and {@code RequestedAt} is an Instant since A3), a widened view (the
 * requests are read through the pending-approvals screen's own scoped query, and the
 * kind needs that screen's permission), and a code for a title — the entry is named
 * after its kind of record from the {@code approval-entity-type} list in the reader's
 * language (W3-08), where the browser used to name only the codes it had keys for.
 *
 * <p>The requests are raw rows raised here as SYSTEM, dated on a day far in the
 * future and marked by an entity id nothing else uses; they, their audit rows and
 * anything hanging off them are removed afterwards.
 */
@Tag("integration")
@SpringBootTest
class CalendarPendingApprovalsTest {

    private static final LocalDate THE_DAY = LocalDate.of(2041, 4, 18);
    private static final String MARK = "calendar-approval-test";
    private static final String ENTITY_TYPE = "STUDENT_FINANCE";
    private static final List<String> MAY_SEE_APPROVALS = List.of("APPROVALS.VIEW");

    @Autowired private CalendarAgendaService agenda;
    @Autowired private AppTimeZone appTimeZone;
    @Autowired private JdbcTemplate jdbc;

    private long pendingMidMorning;
    private long inProgressJustAfterMidnight;
    private long pendingJustBeforeMidnight;
    private long decided;

    @BeforeEach
    void raiseRequests() {
        removeRequests();
        Instant midnight = appTimeZone.startOfDay(THE_DAY);
        pendingMidMorning = raise("PENDING", midnight.plusSeconds(10 * 3600));
        // Half an hour into the application's day (Asia/Riyadh, UTC+3): still the day
        // before in UTC and in the JVM's zone here (Cairo), so an agenda that drew its
        // day with either would file it a day early.
        inProgressJustAfterMidnight = raise("IN_PROGRESS", midnight.plusSeconds(30 * 60));
        pendingJustBeforeMidnight = raise("PENDING", appTimeZone.endOfDay(THE_DAY).minusSeconds(60));
        decided = raise("EXECUTED", midnight.plusSeconds(11 * 3600));
    }

    @AfterEach
    void removeRequests() {
        TenantContext.runAsSystem(() -> {
            List<Long> ids = jdbc.queryForList(
                    "SELECT Id FROM ALERTCORE.ApprovalRequests WHERE EntityId = ?", Long.class, MARK);
            for (Long id : ids) {
                jdbc.update("DELETE FROM ALERTCORE.ApprovalSteps WHERE RequestId = ?", id);
                jdbc.update("DELETE FROM ALERTCORE.ApprovalHistory WHERE RequestId = ?", id);
                jdbc.update("DELETE FROM ALERTCORE.ApprovalRequests WHERE Id = ?", id);
                jdbc.update("DELETE FROM AUDITLOG.AuditTrail WHERE TABLE_NAME = 'ApprovalRequests' AND RECORD_ID = ?",
                        String.valueOf(id));
            }
        });
    }

    /**
     * One request for an Egyptian record, raised by the super admin. {@code RequestedAt}
     * is written as its UTC wall-clock time, which is how an Instant is stored
     * ({@code InstantStoredAsUtcTest}); a bound {@code java.sql.Timestamp} would be
     * written in the JVM's zone and land hours away from where this test means it.
     */
    private long raise(String status, Instant requestedAt) {
        String utc = LocalDateTime.ofInstant(requestedAt, ZoneOffset.UTC).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        Long id = TenantContext.runAsSystem(() -> jdbc.queryForObject("""
                INSERT INTO ALERTCORE.ApprovalRequests
                       (ConfigId, RequestType, EntityType, EntityId, RequestData, CurrentLevel, Status,
                        RequestedBy, RequestedAt, CountryId)
                VALUES ((SELECT TOP 1 Id FROM ALERTCORE.ApprovalConfigs WHERE EntityType = ? ORDER BY IsActive DESC, Id),
                        'UPDATE', ?, ?, '{"calendarTest":true}', 1, ?, ?, CAST(? AS DATETIME2), 1);
                SELECT CAST(SCOPE_IDENTITY() AS BIGINT);
                """, Long.class, ENTITY_TYPE, ENTITY_TYPE, MARK, status, AsUser.SUPERADMIN, utc));
        assertThat(id).as("the fixture request must have been raised").isNotNull();
        return id;
    }

    private static List<CalendarEntryDto> pending(CalendarDayDto day) {
        return day.entries().stream().filter(e -> CalendarEntryKind.APPROVAL_PENDING.equals(e.kind())).toList();
    }

    private static List<Integer> pendingIds(CalendarDayDto day) {
        return pending(day).stream().map(CalendarEntryDto::referenceId).toList();
    }

    private static CalendarEntryDto entryFor(CalendarDayDto day, long requestId) {
        return pending(day).stream()
                .filter(e -> e.referenceId() != null && e.referenceId() == requestId)
                .findFirst().orElseThrow(() -> new AssertionError("request " + requestId + " is not on the agenda"));
    }

    private CalendarDayDto dayAs(long userId, List<String> authorities, LocalDate date) {
        return AsUser.callWithAuthorities(userId, authorities, () -> agenda.day(date));
    }

    private static <T> T inLocale(Locale locale, Supplier<T> body) {
        Locale before = LocaleContextHolder.getLocale();
        LocaleContextHolder.setLocale(locale);
        try {
            return body.get();
        } finally {
            LocaleContextHolder.setLocale(before);
        }
    }

    @Test
    @DisplayName("an undecided request raised that day is on that day's agenda, pointing at the request and naming who raised it")
    void undecidedRequestsAreListed() {
        CalendarDayDto day = dayAs(AsUser.SUPERADMIN, MAY_SEE_APPROVALS, THE_DAY);

        assertThat(pendingIds(day)).as("PENDING and IN_PROGRESS are both undecided")
                .contains((int) pendingMidMorning, (int) inProgressJustAfterMidnight, (int) pendingJustBeforeMidnight);
        CalendarEntryDto entry = entryFor(day, pendingMidMorning);
        assertThat(entry.detail()).as("the requester, so the reader knows whose it is").isNotBlank();
        assertThat(entry.at()).isEqualTo(appTimeZone.startOfDay(THE_DAY).plusSeconds(10 * 3600));
        assertThat(day.totals()).containsKey(CalendarEntryKind.APPROVAL_PENDING);
    }

    @Test
    @DisplayName("a decided request is not on the agenda: there is nothing left to act on")
    void decidedRequestsAreNotListed() {
        CalendarDayDto day = dayAs(AsUser.SUPERADMIN, MAY_SEE_APPROVALS, THE_DAY);

        assertThat(pendingIds(day)).doesNotContain((int) decided);
    }

    @Test
    @DisplayName("the title is the kind of record by its name in the reader's language, not its code")
    void titleIsTheEntityTypesName() {
        Map<String, Object> names = jdbc.queryForMap("""
                SELECT TOP 1 EntityTypeAr AS ar, ISNULL(NULLIF(EntityTypeEn, ''), EntityType) AS en
                FROM ALERTCORE.ApprovalConfigs WHERE EntityType = ? ORDER BY IsActive DESC, Id
                """, ENTITY_TYPE);

        CalendarDayDto en = inLocale(Locale.ENGLISH, () -> dayAs(AsUser.SUPERADMIN, MAY_SEE_APPROVALS, THE_DAY));
        CalendarDayDto ar = inLocale(Locale.forLanguageTag("ar"), () -> dayAs(AsUser.SUPERADMIN, MAY_SEE_APPROVALS, THE_DAY));

        assertThat(entryFor(en, pendingMidMorning).title())
                .isEqualTo(names.get("en"))
                .as("V122 gave the list English names").isNotEqualTo(ENTITY_TYPE);
        assertThat(entryFor(ar, pendingMidMorning).title())
                .isEqualTo(names.get("ar"))
                .isNotEqualTo(entryFor(en, pendingMidMorning).title());
    }

    @Test
    @DisplayName("the day runs midnight to midnight in the application's zone: neither edge spills onto a neighbour")
    void daysFollowTheApplicationTimeZone() {
        CalendarDayDto theDay = dayAs(AsUser.SUPERADMIN, MAY_SEE_APPROVALS, THE_DAY);
        CalendarDayDto dayBefore = dayAs(AsUser.SUPERADMIN, MAY_SEE_APPROVALS, THE_DAY.minusDays(1));
        CalendarDayDto nextDay = dayAs(AsUser.SUPERADMIN, MAY_SEE_APPROVALS, THE_DAY.plusDays(1));

        assertThat(pendingIds(theDay)).contains((int) inProgressJustAfterMidnight, (int) pendingJustBeforeMidnight);
        assertThat(pendingIds(dayBefore))
                .as("half past midnight is the day's, although it is the day before in UTC")
                .doesNotContain((int) inProgressJustAfterMidnight);
        assertThat(pendingIds(nextDay)).doesNotContain((int) pendingJustBeforeMidnight);
    }

    @Test
    @DisplayName("the month marks the day, and only that day, for these requests")
    void theMonthMarksTheDay() {
        CalendarMonthDto month = AsUser.callWithAuthorities(AsUser.SUPERADMIN, MAY_SEE_APPROVALS,
                () -> agenda.month(THE_DAY.getYear(), THE_DAY.getMonthValue()));

        assertThat(month.days())
                .filteredOn(d -> d.date().equals(THE_DAY))
                .singleElement()
                .satisfies(d -> assertThat(d.count()).as("the three undecided requests").isGreaterThanOrEqualTo(3L));
        assertThat(month.days()).extracting(CalendarMonthDto.DayCount::date)
                .doesNotContain(THE_DAY.minusDays(1), THE_DAY.plusDays(1));
    }

    @Test
    @DisplayName("without the approvals permission the kind is left out altogether")
    void kindNeedsTheApprovalsPermission() {
        CalendarDayDto day = dayAs(AsUser.SUPERADMIN, List.of("STUDENTS.VIEW", "NOTIFICATIONS.VIEW"), THE_DAY);

        assertThat(pending(day)).isEmpty();
        assertThat(day.totals()).doesNotContainKey(CalendarEntryKind.APPROVAL_PENDING);
    }

    @Test
    @DisplayName("another attaché's officer does not get the requests through the agenda")
    void anotherCountrysOfficerSeesNone() {
        CalendarDayDto day = dayAs(AsUser.JO_ADMIN, MAY_SEE_APPROVALS, THE_DAY);

        assertThat(pendingIds(day))
                .doesNotContain((int) pendingMidMorning, (int) inProgressJustAfterMidnight, (int) pendingJustBeforeMidnight);
    }
}
