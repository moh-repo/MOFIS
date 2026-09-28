package com.dilaw.mohe.scope;

import com.dilaw.mohe.controler.ChartDataController;
import com.dilaw.mohe.controler.StatisticsController;
import com.dilaw.mohe.ums.controler.UmsUserController;
import com.dilaw.mohe.ums.model.UmsUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * What a plain signed-in user can reach: their own account and the landing page.
 *
 * <p>The profile page loaded {@code GET /ums/users/{id}} with the caller's own id,
 * and that controller is class-guarded {@code UMS_VIEW}, so every non-administrator
 * got a 403 on their own profile. {@code /users/me} needs only an authenticated
 * caller; {@code /users/{other}} still needs the administrative permission.
 *
 * <p>The landing page's overview sat under {@code REPORT_OTHER}; a user granted the
 * dashboard and nothing else saw a blank page. It now takes {@code DASHBOARD.VIEW},
 * and nothing at all is still refused.
 */
@Tag("integration")
@SpringBootTest
class UsersMeEndpointTest {

    private static final List<String> DASHBOARD_ONLY = List.of("DASHBOARD.VIEW");
    private static final List<String> NOTHING = List.of();

    @Autowired private UmsUserController users;
    @Autowired private ChartDataController charts;
    @Autowired private StatisticsController statistics;

    @BeforeEach
    void currentRequest() {
        // Some security expressions read the current request; give them one.
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/mohe/api/v1/x");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(req));
    }

    @AfterEach
    void clearRequest() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    @DisplayName("/users/me returns the caller with only DASHBOARD.VIEW; /users/{other} is still refused")
    void meIsOpenToAnyAuthenticatedUser() {
        ResponseEntity<UmsUser> me = AsUser.callWithAuthorities(AsUser.JO_ADMIN, DASHBOARD_ONLY, users::getCurrentUser);

        assertThat(me.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(me.getBody()).isNotNull();
        assertThat(me.getBody().getId()).isEqualTo(AsUser.JO_ADMIN);

        assertThatThrownBy(() -> AsUser.callWithAuthorities(AsUser.JO_ADMIN, DASHBOARD_ONLY,
                () -> users.getUserById(AsUser.SA_ADMIN)))
                .as("another account still needs the UMS permission")
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("/users/me is refused without a principal")
    void meNeedsAPrincipal() {
        assertThatThrownBy(users::getCurrentUser)
                .isInstanceOfAny(AccessDeniedException.class,
                        org.springframework.security.authentication.AuthenticationCredentialsNotFoundException.class);
    }

    @Test
    @DisplayName("dashboard overview is 200 with DASHBOARD.VIEW and 403 with nothing")
    void overviewTakesDashboardView() {
        ResponseEntity<?> ok = AsUser.callWithAuthorities(AsUser.JO_ADMIN, DASHBOARD_ONLY,
                () -> charts.getDashboardOverview(null));
        assertThat(ok.getStatusCode()).isEqualTo(HttpStatus.OK);

        assertThatThrownBy(() -> AsUser.callWithAuthorities(AsUser.JO_ADMIN, NOTHING,
                () -> charts.getDashboardOverview(null)))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("the KPI and monthly statistics the dashboard calls take DASHBOARD.VIEW too")
    void dashboardStatisticsTakeDashboardView() {
        assertThat(AsUser.callWithAuthorities(AsUser.JO_ADMIN, DASHBOARD_ONLY,
                () -> statistics.getTableCountsSummary(null)).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(AsUser.callWithAuthorities(AsUser.JO_ADMIN, DASHBOARD_ONLY,
                () -> statistics.getMonthlyStudentStatistics(null)).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(AsUser.callWithAuthorities(AsUser.JO_ADMIN, DASHBOARD_ONLY,
                () -> statistics.getMonthlyUserStatistics(null)).getStatusCode()).isEqualTo(HttpStatus.OK);

        assertThatThrownBy(() -> AsUser.callWithAuthorities(AsUser.JO_ADMIN, NOTHING,
                () -> statistics.getTableCountsSummary(null)))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("the legacy overview keeps its pre-V070 gate: a report holder still reads it")
    void legacyOverviewStillOpenToReportHolders() {
        // The comprehensive-dashboards report calls this; its readers never needed
        // DASHBOARD.VIEW and must not start needing it. The landing page itself moved
        // to DashboardController, which DashboardScopeTest covers.
        ResponseEntity<?> ok = AsUser.callWithAuthorities(AsUser.JO_ADMIN, List.of("REPORT_OTHER.VIEW"),
                () -> charts.getDashboardOverview(null));
        assertThat(ok.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
