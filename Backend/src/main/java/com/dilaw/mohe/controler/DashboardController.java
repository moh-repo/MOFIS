package com.dilaw.mohe.controler;

import com.dilaw.mohe.alerteventcases.dto.AlertEventCaseSummaryDto;
import com.dilaw.mohe.config.MoheSecurityExpressions;
import com.dilaw.mohe.dto.SimpleDataPointDto;
import com.dilaw.mohe.dto.dashboard.DashboardApprovalsDto;
import com.dilaw.mohe.dto.dashboard.DashboardFinanceDto;
import com.dilaw.mohe.dto.dashboard.DashboardNoticesDto;
import com.dilaw.mohe.dto.dashboard.DashboardQuarterDto;
import com.dilaw.mohe.dto.dashboard.DashboardSectionsDto;
import com.dilaw.mohe.dto.dashboard.DashboardStudentsDto;
import com.dilaw.mohe.dto.dashboard.DashboardUsersDto;
import com.dilaw.mohe.service.DashboardService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * The landing page, one endpoint per section (V070, 29 Aug 2026).
 *
 * <p>Each section has its own gate — {@code DASHBOARD.VIEW} or that section's
 * {@code DASHBOARD_<SECTION>.VIEW} — so a role or a user can be given the whole
 * page, one section, or several. {@code GET /sections} says which ones the
 * caller's authorities open, so the page renders exactly what the other handlers
 * will serve.
 *
 * <p><b>No {@code countryId} parameter, by design.</b> The country selection is
 * the {@code X-Country-Id} header, which {@code CountryScopeService} narrows the
 * caller's scope by and every composed service honours; a query parameter would
 * be understood by some sections and not others, and the frontend's interceptor
 * stops sending the header when one is present.
 */
@RestController
@RequestMapping("api/v1/dashboard")
@PreAuthorize("isAuthenticated()")
public class DashboardController {

    private final DashboardService service;

    public DashboardController(DashboardService service) {
        this.service = service;
    }

    @GetMapping("/sections")
    public DashboardSectionsDto sections() {
        return service.visibleSections();
    }

    @GetMapping("/students")
    @PreAuthorize(MoheSecurityExpressions.DASHBOARD_STUDENTS)
    public DashboardStudentsDto students() {
        return service.students();
    }

    @GetMapping("/finance")
    @PreAuthorize(MoheSecurityExpressions.DASHBOARD_FINANCE)
    public DashboardFinanceDto finance() {
        return service.finance();
    }

    @GetMapping("/approvals")
    @PreAuthorize(MoheSecurityExpressions.DASHBOARD_APPROVALS)
    public DashboardApprovalsDto approvals() {
        return service.approvals();
    }

    @GetMapping("/alert-cases")
    @PreAuthorize(MoheSecurityExpressions.DASHBOARD_ALERTS)
    public AlertEventCaseSummaryDto alertCases() {
        return service.alertCases();
    }

    @GetMapping("/countries")
    @PreAuthorize(MoheSecurityExpressions.DASHBOARD_COUNTRIES)
    public List<SimpleDataPointDto> countries() {
        return service.countries();
    }

    @GetMapping("/users")
    @PreAuthorize(MoheSecurityExpressions.DASHBOARD_USERS)
    public DashboardUsersDto users() {
        return service.users();
    }

    @GetMapping("/quarter")
    @PreAuthorize(MoheSecurityExpressions.DASHBOARD_QUARTER)
    public DashboardQuarterDto quarter() {
        return service.quarter();
    }

    @GetMapping("/notices")
    @PreAuthorize(MoheSecurityExpressions.DASHBOARD_NOTICES)
    public DashboardNoticesDto notices() {
        return service.notices();
    }
}
