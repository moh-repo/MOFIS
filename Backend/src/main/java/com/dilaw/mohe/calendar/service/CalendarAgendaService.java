package com.dilaw.mohe.calendar.service;

import com.dilaw.mohe.alerteventcases.dto.AlertEventCaseDto;
import com.dilaw.mohe.alerteventcases.service.AlertEventCaseService;
import com.dilaw.mohe.approval.service.ApprovalWorkflowService;
import com.dilaw.mohe.calendar.dto.CalendarDayDto;
import com.dilaw.mohe.calendar.dto.CalendarEntryDto;
import com.dilaw.mohe.calendar.dto.CalendarMonthDto;
import com.dilaw.mohe.config.AppTimeZone;
import com.dilaw.mohe.model.MoheQuarter;
import com.dilaw.mohe.model.ScheduledReportEntity;
import com.dilaw.mohe.model.StudentDocument;
import com.dilaw.mohe.notification.dto.NotificationLogDto;
import com.dilaw.mohe.notification.service.NotificationService;
import com.dilaw.mohe.repository.ScheduledReportEntityRepository;
import com.dilaw.mohe.repository.StudentDocumentRepository;
import com.dilaw.mohe.service.MoheQuarterService;
import com.dilaw.mohe.codes.AlertSeverity;
import com.dilaw.mohe.codes.ScheduleStatus;
import com.dilaw.mohe.service.codelist.CodeListService;
import com.dilaw.mohe.systemsettings.service.SysParameterService;
import com.dilaw.mohe.ums.service.CountryScopeService;
import com.dilaw.mohe.ums.service.CountryScopeService.CountryScope;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;

/**
 * What falls on a day, for the calendar in the topbar.
 *
 * <p>The calendar used to be a date picker bound to a field nothing read. It now
 * answers the question the picture of a calendar makes a user ask — <em>what is
 * happening on this day?</em> — from the records the system already keeps: a
 * document about to expire, a case opened, a quarter's money moving, a report due
 * to run, a message sent.
 *
 * <p>Two rules hold everywhere here. Every source is read through the service
 * that already scopes it by country, so the agenda cannot widen anybody's view;
 * and a kind the caller has no authority for is left out altogether rather than
 * shown empty, so what the agenda lists is what the caller can go and act on.
 *
 * <p>Days are the application's days: {@link AppTimeZone} draws the boundaries,
 * not the JVM's default zone, so an agenda for the 30th holds what the reports
 * printed on the 30th.
 */
@Transactional(readOnly = true)
@Service
public class CalendarAgendaService {

    private static final org.slf4j.Logger log =
            org.slf4j.LoggerFactory.getLogger(CalendarAgendaService.class);

    /** Per kind, per day. Enough to recognise what is there; the total says how much more. */
    private static final int ENTRIES_PER_KIND = 5;

    /** The most pending requests the agenda will read in one go (H1). */
    private static final int APPROVAL_SCAN_MAX = 2000;

    /** Granted to administrators in {@code JwtAuthFilter}; passes every page expression. */
    /** R-12: any tier authority (ROLE_SUPER_ADMIN to ROLE_COUNTRY_ADMIN) opens every kind, as ROLE_ADMIN did. */
    private static final String ADMIN_PREFIX = "ROLE_";

    private final AppTimeZone appTimeZone;
    private final CountryScopeService countryScopeService;
    private final CodeListService codeLists;
    private final StudentDocumentRepository documentRepository;
    private final AlertEventCaseService alertEventCaseService;
    private final NotificationService notificationService;
    private final MoheQuarterService quarterService;
    private final ScheduledReportEntityRepository scheduleRepository;
    private final SysParameterService sysParameters;
    private final ApprovalWorkflowService approvalWorkflowService;
    private final MessageSource messages;

    public CalendarAgendaService(AppTimeZone appTimeZone,
                                 CountryScopeService countryScopeService,
                                 CodeListService codeLists,
                                 StudentDocumentRepository documentRepository,
                                 AlertEventCaseService alertEventCaseService,
                                 NotificationService notificationService,
                                 MoheQuarterService quarterService,
                                 ScheduledReportEntityRepository scheduleRepository,
                                 SysParameterService sysParameters,
                                 ApprovalWorkflowService approvalWorkflowService,
                                 MessageSource messages) {
        this.appTimeZone = appTimeZone;
        this.countryScopeService = countryScopeService;
        this.codeLists = codeLists;
        this.documentRepository = documentRepository;
        this.alertEventCaseService = alertEventCaseService;
        this.notificationService = notificationService;
        this.quarterService = quarterService;
        this.scheduleRepository = scheduleRepository;
        this.sysParameters = sysParameters;
        this.approvalWorkflowService = approvalWorkflowService;
        this.messages = messages;
    }

    /** The agenda for one day. */
    public CalendarDayDto day(LocalDate date) {
        Set<String> authorities = currentAuthorities();
        List<CalendarEntryDto> entries = new ArrayList<>();
        Map<String, Long> totals = new LinkedHashMap<>();

        List<String> failed = new ArrayList<>();

        for (String kind : CalendarEntryKind.ALL) {
            if (!permits(authorities, kind)) {
                continue;
            }
            Found found;
            try {
                found = read(kind, date, date);
            } catch (RuntimeException e) {
                // QA C10 / N14: one kind used to take the whole agenda with it, so a
                // single broken query showed the reader an empty, untroubled day. The
                // kind is named in the answer instead and the rest is served.
                log.warn("Calendar: {} could not be read for {}: {}", kind, date, e.toString());
                failed.add(kind);
                continue;
            }
            if (found.total() == 0) {
                continue;
            }
            totals.put(kind, found.total());
            entries.addAll(found.entries());
        }
        return new CalendarDayDto(date, entries, totals, List.copyOf(failed));
    }

    /** How many entries fall on each day of a month, for the marks in the date picker. */
    public CalendarMonthDto month(int year, int month) {
        YearMonth ym = YearMonth.of(year, month);
        Set<String> authorities = currentAuthorities();

        Map<LocalDate, Long> perDay = new LinkedHashMap<>();
        List<String> failed = new ArrayList<>();
        for (String kind : CalendarEntryKind.ALL) {
            if (!permits(authorities, kind)) {
                continue;
            }
            try {
                // Counted by walking the month's entries rather than by a query per day:
                // a month is at most 31 days and this is one read per kind, not 31.
                for (CalendarEntryDto entry : read(kind, ym.atDay(1), ym.atEndOfMonth()).entries()) {
                    LocalDate on = appTimeZone.toLocalDate(entry.at());
                    if (on != null) {
                        perDay.merge(on, 1L, Long::sum);
                    }
                }
            } catch (RuntimeException e) {
                log.warn("Calendar: {} could not be counted for {}-{}: {}", kind, year, month, e.toString());
                failed.add(kind);
            }
        }

        List<CalendarMonthDto.DayCount> days = perDay.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> new CalendarMonthDto.DayCount(e.getKey(), e.getValue()))
                .toList();
        return new CalendarMonthDto(year, month, days, List.copyOf(failed));
    }

    /**
     * The entries of one kind between two dates, and how many there are in total.
     *
     * <p>The month view needs every entry it counts, so the per-kind cap is only
     * applied for a single day. A month of one kind is bounded by the same reads
     * the screens behind it already make.
     */
    private Found read(String kind, LocalDate from, LocalDate to) {
        boolean oneDay = from.equals(to);
        int cap = oneDay ? ENTRIES_PER_KIND : Integer.MAX_VALUE;
        return switch (kind) {
            case CalendarEntryKind.DOCUMENT_EXPIRY -> documentExpiries(from, to, cap);
            case CalendarEntryKind.ALERT_CASE -> alertCases(from, to, cap);
            case CalendarEntryKind.NOTIFICATION -> notifications(from, to, cap);
            case CalendarEntryKind.QUARTER_PAY -> quarters(from, to, cap, true);
            case CalendarEntryKind.QUARTER_RECEIVED -> quarters(from, to, cap, false);
            case CalendarEntryKind.REPORT_RUN -> reportRuns(from, to, cap);
            case CalendarEntryKind.APPROVAL_PENDING -> pendingApprovals(from, to, cap);
            default -> Found.none();
        };
    }

    /**
     * Approval requests raised in the window that are still undecided, read
     * through {@link ApprovalWorkflowService#getAllPending} so the country scope
     * is the pending-approvals screen's own. The open set is small, so it is read
     * whole and filtered here, like the schedules.
     *
     * <p>{@code RequestedAt} is an {@code Instant} (review item A3), so the
     * application's zone draws the day boundaries directly.
     */
    private Found pendingApprovals(LocalDate from, LocalDate to, int cap) {
        String lang = lang();
        Instant start = appTimeZone.startOfDay(from);
        Instant end = appTimeZone.endOfDay(to);
        // H1: this used to ask for every pending request in the system with
        // Pageable.unpaged() and filter the window in memory. The open set is
        // small today, which is exactly the kind of assumption that stops being
        // true quietly. Newest first, bounded by what the agenda can show.
        List<CalendarEntryDto> all = approvalWorkflowService
                .getAllPending(PageRequest.of(0, approvalScanSize(cap), Sort.by(Sort.Direction.DESC, "requestedAt")),
                        null, null)
                .getContent().stream()
                .map(request -> {
                    Instant at = request.getRequestedAt();
                    if (at == null || at.isBefore(start) || at.isAfter(end)) {
                        return null;
                    }
                    // The kind of record by its name in the reader's language (W3-08, B11):
                    // the code was sent and the browser named only the codes it had keys for.
                    return new CalendarEntryDto(
                            CalendarEntryKind.APPROVAL_PENDING,
                            request.getId() == null ? null : request.getId().intValue(),
                            request.getEntityType() == null ? null
                                    : codeLists.label("approval-entity-type", request.getEntityType(), lang),
                            request.getRequesterFullName(),
                            at,
                            null);
                })
                .filter(java.util.Objects::nonNull)
                .sorted(Comparator.comparing(CalendarEntryDto::at).reversed())
                .toList();
        return new Found(all.stream().limit(cap).toList(), all.size());
    }

    private Found documentExpiries(LocalDate from, LocalDate to, int cap) {
        CountryScope scope = countryScopeService.getScope();
        List<Integer> allowed = scope.getAllowedCountryIds();
        if (!scope.isMinistryUser() && allowed.isEmpty()) {
            // IN () is not valid SQL, and a user with no countries sees nothing anyway.
            return Found.none();
        }
        List<Object[]> rows = documentRepository.findExpiringInScope(
                from, to, scope.isMinistryUser(), allowed.isEmpty() ? List.of(-1) : allowed);

        // The same ladder DocumentExpiryScheduler applies at 07:00, read from the
        // same SYS_PARAMETER rows, so the agenda and the notice it later becomes do
        // not disagree about how urgent the same document is.
        LocalDate today = appTimeZone.today();
        int criticalDays = sysParameters.intValue(SysParameterService.ALERTS_DOC_EXPIRY_CRITICAL_DAYS);
        int highDays = sysParameters.intValue(SysParameterService.ALERTS_DOC_EXPIRY_HIGH_DAYS);

        List<CalendarEntryDto> entries = rows.stream().limit(cap).map(row -> {
            StudentDocument document = (StudentDocument) row[0];
            String studentName = (String) row[1];
            long daysLeft = ChronoUnit.DAYS.between(today, document.getExpiryDate());
            String priority = daysLeft <= criticalDays ? AlertSeverity.CRITICAL
                    : (daysLeft <= highDays ? AlertSeverity.HIGH : AlertSeverity.MEDIUM);
            return new CalendarEntryDto(
                    CalendarEntryKind.DOCUMENT_EXPIRY,
                    document.getStudentId(),
                    document.getDocumentName(),
                    studentName,
                    appTimeZone.startOfDay(document.getExpiryDate()),
                    severityOf("alert-severity", priority));
        }).toList();
        return new Found(entries, rows.size());
    }

    private Found alertCases(LocalDate from, LocalDate to, int cap) {
        String lang = lang();
        Page<AlertEventCaseDto> page = alertEventCaseService.getAll(
                null, null, null, null, null, null,
                appTimeZone.startOfDay(from), appTimeZone.endOfDay(to),
                PageRequest.of(0, capped(cap), Sort.by(Sort.Direction.DESC, "createdAt")));

        List<CalendarEntryDto> entries = page.getContent().stream()
                .map(c -> new CalendarEntryDto(
                        CalendarEntryKind.ALERT_CASE,
                        c.getCaseId(),
                        inLanguage(c.getScenarioNameAr(), c.getScenarioNameEn(), c.getScenarioCode(), lang),
                        c.getStudentName(),
                        c.getCreatedAt(),
                        severityOf("alert-severity", c.getSeverity())))
                .toList();
        return new Found(entries, page.getTotalElements());
    }

    private Found notifications(LocalDate from, LocalDate to, int cap) {
        String lang = lang();
        Page<NotificationLogDto> page = notificationService.getHistory(
                0, capped(cap), null, null, null, null, null,
                appTimeZone.startOfDay(from), appTimeZone.endOfDay(to));

        List<CalendarEntryDto> entries = page.getContent().stream()
                .map(sent -> new CalendarEntryDto(
                        CalendarEntryKind.NOTIFICATION,
                        sent.getId() == null ? null : sent.getId().intValue(),
                        sent.getSubject(),
                        sent.getChannel() == null ? null : codeLists.label("notification-channel", sent.getChannel(), lang),
                        sent.getCreatedAt(),
                        severityOf("notification-send-status", sent.getStatus())))
                .toList();
        return new Found(entries, page.getTotalElements());
    }

    /**
     * Quarter dates. {@code payDate} chooses which of the two columns is read —
     * they are separate kinds because they mean different things to the officer
     * reading them: money going out, and money acknowledged as received.
     */
    private Found quarters(LocalDate from, LocalDate to, int cap, boolean payDate) {
        String kind = payDate ? CalendarEntryKind.QUARTER_PAY : CalendarEntryKind.QUARTER_RECEIVED;
        String lang = lang();
        List<CalendarEntryDto> all = quarterService.getAll().stream()
                .map(quarter -> {
                    Instant at = payDate ? quarter.getQuarterPayDate() : quarter.getQuarterRecivedDate();
                    LocalDate on = appTimeZone.toLocalDate(at);
                    if (on == null || on.isBefore(from) || on.isAfter(to)) {
                        return null;
                    }
                    return new CalendarEntryDto(kind, quarter.getId(), quarterName(quarter, lang), null, at, null);
                })
                .filter(java.util.Objects::nonNull)
                .sorted(Comparator.comparing(CalendarEntryDto::at))
                .toList();
        return new Found(all.stream().limit(cap).toList(), all.size());
    }

    private Found reportRuns(LocalDate from, LocalDate to, int cap) {
        String lang = lang();
        Instant start = appTimeZone.startOfDay(from);
        Instant end = appTimeZone.endOfDay(to);

        // Schedules are a settings-sized table and carry no country, so they are
        // read whole and filtered here rather than through a query per day.
        //
        // Only the ones that will actually run: a disabled or paused schedule has
        // a next-run date like any other, and putting it on an agenda of what is
        // happening would promise a run that is not going to happen.
        List<CalendarEntryDto> all = scheduleRepository.findAll().stream()
                .filter(schedule -> Boolean.TRUE.equals(schedule.getIsEnabled())
                        && ScheduleStatus.ACTIVE.equals(schedule.getScheduleStatus()))
                .flatMap(schedule -> runsInWindow(schedule, start, end).stream()
                        .map(at -> new CalendarEntryDto(
                                CalendarEntryKind.REPORT_RUN,
                                schedule.getScheduleId(),
                                schedule.getScheduleName(),
                                frequencyName(schedule.getFrequency(), lang),
                                at,
                                severityOf("run-status", schedule.getLastRunStatus()))))
                .sorted(Comparator.comparing(CalendarEntryDto::at))
                .toList();
        return new Found(all.stream().limit(cap).toList(), all.size());
    }

    /**
     * The runs a schedule has inside the window: the one it is due to make and
     * the one it last made, whichever of them falls in it.
     *
     * <p>Both, not one of them. An earlier version answered "the next run, or
     * failing that the last", which is right for a single day and wrong for a
     * month: over a month-wide window the next run is almost always in range, so
     * the last run was dropped and the month's marks disagreed with the agenda
     * the marked days themselves would show.
     *
     * <p>Two runs on the same calendar day — a schedule that ran this morning and
     * is due again this evening — collapse to one entry, the one still to come,
     * since a day is as fine as the agenda divides time.
     */
    private List<Instant> runsInWindow(ScheduledReportEntity schedule, Instant start, Instant end) {
        List<Instant> runs = new ArrayList<>(2);
        Instant next = schedule.getNextRunDate();
        Instant last = schedule.getLastRunDate();
        if (next != null && !next.isBefore(start) && !next.isAfter(end)) {
            runs.add(next);
        }
        if (last != null && !last.isBefore(start) && !last.isAfter(end)
                && (runs.isEmpty() || !sameDay(runs.get(0), last))) {
            runs.add(last);
        }
        return runs;
    }

    /** DAILY, WEEKLY… by name; the code itself when it has none. */
    private String frequencyName(String code, String lang) {
        if (code == null || code.isBlank()) return null;
        return messages.getMessage("report.frequency." + code.toUpperCase(java.util.Locale.ROOT), null, code,
                java.util.Locale.forLanguageTag(lang));
    }

    private boolean sameDay(Instant one, Instant other) {
        return appTimeZone.toLocalDate(one).equals(appTimeZone.toLocalDate(other));
    }

    private static String quarterName(MoheQuarter quarter, String lang) {
        return inLanguage(quarter.getQuarterName(), quarter.getQuarterNameEn(), String.valueOf(quarter.getId()), lang);
    }

    /** The reader's language, {@code ar} or {@code en}: what the entries' names are given in (W3-07). */
    private static String lang() {
        return "en".equalsIgnoreCase(LocaleContextHolder.getLocale().getLanguage()) ? "en" : "ar";
    }

    /** The name in {@code lang}, then in the other language, then {@code fallback}. */
    private static String inLanguage(String ar, String en, String fallback, String lang) {
        String first = "en".equals(lang) ? en : ar;
        String second = "en".equals(lang) ? ar : en;
        if (first != null && !first.isBlank()) return first;
        if (second != null && !second.isBlank()) return second;
        return fallback;
    }

    /**
     * The PrimeNG severity a code carries in its list. Null in, null out — an
     * entry with no status of its own is drawn plain rather than as "secondary",
     * which {@code CodeListService.severity} would answer for an absent code.
     */
    private String severityOf(String list, String code) {
        return code == null || code.isBlank() ? null : codeLists.severity(list, code);
    }

    /** A page size, from a cap that may be "no cap". */
    /**
     * How many pending requests to read when building the agenda.
     *
     * <p>The window is filtered after the read, so this has to be wider than the
     * cap: taking exactly {@code cap} rows would drop entries inside the window
     * whenever newer requests sit outside it. A month asks for the ceiling.
     */
    private static int approvalScanSize(int cap) {
        return cap == Integer.MAX_VALUE ? APPROVAL_SCAN_MAX : Math.min(APPROVAL_SCAN_MAX, cap * 40);
    }

    private static int capped(int cap) {
        return cap == Integer.MAX_VALUE ? 1000 : cap;
    }

    private static Set<String> currentAuthorities() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth == null ? Set.of()
                : auth.getAuthorities().stream().map(GrantedAuthority::getAuthority).collect(Collectors.toSet());
    }

    private static boolean permits(Set<String> authorities, String kind) {
        return authorities.stream().anyMatch(a -> a.startsWith(ADMIN_PREFIX))
                || CalendarEntryKind.authoritiesFor(kind).stream().anyMatch(authorities::contains);
    }

    /** Entries of one kind, and how many there were before the cap. */
    private record Found(List<CalendarEntryDto> entries, long total) {
        static Found none() {
            return new Found(List.of(), 0);
        }
    }
}
