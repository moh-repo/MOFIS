package com.dilaw.mohe.calendar;

import com.dilaw.mohe.calendar.dto.CalendarDayDto;
import com.dilaw.mohe.calendar.dto.CalendarEntryDto;
import com.dilaw.mohe.calendar.dto.CalendarMonthDto;
import com.dilaw.mohe.calendar.service.CalendarAgendaService;
import com.dilaw.mohe.calendar.service.CalendarEntryKind;
import com.dilaw.mohe.config.AppTimeZone;
import com.dilaw.mohe.model.StudentDatum;
import com.dilaw.mohe.model.ScheduledReportEntity;
import com.dilaw.mohe.model.StudentDocument;
import com.dilaw.mohe.notification.model.NotificationLog;
import com.dilaw.mohe.notification.repository.NotificationLogRepository;
import com.dilaw.mohe.repository.ScheduledReportEntityRepository;
import com.dilaw.mohe.repository.StudentDataRepository;
import com.dilaw.mohe.repository.StudentDocumentRepository;
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
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The day agenda behind the topbar calendar.
 *
 * <p>Three things are worth pinning, and they are the three ways an endpoint
 * that gathers from six places goes wrong. It can widen somebody's country
 * scope, because it reads tables the caller normally reaches through a scoped
 * service. It can offer a row the caller has no permission to follow, because
 * the permission lives on the screen rather than on the data. And it can put an
 * entry on the wrong day, because a day is a time zone's opinion and the JVM's
 * default is rarely the ministry's.
 *
 * <p>The fixture is written and removed here: a document against a student that
 * already exists — {@code STUDENT_DATA} is never written by a test — and a
 * notification under a batch id nothing else uses. Both are dated well into the
 * future so that only this test's rows land on the day under examination.
 */
@Tag("integration")
@SpringBootTest
class CalendarAgendaTest {

    /** Well clear of any real expiry date, so the day holds this test's rows and nothing else. */
    private static final LocalDate THE_DAY = LocalDate.of(2041, 3, 14);
    private static final String FIXTURE = "calendar-agenda-test";
    private static final String DOCUMENT = "calendar-fixture-passport";

    /** The page-level authority that opens the students screen. */
    private static final List<String> MAY_SEE_STUDENTS = List.of("STUDENTS.VIEW");

    @Autowired private CalendarAgendaService agenda;
    @Autowired private StudentDocumentRepository documentRepository;
    @Autowired private StudentDataRepository studentRepository;
    @Autowired private NotificationLogRepository logRepository;
    @Autowired private ScheduledReportEntityRepository scheduleRepository;
    @Autowired private AppTimeZone appTimeZone;
    @Autowired private JdbcTemplate jdbc;

    /** The study country of the student the fixture document hangs off. */
    private int fixtureCountry;

    @BeforeEach
    void writeFixture() {
        removeFixture();

        AsUser.run(AsUser.SUPERADMIN, () -> {
            StudentDatum student = studentRepository.findAll().stream()
                    .filter(s -> s.getStudyCountryIn() != null)
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException(
                            "no student with a study country — nothing to hang a document on"));
            fixtureCountry = student.getStudyCountryIn();

            StudentDocument document = new StudentDocument();
            document.setStudentId(student.getStudentId());
            document.setDocumentName(DOCUMENT);
            document.setDocumentType("PASSPORT");
            document.setFilePath(FIXTURE + "/" + DOCUMENT);
            document.setIsActive(true);
            document.setExpiryDate(THE_DAY);
            documentRepository.save(document);
        });
    }

    @AfterEach
    void removeFixture() {
        jdbc.update("DELETE FROM MOHECORE.STUDENT_DOCUMENTS WHERE FILE_PATH LIKE ?", FIXTURE + "%");
        jdbc.update("DELETE FROM MOHECORE.NOTIFICATION_LOG WHERE BATCH_ID = ?", FIXTURE);
        jdbc.update("DELETE FROM MOHECORE.SCHEDULED_REPORT WHERE CREATED_BY = ?", FIXTURE);
    }

    private static List<String> titlesOfKind(CalendarDayDto day, String kind) {
        return day.entries().stream().filter(e -> kind.equals(e.kind())).map(CalendarEntryDto::title).toList();
    }

    @Test
    @DisplayName("a document expiring that day is on that day's agenda, named after its student")
    void documentExpiringOnTheDayIsListed() {
        CalendarDayDto day = AsUser.callWithAuthorities(AsUser.SUPERADMIN, MAY_SEE_STUDENTS,
                () -> agenda.day(THE_DAY));

        assertThat(titlesOfKind(day, CalendarEntryKind.DOCUMENT_EXPIRY)).contains(DOCUMENT);
        assertThat(day.entries())
                .filteredOn(e -> DOCUMENT.equals(e.title()))
                .allSatisfy(entry -> {
                    assertThat(entry.detail()).as("the entry names the student it belongs to").isNotBlank();
                    assertThat(entry.referenceId()).as("and points at them, so the row can be followed").isNotNull();
                });
        assertThat(day.totals()).containsKey(CalendarEntryKind.DOCUMENT_EXPIRY);
    }

    @Test
    @DisplayName("the expiry query is bounded by the caller's countries")
    void countryScopeIsNotWidened() {
        // STUDENT_DOCUMENTS carries no country of its own, so the scope has to be
        // applied through the student it belongs to. Read as a ministry user in all
        // three cases: Row-Level Security then admits every row and the only thing
        // that varies is the clause under test, which is what should be measured —
        // a country user would be filtered twice and the assertion could not say
        // which guard had done the work.
        AsUser.run(AsUser.SUPERADMIN, () -> {
            int elsewhere = fixtureCountry + 1;

            assertThat(namesFrom(documentRepository.findExpiringInScope(THE_DAY, THE_DAY, true, List.of(-1))))
                    .as("a ministry user sees it whatever the id list says").contains(DOCUMENT);
            assertThat(namesFrom(documentRepository.findExpiringInScope(THE_DAY, THE_DAY, false, List.of(fixtureCountry))))
                    .as("an officer of the student's own country sees it").contains(DOCUMENT);
            assertThat(namesFrom(documentRepository.findExpiringInScope(THE_DAY, THE_DAY, false, List.of(elsewhere))))
                    .as("an officer of country " + elsewhere + " does not").doesNotContain(DOCUMENT);
        });
    }

    private static List<String> namesFrom(List<Object[]> rows) {
        return rows.stream().map(row -> ((StudentDocument) row[0]).getDocumentName()).toList();
    }

    @Test
    @DisplayName("an officer of another country does not get the entry through the agenda either")
    void anotherCountrysOfficerSeesNothingOfIt() {
        // The end-to-end half of the check above, through the service and with the
        // database predicate in force. Jordan is the country the rest of the
        // integration suite uses; the guard states the assumption it rests on
        // rather than letting a coincidence make the assertion vacuous.
        AsUser.run(AsUser.SUPERADMIN, () -> assertThat(fixtureCountry)
                .as("the fixture student would have to be outside Jordan for this to mean anything")
                .isNotEqualTo(1004));

        CalendarDayDto day = AsUser.callWithAuthorities(AsUser.JO_ADMIN, MAY_SEE_STUDENTS,
                () -> agenda.day(THE_DAY));

        assertThat(titlesOfKind(day, CalendarEntryKind.DOCUMENT_EXPIRY)).doesNotContain(DOCUMENT);
    }

    @Test
    @DisplayName("a caller with no permissions gets an empty day, not a permission-free view")
    void aKindTheCallerCannotOpenIsAbsent() {
        CalendarDayDto day = AsUser.callWithAuthorities(AsUser.SUPERADMIN, List.of(),
                () -> agenda.day(THE_DAY));

        assertThat(day.entries())
                .as("the endpoint is open to any signed-in user, so the permission check has to be per kind")
                .isEmpty();
        assertThat(day.totals())
                .as("a kind the caller cannot follow is left out, not reported as zero")
                .isEmpty();
    }

    @Test
    @DisplayName("the month marks the day the document expires on, and only days that have something")
    void theMonthMarksTheDay() {
        CalendarMonthDto month = AsUser.callWithAuthorities(AsUser.SUPERADMIN, MAY_SEE_STUDENTS,
                () -> agenda.month(THE_DAY.getYear(), THE_DAY.getMonthValue()));

        assertThat(month.days())
                .as("the dot under the day comes from this list")
                .anySatisfy(d -> assertThat(d.date()).isEqualTo(THE_DAY));
        assertThat(month.days())
                .extracting(CalendarMonthDto.DayCount::date)
                .allMatch(d -> d.getMonthValue() == THE_DAY.getMonthValue() && d.getYear() == THE_DAY.getYear());
    }

    @Test
    @DisplayName("a day runs midnight to midnight in the application's zone, not the JVM's")
    void daysFollowTheApplicationTimeZone() {
        // A minute before midnight on THE_DAY in the application's zone. Read in
        // UTC — as it would be on a server started there — that instant is on the
        // day before, which is where the agenda would file it if it drew its day
        // boundaries with the JVM default.
        Instant lateOnTheDay = appTimeZone.endOfDay(THE_DAY).minusSeconds(60);
        AsUser.run(AsUser.SUPERADMIN, () -> saveNotification(lateOnTheDay));

        List<String> maySeeNotifications = List.of("NOTIFICATIONS.VIEW");
        CalendarDayDto theDay = AsUser.callWithAuthorities(AsUser.SUPERADMIN, maySeeNotifications,
                () -> agenda.day(THE_DAY));
        CalendarDayDto dayBefore = AsUser.callWithAuthorities(AsUser.SUPERADMIN, maySeeNotifications,
                () -> agenda.day(THE_DAY.minusDays(1)));

        assertThat(titlesOfKind(theDay, CalendarEntryKind.NOTIFICATION))
                .as("a message sent just before midnight belongs to that day")
                .contains(FIXTURE);
        assertThat(titlesOfKind(dayBefore, CalendarEntryKind.NOTIFICATION))
                .as("and not to the one before it")
                .doesNotContain(FIXTURE);
    }

    @Test
    @DisplayName("a month's marks agree with the days they mark, for a schedule with two runs")
    void theMonthAndTheDayAgreeAboutScheduledRuns() {
        // The month is read with one window spanning it, a day with a window of
        // one. A schedule has two dates, and the rule used to be "the next run, or
        // failing that the last" -- right for a day, wrong for a month, where the
        // next run is nearly always in range and the last run was therefore
        // dropped. The month then marked one day while the other day's agenda held
        // an entry the month had not marked.
        LocalDate ranOn = THE_DAY.minusDays(6);
        AsUser.run(AsUser.SUPERADMIN, () -> saveSchedule(
                appTimeZone.startOfDay(THE_DAY).plusSeconds(6 * 3600),
                appTimeZone.startOfDay(ranOn).plusSeconds(6 * 3600)));

        List<String> maySeeReports = List.of("REPORT_OTHER.VIEW");
        CalendarMonthDto month = AsUser.callWithAuthorities(AsUser.SUPERADMIN, maySeeReports,
                () -> agenda.month(THE_DAY.getYear(), THE_DAY.getMonthValue()));

        assertThat(month.days()).extracting(CalendarMonthDto.DayCount::date)
                .as("the month marks the day it is due to run, and the day it last ran")
                .contains(THE_DAY, ranOn);

        for (LocalDate day : List.of(THE_DAY, ranOn)) {
            CalendarDayDto agendaForDay = AsUser.callWithAuthorities(AsUser.SUPERADMIN, maySeeReports,
                    () -> agenda.day(day));
            assertThat(titlesOfKind(agendaForDay, CalendarEntryKind.REPORT_RUN))
                    .as("a day the month marked holds the entry that earned the mark: " + day)
                    .contains(FIXTURE);
        }
    }

    @Test
    @DisplayName("a paused schedule is not on the agenda: it is not going to run")
    void aPausedScheduleIsNotListed() {
        AsUser.run(AsUser.SUPERADMIN, () -> {
            ScheduledReportEntity schedule = saveSchedule(
                    appTimeZone.startOfDay(THE_DAY).plusSeconds(6 * 3600), null);
            schedule.setScheduleStatus("PAUSED");
            scheduleRepository.save(schedule);
        });

        CalendarDayDto day = AsUser.callWithAuthorities(AsUser.SUPERADMIN, List.of("REPORT_OTHER.VIEW"),
                () -> agenda.day(THE_DAY));

        assertThat(titlesOfKind(day, CalendarEntryKind.REPORT_RUN)).doesNotContain(FIXTURE);
    }

    @Test
    @DisplayName("an entry's names are in the reader's language, not codes (W3-07): the channel and the frequency")
    void entryNamesFollowTheReadersLanguage() {
        AsUser.run(AsUser.SUPERADMIN, () -> {
            saveSchedule(appTimeZone.startOfDay(THE_DAY).plusSeconds(6 * 3600), null);
            saveNotification(appTimeZone.startOfDay(THE_DAY).plusSeconds(8 * 3600));
        });
        List<String> maySee = List.of("REPORT_OTHER.VIEW", "NOTIFICATIONS.VIEW");

        CalendarDayDto en = inLocale(Locale.ENGLISH,
                () -> AsUser.callWithAuthorities(AsUser.SUPERADMIN, maySee, () -> agenda.day(THE_DAY)));
        CalendarDayDto ar = inLocale(Locale.forLanguageTag("ar"),
                () -> AsUser.callWithAuthorities(AsUser.SUPERADMIN, maySee, () -> agenda.day(THE_DAY)));

        assertThat(detailOf(en, CalendarEntryKind.REPORT_RUN)).isEqualTo("Weekly");
        assertThat(detailOf(ar, CalendarEntryKind.REPORT_RUN)).isEqualTo("أسبوعي");
        assertThat(detailOf(en, CalendarEntryKind.NOTIFICATION)).isEqualTo("Email");
        assertThat(detailOf(ar, CalendarEntryKind.NOTIFICATION)).isNotEqualTo("EMAIL").isNotEqualTo("Email");
    }

    private static String detailOf(CalendarDayDto day, String kind) {
        return day.entries().stream()
                .filter(e -> kind.equals(e.kind()) && FIXTURE.equals(e.title()))
                .map(CalendarEntryDto::detail)
                .findFirst().orElseThrow(() -> new AssertionError("no " + kind + " fixture entry on the day"));
    }

    private static <T> T inLocale(Locale locale, java.util.function.Supplier<T> body) {
        Locale before = LocaleContextHolder.getLocale();
        LocaleContextHolder.setLocale(locale);
        try {
            return body.get();
        } finally {
            LocaleContextHolder.setLocale(before);
        }
    }

    private ScheduledReportEntity saveSchedule(Instant nextRun, Instant lastRun) {
        ScheduledReportEntity schedule = new ScheduledReportEntity();
        schedule.setScheduleName(FIXTURE);
        schedule.setReportType("STUDENT_LIST");
        schedule.setFrequency("WEEKLY");
        schedule.setNextRunDate(nextRun);
        schedule.setLastRunDate(lastRun);
        schedule.setScheduleStatus("ACTIVE");
        schedule.setIsEnabled(true);
        schedule.setCreatedDate(Instant.now());
        schedule.setCreatedBy(FIXTURE);
        return scheduleRepository.save(schedule);
    }

    /**
     * Written through the repository, not JdbcTemplate: a {@code java.sql.Timestamp}
     * is written as local wall-clock time while Hibernate maps {@code Instant} in
     * UTC, so a raw insert would land the fixture hours away from where this test
     * means to put it — which is the very thing it is measuring.
     *
     * <p>Scope {@code ALL} with no country rows, the shape
     * {@code findScopedHistory} admits for everyone.
     */
    private void saveNotification(Instant at) {
        NotificationLog log = new NotificationLog();
        log.setBatchId(FIXTURE);
        log.setChannel("EMAIL");
        log.setRecipientType("STUDENT");
        log.setScope("ALL");
        log.setSubject(FIXTURE);
        log.setBody("fixture");
        log.setStatus("SENT");
        log.setCreatedAt(at);
        logRepository.save(log);
    }
}
