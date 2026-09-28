package com.dilaw.mohe.service;

import com.dilaw.mohe.codes.StudyStatusCode;
import com.dilaw.mohe.config.AppTimeZone;
import com.dilaw.mohe.dto.dashboard.MonthPointDto;
import com.dilaw.mohe.repository.StudentDataRepository;
import com.dilaw.mohe.systemsettings.service.SysParameterService;
import com.dilaw.mohe.repository.StudentStudyStatusRepository;
import com.dilaw.mohe.repository.spec.StudentDataSpec;
import com.dilaw.mohe.ums.model.UmsUser;
import com.dilaw.mohe.ums.repository.UmsUserRepository;
import com.dilaw.mohe.ums.repository.spec.StaffCountrySpec;
import com.dilaw.mohe.ums.repository.spec.UmsUserSpecs;
import com.dilaw.mohe.ums.service.CountryScopeService;
import com.dilaw.mohe.ums.service.CountryScopeService.CountryScope;
import com.dilaw.mohe.ums.service.ReportCountryScopeHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.*;

/**
 * Dashboard counters and charts.
 *
 * <p>Failures propagate. Every method here used to catch everything and answer 200 with
 * zeros, {@code success:false} and the raw exception message, so a broken query showed
 * the dashboard an empty but healthy-looking system and leaked driver text to the
 * client. The error is now logged here with its context and rethrown; the global
 * handler turns it into a clean 500 (or 403 for a scope violation), and the dashboard
 * already treats a failed call as "no data". {@code success:true} stays in the bodies
 * because the frontend reads the same shape.
 *
 * <p>Day and month boundaries are taken in the application time zone rather than the
 * JVM default, so "today" means the same thing here as on the reports.
 *
 * <p><b>User counters are scoped since V070 (29 Aug 2026).</b> {@code UMSCORE} sits
 * outside the row-level policy and nothing here filtered it, so a Jordan-only admin
 * read the ministry's total of registered accounts. A country-scoped caller now counts
 * the accounts whose active {@code UserCountries} rows meet their countries (see
 * {@link UmsUserRepository#countActiveInCountries}); a ministry caller keeps the global
 * figure. The {@link UserScope#scoped} flag says which one a body carries.
 */
@Transactional(readOnly = true)
@Service
public class StatisticsService {

    private static final Logger log = LoggerFactory.getLogger(StatisticsService.class);

    @Autowired
    AppTimeZone appTimeZone;

    @Autowired
    StudentDataRepository studentDataRepository;

    @Autowired
    UmsUserRepository umsUserRepository;

    @Autowired
    CountryScopeService countryScopeService;

    @Autowired
    ReportCountryScopeHelper countryScopeHelper;

    @Autowired
    StudentStudyStatusRepository studyStatusRepository;

    @Autowired
    SysParameterService sysParameters;

    private Specification<com.dilaw.mohe.model.StudentDatum> scopedStudents(Integer requestCountryId) {
        countryScopeHelper.ensureScopeOrThrow();
        if (requestCountryId != null) {
            countryScopeService.validateCountryAccess(requestCountryId);
        }
        List<Integer> countryIds = countryScopeHelper.resolveCountryIds(requestCountryId);
        if (!countryScopeHelper.shouldApplyCountryFilter(countryIds)) {
            return StudentDataSpec.countryScope(countryScopeService.getScope());
        }
        List<Integer> ids = countryScopeHelper.safeList(countryIds);
        return (root, query, cb) -> {
            if (ids.isEmpty()) {
                return cb.disjunction();
            }
            return root.get("studyCountryIn").in(ids);
        };
    }

    /**
     * Which accounts a user counter covers: {@code scoped = false} means the ministry
     * figure (every non-deleted account). A country-scoped caller with no explicit
     * country counts what their staff list shows ({@code staffList}, live QA L6 /
     * D12-a); an explicit country counts the accounts assigned to {@code countryIds}.
     */
    public record UserScope(boolean scoped, List<Integer> countryIds, CountryScope staffList) {
        static final UserScope MINISTRY = new UserScope(false, List.of(), null);
    }

    /**
     * The same resolution the student counters use, expressed for {@code UMSCORE}:
     * an explicit {@code countryId} (validated) narrows to that country; otherwise
     * the caller's scope — ministry-wide, or their assigned countries (already
     * narrowed by the {@code X-Country-Id} header when one was sent).
     */
    public UserScope userScope(Integer requestCountryId) {
        countryScopeHelper.ensureScopeOrThrow();
        if (requestCountryId != null) {
            countryScopeService.validateCountryAccess(requestCountryId);
            return new UserScope(true, List.of(requestCountryId), null);
        }
        CountryScope scope = countryScopeService.getScope();
        if (scope.isMinistryUser()) {
            return UserScope.MINISTRY;
        }
        return new UserScope(true, List.copyOf(scope.getAllowedCountryIds()), scope);
    }

    private long countUsers(UserScope users) {
        if (!users.scoped()) {
            return umsUserRepository.countByIsDeletedFalse();
        }
        if (users.staffList() != null) {
            return umsUserRepository.count(staffList(users.staffList()));
        }
        return users.countryIds().isEmpty() ? 0L : umsUserRepository.countActiveInCountries(users.countryIds());
    }

    private long countUsersCreatedAfter(UserScope users, LocalDateTime since) {
        if (!users.scoped()) {
            return umsUserRepository.countByIsDeletedFalseAndCreatedAtAfter(since);
        }
        if (users.staffList() != null) {
            return umsUserRepository.count(staffList(users.staffList())
                    .and((root, query, cb) -> cb.greaterThan(root.<LocalDateTime>get("createdAt"), since)));
        }
        return users.countryIds().isEmpty() ? 0L
                : umsUserRepository.countActiveInCountriesCreatedAfter(users.countryIds(), since);
    }

    /**
     * The accounts the caller's staff list shows (live QA L6, decision D12-a): a
     * country admin's dashboard said 25 users while the list beside it said 27,
     * because the counter took the accounts assigned to any of their countries and
     * the list takes the accounts they may administer, unassigned ones included. The
     * counter now uses the list's own filter, so the two cannot drift again.
     */
    private static Specification<UmsUser> staffList(CountryScope scope) {
        return UmsUserSpecs.notDeleted().and(StaffCountrySpec.administrableBy(scope));
    }

    public Map<String, Object> getOperationsStatistics(Integer requestCountryId) {
        Map<String, Object> stats = new HashMap<>();

        try {
            Specification<com.dilaw.mohe.model.StudentDatum> scope = scopedStudents(requestCountryId);
            UserScope users = userScope(requestCountryId);
            long totalStudents = studentDataRepository.count(scope);
            long totalUsers = countUsers(users);

            LocalDateTime startOfToday = LocalDate.now().atStartOfDay();
            Instant startOfTodayInstant = startOfToday.atZone(appTimeZone.zone()).toInstant();
            long todayStudents = studentDataRepository.count(
                    scope.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("studentMidate"), startOfTodayInstant)));
            long todayUsers = countUsersCreatedAfter(users, startOfToday);

            stats.put("totalStudents", totalStudents);
            stats.put("totalUsers", totalUsers);
            stats.put("todayStudents", todayStudents);
            stats.put("todayUsers", todayUsers);
            stats.put("success", true);

        } catch (RuntimeException e) {
            log.error("Operations statistics failed (countryId={})", requestCountryId, e);
            throw e;
        }

        return stats;
    }

    /**
     * The months of the window ending this month, oldest first, each at zero. The
     * window is the {@code DASHBOARD.MONTHS_WINDOW} parameter (V072), not a constant.
     */
    private LinkedHashMap<YearMonth, Long> monthWindow(LocalDate currentDate) {
        int months = sysParameters.intValue(SysParameterService.DASHBOARD_MONTHS_WINDOW);
        LinkedHashMap<YearMonth, Long> window = new LinkedHashMap<>();
        for (int i = months - 1; i >= 0; i--) {
            window.put(YearMonth.from(currentDate.minusMonths(i)), 0L);
        }
        return window;
    }

    private Instant windowStart(LocalDate currentDate) {
        int months = sysParameters.intValue(SysParameterService.DASHBOARD_MONTHS_WINDOW);
        return appTimeZone.startOfDay(currentDate.minusMonths(months - 1L).withDayOfMonth(1));
    }

    /** Rows are {@code [year, month, count]}; a month outside the window is ignored. */
    private static void fillMonths(List<Object[]> results, Map<YearMonth, Long> window) {
        if (results == null) {
            return;
        }
        for (Object[] row : results) {
            YearMonth ym = YearMonth.of(((Number) row[0]).intValue(), ((Number) row[1]).intValue());
            if (window.containsKey(ym)) {
                window.put(ym, ((Number) row[2]).longValue());
            }
        }
    }

    private static List<MonthPointDto> toPoints(Map<YearMonth, Long> window) {
        List<MonthPointDto> points = new ArrayList<>();
        window.forEach((ym, count) -> points.add(new MonthPointDto(ym.getYear(), ym.getMonthValue(), count)));
        return points;
    }

    /** Monthly student intake over the dashboard window, in the caller's scope. */
    public List<MonthPointDto> studentIntakeByMonth(Integer requestCountryId) {
        LocalDate currentDate = LocalDate.now();
        LinkedHashMap<YearMonth, Long> window = monthWindow(currentDate);
        Instant startDate = windowStart(currentDate);

        countryScopeHelper.ensureScopeOrThrow();
        if (requestCountryId != null) {
            countryScopeService.validateCountryAccess(requestCountryId);
        }
        List<Integer> countryIds = countryScopeHelper.resolveCountryIds(requestCountryId);
        boolean applyCountry = countryScopeHelper.shouldApplyCountryFilter(countryIds);
        // One query for both branches: the parameter switches the country clause
        // off, and the IS_DELETED filter applies either way.
        List<Integer> ids = applyCountry ? countryScopeHelper.safeList(countryIds) : List.of(-1);
        fillMonths(studentDataRepository.findStudentCountsByMonthSinceScoped(startDate, applyCountry ? 1 : 0, ids),
                window);
        return toPoints(window);
    }

    /** Monthly account registrations over the dashboard window, in the caller's scope. */
    public List<MonthPointDto> userRegistrationsByMonth(Integer requestCountryId) {
        LocalDate currentDate = LocalDate.now();
        LinkedHashMap<YearMonth, Long> window = monthWindow(currentDate);
        Instant startDate = windowStart(currentDate);

        UserScope users = userScope(requestCountryId);
        List<Object[]> results;
        if (!users.scoped()) {
            results = umsUserRepository.findUserRegistrationCountsByMonthSince(startDate);
        } else if (users.countryIds().isEmpty()) {
            results = List.of();
        } else {
            results = umsUserRepository.findUserRegistrationCountsByMonthSinceInCountries(startDate, users.countryIds());
        }
        fillMonths(results, window);
        return toPoints(window);
    }

    /**
     * The legacy {@code /statistics/*\/monthly} body for the reports page: labels are
     * {@code yyyy-MM} — data the client can format — no longer English month names.
     */
    private static Map<String, Object> monthlyBody(List<MonthPointDto> points) {
        List<String> labels = new ArrayList<>();
        List<Long> counts = new ArrayList<>();
        for (MonthPointDto p : points) {
            labels.add(String.format(Locale.ROOT, "%04d-%02d", p.year(), p.month()));
            counts.add(p.count());
        }
        Map<String, Object> stats = new HashMap<>();
        stats.put("labels", labels);
        stats.put("data", counts);
        stats.put("points", points);
        stats.put("success", true);
        return stats;
    }

    public Map<String, Object> getMonthlyStudentStatistics(Integer requestCountryId) {
        try {
            return monthlyBody(studentIntakeByMonth(requestCountryId));
        } catch (RuntimeException e) {
            log.error("Monthly student statistics failed (countryId={})", requestCountryId, e);
            throw e;
        }
    }

    public Map<String, Object> getMonthlyUserStatistics(Integer requestCountryId) {
        try {
            return monthlyBody(userRegistrationsByMonth(requestCountryId));
        } catch (RuntimeException e) {
            log.error("Monthly user statistics failed (countryId={})", requestCountryId, e);
            throw e;
        }
    }

    /** Student head-counts for the KPI tiles: total, active (by status CODE), new this month. */
    public record StudentsSummary(long total, long active, long newThisMonth) {}

    public StudentsSummary studentsSummary(Integer requestCountryId) {
        Specification<com.dilaw.mohe.model.StudentDatum> scope = scopedStudents(requestCountryId);
        long totalStudents = studentDataRepository.count(scope);

        // Resolved by CODE rather than the literal 1. The value happens to be
        // right, but the neighbouring assumptions were not — 2 is New, not
        // Graduated — so no report should hardcode a status ID.
        Integer activeStatusId = studyStatusRepository.findFirstByCodeIgnoreCase(StudyStatusCode.ACTIVE)
                .map(com.dilaw.mohe.model.StudentStudyStatus::getId)
                .orElse(null);
        long activeStudents = activeStatusId == null ? 0L : studentDataRepository.count(
                scope.and((root, query, cb) -> cb.equal(root.get("studentStudyStatusId"), activeStatusId)));

        Instant startOfMonth = appTimeZone.startOfDay(LocalDate.now().withDayOfMonth(1));
        long newStudents = studentDataRepository.count(
                scope.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("studentMidate"), startOfMonth)));
        return new StudentsSummary(totalStudents, activeStudents, newStudents);
    }

    /** Account head-counts for the KPI tiles, with the scope they were counted under. */
    public record UsersSummary(long total, long newThisMonth, boolean scoped) {}

    public UsersSummary usersSummary(Integer requestCountryId) {
        UserScope users = userScope(requestCountryId);
        LocalDateTime startOfMonth = LocalDate.now().withDayOfMonth(1).atStartOfDay();
        return new UsersSummary(countUsers(users), countUsersCreatedAfter(users, startOfMonth), users.scoped());
    }

    public Map<String, Object> getTableCountsSummary(Integer requestCountryId) {
        Map<String, Object> summary = new HashMap<>();
        try {
            StudentsSummary students = studentsSummary(requestCountryId);
            UsersSummary users = usersSummary(requestCountryId);

            Map<String, Object> studentStats = new HashMap<>();
            studentStats.put("total", students.total());
            studentStats.put("active", students.active());
            studentStats.put("newThisMonth", students.newThisMonth());

            Map<String, Object> userStats = new HashMap<>();
            userStats.put("total", users.total());
            userStats.put("active", users.total());
            userStats.put("newThisMonth", users.newThisMonth());
            userStats.put("scoped", users.scoped());

            // Both halves are now counted under the same scope, so the sum means
            // something for a country user too.
            Map<String, Object> systemHealth = new HashMap<>();
            systemHealth.put("status", students.total() > 0 && users.total() > 0 ? "HEALTHY" : "WARNING");
            systemHealth.put("totalRecords", students.total() + users.total());
            systemHealth.put("lastUpdated", LocalDate.now().toString());

            summary.put("students", studentStats);
            summary.put("users", userStats);
            summary.put("systemHealth", systemHealth);
            summary.put("success", true);

        } catch (RuntimeException e) {
            log.error("Table counts summary failed (countryId={})", requestCountryId, e);
            throw e;
        }
        return summary;
    }
}
