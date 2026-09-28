package com.dilaw.mohe.scope;

import com.dilaw.mohe.alerteventcases.dto.AlertEventCaseSummaryDto;
import com.dilaw.mohe.controler.ChartDataController;
import com.dilaw.mohe.controler.DashboardController;
import com.dilaw.mohe.dto.SimpleDataPointDto;
import com.dilaw.mohe.dto.dashboard.CodeCountDto;
import com.dilaw.mohe.dto.dashboard.DashboardApprovalsDto;
import com.dilaw.mohe.dto.dashboard.DashboardFinanceDto;
import com.dilaw.mohe.dto.dashboard.DashboardNoticesDto;
import com.dilaw.mohe.dto.dashboard.NoticeDto;
import com.dilaw.mohe.dto.dashboard.DashboardQuarterDto;
import com.dilaw.mohe.dto.dashboard.DashboardSectionsDto;
import com.dilaw.mohe.dto.dashboard.DashboardStudentsDto;
import com.dilaw.mohe.dto.dashboard.DashboardUsersDto;
import com.dilaw.mohe.service.DashboardSection;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * The landing page by country and by section (V070).
 *
 * <p>Two questions, asked of the real accounts and the real database: does every
 * section show a country officer only their own countries' figures, and does each
 * section open only to the authorities meant to open it. The Jordan admin (one
 * country) is compared with the super admin (ministry-wide) and with the
 * eighteen-country admin, which must equal the ministry.
 */
@Tag("integration")
@SpringBootTest
class DashboardScopeTest {

    private static final List<String> ALL = List.of("DASHBOARD.VIEW");
    private static final List<String> NOTHING = List.of();
    private static final int JORDAN = 1004;

    @Autowired private DashboardController dashboard;
    @Autowired private ChartDataController charts;
    @Autowired private JdbcTemplate jdbc;

    @BeforeEach
    void currentRequest() {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/mohe/api/v1/dashboard/x");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(req));
    }

    @AfterEach
    void clearRequest() {
        RequestContextHolder.resetRequestAttributes();
    }

    private <T> T as(long userId, List<String> authorities, Supplier<T> body) {
        return AsUser.callWithAuthorities(userId, authorities, body);
    }

    /** Every section handler, keyed by its section, so the gate tests cannot miss one. */
    private Map<DashboardSection, Supplier<Object>> handlers() {
        Map<DashboardSection, Supplier<Object>> m = new LinkedHashMap<>();
        m.put(DashboardSection.STUDENTS, dashboard::students);
        m.put(DashboardSection.FINANCE, dashboard::finance);
        m.put(DashboardSection.APPROVALS, dashboard::approvals);
        m.put(DashboardSection.ALERTS, dashboard::alertCases);
        m.put(DashboardSection.COUNTRIES, dashboard::countries);
        m.put(DashboardSection.USERS, dashboard::users);
        m.put(DashboardSection.QUARTER, dashboard::quarter);
        m.put(DashboardSection.NOTICES, dashboard::notices);
        assertThat(m.keySet()).containsExactly(DashboardSection.values());
        return m;
    }

    // ── country scope ────────────────────────────────────────────────────────

    @Test
    @DisplayName("students: Jordan sees fewer than the ministry; the eighteen-country admin sees the ministry's figure")
    void studentsAreScoped() {
        DashboardStudentsDto ministry = as(AsUser.SUPERADMIN, ALL, dashboard::students);
        assumeTrue(ministry.total() > 0, "no students on this database");

        DashboardStudentsDto jordan = as(AsUser.JO_ADMIN, ALL, dashboard::students);
        DashboardStudentsDto everyCountry = as(AsUser.ADMIN, ALL, dashboard::students);

        assertThat(jordan.total()).isLessThan(ministry.total());
        assertThat(jordan.total()).isEqualTo(jdbc.queryForObject(
                "SELECT COUNT(*) FROM MOHECORE.STUDENT_DATA WHERE ISNULL(IS_DELETED,0)=0 AND STUDY_COUNTRY_IN = ?",
                Long.class, JORDAN));
        assertThat(jordan.notActive()).isEqualTo(jordan.total() - jordan.active());
        assertThat(sum(jordan.byStatus())).isEqualTo(jordan.total());
        assertThat(everyCountry.total()).isEqualTo(ministry.total());
        assertThat(jordan.monthly()).hasSize(monthsWindow());
    }

    /** The DASHBOARD.MONTHS_WINDOW parameter, read the way the service reads it. */
    private int monthsWindow() {
        return jdbc.queryForObject(
                "SELECT CAST(PARAM_VALUE AS INT) FROM MOHECORE.SYS_PARAMETER WHERE PARAM_KEY = 'DASHBOARD.MONTHS_WINDOW'",
                Integer.class);
    }

    @Test
    @DisplayName("finance and the Wafar balance are the attaché's, not the ministry's")
    void financeAndWafarAreScoped() {
        DashboardFinanceDto ministry = as(AsUser.SUPERADMIN, ALL, dashboard::finance);
        DashboardFinanceDto jordan = as(AsUser.JO_ADMIN, ALL, dashboard::finance);

        assertThat(jordan.totalDisbursed()).isLessThanOrEqualTo(ministry.totalDisbursed());
        assertThat(jordan.wafar()).isNotNull();
        assertThat(jordan.wafar().getTotalDeposits()).isLessThanOrEqualTo(ministry.wafar().getTotalDeposits());
        assertThat(jordan.wafar().getTransactionCount()).isLessThanOrEqualTo(ministry.wafar().getTransactionCount());
    }

    @Test
    @DisplayName("users: a country admin counts the accounts of their countries; the ministry counts every account")
    void usersKpiFollowsUserCountries() {
        DashboardUsersDto jordan = as(AsUser.JO_ADMIN, ALL, dashboard::users);
        DashboardUsersDto ministry = as(AsUser.SUPERADMIN, ALL, dashboard::users);

        Long jordanAccounts = jdbc.queryForObject(
                "SELECT COUNT(DISTINCT u.Id) FROM UMSCORE.Users u JOIN UMSCORE.UserCountries uc ON uc.UserId = u.Id "
                        + "WHERE u.IsDeleted = 0 AND uc.IsActive = 1 AND uc.CountryId = ?",
                Long.class, JORDAN);
        Long allAccounts = jdbc.queryForObject("SELECT COUNT(*) FROM UMSCORE.Users WHERE IsDeleted = 0", Long.class);

        assertThat(jordan.scoped()).isTrue();
        assertThat(jordan.total()).isEqualTo(jordanAccounts);
        assertThat(ministry.scoped()).isFalse();
        assertThat(ministry.total()).isEqualTo(allAccounts);
        assertThat(jordan.total()).isLessThan(ministry.total());
        assertThat(jordan.monthly()).hasSize(monthsWindow());
    }

    @Test
    @DisplayName("approvals and alert cases are counted in scope, and the breakdown adds up")
    void approvalsAndAlertCasesAreScoped() {
        DashboardApprovalsDto ministry = as(AsUser.SUPERADMIN, ALL, dashboard::approvals);
        DashboardApprovalsDto jordan = as(AsUser.JO_ADMIN, ALL, dashboard::approvals);
        assertThat(jordan.pendingTotal()).isLessThanOrEqualTo(ministry.pendingTotal());
        assertThat(sum(ministry.byEntityType())).isEqualTo(ministry.pendingTotal());
        assertThat(sum(jordan.byEntityType())).isEqualTo(jordan.pendingTotal());
        assertThat(jordan.myPending()).isLessThanOrEqualTo(jordan.pendingTotal());

        AlertEventCaseSummaryDto ministryCases = as(AsUser.SUPERADMIN, ALL, dashboard::alertCases);
        AlertEventCaseSummaryDto jordanCases = as(AsUser.JO_ADMIN, ALL, dashboard::alertCases);
        assertThat(jordanCases.getTotalAll()).isLessThanOrEqualTo(ministryCases.getTotalAll());
    }

    @Test
    @DisplayName("the quarter shown to Jordan is Jordan's or a global one; countries are Jordan alone")
    void quarterAndCountriesAreInScope() {
        DashboardQuarterDto jordan = as(AsUser.JO_ADMIN, ALL, dashboard::quarter);
        if (jordan.present()) {
            assertThat(jordan.countryId()).satisfiesAnyOf(
                    c -> assertThat(c).isNull(),
                    c -> assertThat(c).isEqualTo(JORDAN));
        }
        DashboardQuarterDto ministry = as(AsUser.SUPERADMIN, ALL, dashboard::quarter);
        assertThat(ministry).isNotNull();

        List<SimpleDataPointDto> countries = as(AsUser.JO_ADMIN, ALL, dashboard::countries);
        assertThat(countries).hasSizeLessThanOrEqualTo(1);
        List<SimpleDataPointDto> all = as(AsUser.SUPERADMIN, ALL, dashboard::countries);
        assertThat(all).hasSizeGreaterThanOrEqualTo(countries.size());
    }

    // ── gates ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("without any dashboard code every section is refused and /sections is empty")
    void everySectionRefusesWithoutADashboardCode() {
        for (Map.Entry<DashboardSection, Supplier<Object>> h : handlers().entrySet()) {
            assertThatThrownBy(() -> as(AsUser.JO_ADMIN, NOTHING, h.getValue()))
                    .as(h.getKey().name())
                    .isInstanceOf(AccessDeniedException.class);
        }
        DashboardSectionsDto none = as(AsUser.JO_ADMIN, NOTHING, dashboard::sections);
        assertThat(none.sections()).isEmpty();
        assertThat(none.all()).isFalse();
    }

    @Test
    @DisplayName("one section code opens that section only, and /sections says exactly that")
    void singleSectionCodeOpensOnlyThatSection() {
        List<String> financeOnly = List.of(DashboardSection.FINANCE.viewAuthority());
        for (Map.Entry<DashboardSection, Supplier<Object>> h : handlers().entrySet()) {
            if (h.getKey() == DashboardSection.FINANCE) {
                assertThat(as(AsUser.JO_ADMIN, financeOnly, h.getValue())).isNotNull();
            } else {
                assertThatThrownBy(() -> as(AsUser.JO_ADMIN, financeOnly, h.getValue()))
                        .as(h.getKey().name())
                        .isInstanceOf(AccessDeniedException.class);
            }
        }
        DashboardSectionsDto sections = as(AsUser.JO_ADMIN, financeOnly, dashboard::sections);
        assertThat(sections.sections()).containsExactly(DashboardSection.FINANCE.code());
        assertThat(sections.all()).isFalse();
    }

    @Test
    @DisplayName("DASHBOARD.VIEW opens every section")
    void dashboardViewOpensEverything() {
        for (Map.Entry<DashboardSection, Supplier<Object>> h : handlers().entrySet()) {
            assertThat(as(AsUser.JO_ADMIN, ALL, h.getValue())).as(h.getKey().name()).isNotNull();
        }
        DashboardSectionsDto sections = as(AsUser.JO_ADMIN, ALL, dashboard::sections);
        List<String> expected = new ArrayList<>();
        for (DashboardSection s : DashboardSection.values()) {
            expected.add(s.code());
        }
        assertThat(sections.sections()).containsExactlyElementsOf(expected);
        assertThat(sections.all()).isTrue();
    }

    @Test
    @DisplayName("the report codes no longer open the landing page, but still open the legacy overview")
    void reportCodesNoLongerOpenTheDashboard() {
        List<String> reportOnly = List.of("REPORT_OTHER.VIEW", "REPORTS.VIEW", "UMS_ADMIN.VIEW");
        assertThatThrownBy(() -> as(AsUser.JO_ADMIN, reportOnly, dashboard::students))
                .isInstanceOf(AccessDeniedException.class);
        assertThat(as(AsUser.JO_ADMIN, reportOnly, dashboard::sections).sections()).isEmpty();
        assertThat(as(AsUser.JO_ADMIN, reportOnly, () -> charts.getDashboardOverview(null)).getStatusCode().is2xxSuccessful())
                .isTrue();
    }

    @Test
    @DisplayName("every DashboardSection has a controller handler gated by its own authority")
    void sectionEnumMatchesControllerGuards() {
        for (DashboardSection section : DashboardSection.values()) {
            boolean found = false;
            for (Method m : DashboardController.class.getDeclaredMethods()) {
                PreAuthorize gate = m.getAnnotation(PreAuthorize.class);
                if (gate != null && m.isAnnotationPresent(GetMapping.class)
                        && gate.value().contains("'" + section.viewAuthority() + "'")
                        && gate.value().contains("'" + DashboardSection.ALL_AUTHORITY + "'")) {
                    found = true;
                }
            }
            assertThat(found).as("handler gated by %s", section.viewAuthority()).isTrue();
        }
    }

    // ── the database owns the catalogue (V072–V076) ──────────────────────────

    @Test
    @DisplayName("the alert-case legend is exactly the active rows of LU_ALERT_SEVERITY / LU_ALERT_STATUS, in their order")
    void legendRowsAreTheCodeLists() {
        AlertEventCaseSummaryDto cases = as(AsUser.SUPERADMIN, ALL, dashboard::alertCases);
        List<String> severities = jdbc.queryForList(
                "SELECT CODE FROM MOHECORE.LU_ALERT_SEVERITY WHERE IS_DELETED = 0 ORDER BY SORT_ORDER, ID", String.class);
        List<String> statuses = jdbc.queryForList(
                "SELECT CODE FROM MOHECORE.LU_ALERT_STATUS WHERE IS_DELETED = 0 ORDER BY SORT_ORDER, ID", String.class);

        assertThat(cases.getBySeverity()).extracting(AlertEventCaseSummaryDto.CodeCount::code)
                .containsExactlyElementsOf(severities);
        assertThat(cases.getByStatus()).extracting(AlertEventCaseSummaryDto.CodeCount::code)
                .containsExactlyElementsOf(statuses);
        assertThat(cases.getBySeverity()).allSatisfy(row -> {
            assertThat(row.nameAr()).isNotBlank();
            assertThat(row.nameEn()).isNotBlank();
            assertThat(row.uiSeverity()).isIn("success", "info", "warn", "danger", "secondary", "contrast");
        });
        long openBySeverity = cases.getBySeverity().stream().mapToLong(AlertEventCaseSummaryDto.CodeCount::count).sum();
        assertThat(openBySeverity).isEqualTo(cases.getTotalOpen());
    }

    @Test
    @DisplayName("approvals by type carry the names of ApprovalConfigs; statuses by name, not by code, on the donut")
    void breakdownsCarryNamesFromTheTables() {
        DashboardApprovalsDto approvals = as(AsUser.SUPERADMIN, ALL, dashboard::approvals);
        for (CodeCountDto row : approvals.byEntityType()) {
            if (row.code() != null) {
                Integer configured = jdbc.queryForObject(
                        "SELECT COUNT(*) FROM ALERTCORE.ApprovalConfigs WHERE EntityType = ?", Integer.class, row.code());
                if (configured != null && configured > 0) {
                    assertThat(row.nameAr()).as("Arabic name of %s", row.code()).isNotBlank();
                    assertThat(row.nameEn()).as("English name of %s", row.code()).isNotBlank();
                }
            }
        }

        DashboardStudentsDto students = as(AsUser.SUPERADMIN, ALL, dashboard::students);
        for (CodeCountDto row : students.byStatus()) {
            if (row.code() != null) {
                assertThat(row.nameAr()).as("Arabic name of status %s", row.code()).isNotBlank();
                assertThat(row.uiColor()).as("colour of status %s (STUDENT_STUDY_STATUS.UI_COLOR)", row.code())
                        .matches("#[0-9A-Fa-f]{6}");
            }
        }
    }

    @Test
    @DisplayName("monthly series are (year, month) pairs over the DASHBOARD.MONTHS_WINDOW parameter — change it and the charts follow")
    void monthlyWindowFollowsTheParameter() {
        String original = jdbc.queryForObject(
                "SELECT PARAM_VALUE FROM MOHECORE.SYS_PARAMETER WHERE PARAM_KEY = 'DASHBOARD.MONTHS_WINDOW'", String.class);
        try {
            jdbc.update("UPDATE MOHECORE.SYS_PARAMETER SET PARAM_VALUE = '3' WHERE PARAM_KEY = 'DASHBOARD.MONTHS_WINDOW'");
            sysParameters.update("DASHBOARD.MONTHS_WINDOW", "3", "test"); // evicts the cache
            DashboardStudentsDto students = as(AsUser.SUPERADMIN, ALL, dashboard::students);
            DashboardUsersDto users = as(AsUser.SUPERADMIN, ALL, dashboard::users);
            assertThat(students.monthly()).hasSize(3);
            assertThat(users.monthly()).hasSize(3);
            assertThat(students.monthly()).allSatisfy(p -> {
                assertThat(p.month()).isBetween(1, 12);
                assertThat(p.year()).isGreaterThan(2000);
            });
        } finally {
            sysParameters.update("DASHBOARD.MONTHS_WINDOW", original, "test");
        }
    }

    @Test
    @DisplayName("a notice's type, priority and status come back resolved through the code lists, never defaulted")
    void noticesAreResolvedThroughTheCodeLists() {
        DashboardNoticesDto notices = as(AsUser.SUPERADMIN, ALL, dashboard::notices);
        assertThat(notices.countrySpecific()).isFalse();
        List<NoticeDto> all = new ArrayList<>(notices.today());
        all.addAll(notices.yesterday());
        for (NoticeDto n : all) {
            if (n.priority() != null) {
                assertThat(n.priority().nameAr()).isNotBlank();
            }
            if (n.type() != null) {
                assertThat(n.type().nameAr()).isNotBlank();
            }
        }
    }

    @Test
    @DisplayName("the delegation-year chart stops at the ministry's newest quarter year and follows the window parameter")
    void delegationYearsEndAtTheMinistryCalendar() {
        // STUDENT_DELEGATION_DECISION_YEAR is typed in by hand, and the dev data
        // carried a tail of future years — one student in each of 2026 to 2042.
        // Asking for the six highest years returned that tail and nothing else;
        // the axis now ends at the newest quarter the ministry has opened.
        Integer newestQuarterYear = jdbc.queryForObject(
                "SELECT MAX(QUARTER_YEAR) FROM MOHECORE.MOHE_QUARTER", Integer.class);
        assertThat(newestQuarterYear).as("the dev calendar has quarters").isNotNull();

        // The seventeen future rows this was written against were corrected on the
        // development database on 29 August 2026, so the bound may now hold with
        // nothing to exclude. The assertion stays: it is the rule, and the next
        // hand-typed 2042 has to meet it.

        List<SimpleDataPointDto> points = as(AsUser.SUPERADMIN, ALL, dashboard::students).byDelegationYear();
        assertThat(points).isNotEmpty();

        int window = sysParameters.intValue("DASHBOARD.YEARS_WINDOW");
        assertThat(points).hasSizeLessThanOrEqualTo(window);

        List<Integer> years = points.stream().map(p -> Integer.parseInt(p.getLabel())).toList();
        assertThat(years).allSatisfy(y -> assertThat(y).isLessThanOrEqualTo(newestQuarterYear));
        assertThat(years).as("oldest first, so the axis reads left to right").isSorted();

        // The window holds the most recent years that exist within the bound —
        // not the highest values in the column, which is what it used to show.
        List<Integer> expected = jdbc.queryForList(
                "SELECT TOP (?) TRY_CAST(STUDENT_DELEGATION_DECISION_YEAR AS INT) AS y "
                        + "FROM MOHECORE.STUDENT_DATA WHERE ISNULL(IS_DELETED, 0) = 0 "
                        + "AND TRY_CAST(STUDENT_DELEGATION_DECISION_YEAR AS INT) IS NOT NULL "
                        + "AND TRY_CAST(STUDENT_DELEGATION_DECISION_YEAR AS INT) <= ? "
                        + "GROUP BY TRY_CAST(STUDENT_DELEGATION_DECISION_YEAR AS INT) ORDER BY 1 DESC",
                Integer.class, window, newestQuarterYear);
        assertThat(years).containsExactlyElementsOf(expected.reversed());

        // Every bar is the count STUDENT_DATA holds for that year.
        for (SimpleDataPointDto point : points) {
            Integer counted = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM MOHECORE.STUDENT_DATA WHERE ISNULL(IS_DELETED, 0) = 0 "
                            + "AND TRY_CAST(STUDENT_DELEGATION_DECISION_YEAR AS INT) = ?",
                    Integer.class, Integer.parseInt(point.getLabel()));
            assertThat(point.getValue()).as("students delegated in %s", point.getLabel())
                    .isEqualTo(counted.doubleValue());
        }
    }

    @Autowired private com.dilaw.mohe.systemsettings.service.SysParameterService sysParameters;

    private static long sum(List<CodeCountDto> rows) {
        long total = 0;
        for (CodeCountDto r : rows) {
            total += (long) r.count();
        }
        return total;
    }
}
