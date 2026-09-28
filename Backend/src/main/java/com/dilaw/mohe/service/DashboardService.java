package com.dilaw.mohe.service;

import com.dilaw.mohe.alerteventcases.dto.AlertEventCaseSummaryDto;
import com.dilaw.mohe.alerteventcases.service.AlertEventCaseService;
import com.dilaw.mohe.approval.service.ApprovalWorkflowService;
import com.dilaw.mohe.config.SecurityRequestUtils;
import com.dilaw.mohe.dto.SimpleDataPointDto;
import com.dilaw.mohe.dto.dashboard.CodeCountDto;
import com.dilaw.mohe.dto.dashboard.NoticeDto;
import com.dilaw.mohe.model.Alert;
import com.dilaw.mohe.service.codelist.CodeListService;
import com.dilaw.mohe.dto.dashboard.DashboardApprovalsDto;
import com.dilaw.mohe.dto.dashboard.DashboardFinanceDto;
import com.dilaw.mohe.dto.dashboard.DashboardNoticesDto;
import com.dilaw.mohe.dto.dashboard.DashboardQuarterDto;
import com.dilaw.mohe.dto.dashboard.DashboardSectionsDto;
import com.dilaw.mohe.dto.dashboard.DashboardStudentsDto;
import com.dilaw.mohe.dto.dashboard.DashboardUsersDto;
import com.dilaw.mohe.wafar.service.WafarBalanceService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The landing page, one section at a time (V070, 29 Aug 2026).
 *
 * <p>Every figure is read through a service that already resolves the caller's
 * country scope from {@code CountryScopeService.getScope()} — student and finance
 * totals through {@link ChartDataService}'s {@code IN (…)} clauses on top of the
 * row-level policy, Wafar through {@link WafarBalanceService}, approvals and alert
 * cases through their own scoped queries, the quarter through
 * {@link MoheQuarterService}, and (new here) the user counters through
 * {@link StatisticsService#userScope}. Nothing here takes a {@code countryId}: the
 * one country-selection mechanism every one of those services understands is the
 * {@code X-Country-Id} header, which {@code CountryScopeService} folds into the
 * scope, and a query parameter would make the frontend's interceptor drop that
 * header. So the header is the contract, and {@code null} is passed down.
 *
 * <p>The system notices are the one unscoped section — {@code MOHE_ALERTS.alerts}
 * has no country column — and their body says so.
 */
@Service
@Transactional(readOnly = true)
public class DashboardService {

    private final ChartDataService chartDataService;
    private final StatisticsService statisticsService;
    private final WafarBalanceService wafarBalanceService;
    private final MoheQuarterService moheQuarterService;
    private final ApprovalWorkflowService approvalWorkflowService;
    private final AlertEventCaseService alertEventCaseService;
    private final AlertService alertService;
    private final CodeListService codeLists;

    public DashboardService(ChartDataService chartDataService, StatisticsService statisticsService,
            WafarBalanceService wafarBalanceService, MoheQuarterService moheQuarterService,
            ApprovalWorkflowService approvalWorkflowService, AlertEventCaseService alertEventCaseService,
            AlertService alertService, CodeListService codeLists) {
        this.chartDataService = chartDataService;
        this.statisticsService = statisticsService;
        this.wafarBalanceService = wafarBalanceService;
        this.moheQuarterService = moheQuarterService;
        this.approvalWorkflowService = approvalWorkflowService;
        this.alertEventCaseService = alertEventCaseService;
        this.alertService = alertService;
        this.codeLists = codeLists;
    }

    /**
     * The sections the caller's authorities open, from the security context (not
     * the token), so a test that signs in through {@code AsUser} and a real request
     * are judged the same way. Administrators and {@code DASHBOARD.VIEW} holders
     * get every section.
     */
    public DashboardSectionsDto visibleSections() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Set<String> authorities = auth == null ? Set.of()
                : auth.getAuthorities().stream().map(GrantedAuthority::getAuthority).collect(Collectors.toSet());
        // R-12: the tier authorities replace ROLE_ADMIN; administrators of every tier see every section (V071).
        boolean all = authorities.contains(DashboardSection.ALL_AUTHORITY)
                || authorities.stream().anyMatch(a -> a.startsWith("ROLE_"));
        List<String> codes = new ArrayList<>();
        for (DashboardSection section : DashboardSection.values()) {
            if (all || authorities.contains(section.viewAuthority())) {
                codes.add(section.code());
            }
        }
        return new DashboardSectionsDto(codes, all);
    }

    public DashboardStudentsDto students() {
        ChartDataService.StudentTotals totals = chartDataService.studentTotals(null);
        StatisticsService.StudentsSummary summary = statisticsService.studentsSummary(null);
        return new DashboardStudentsDto(
                totals.total(), totals.active(), totals.newStatus(), totals.suspended(),
                Math.max(0L, totals.total() - totals.active()),
                summary.newThisMonth(),
                statisticsService.studentIntakeByMonth(null),
                chartDataService.enrollmentTrend(null),
                chartDataService.studentsByStatusRows(null));
    }

    public DashboardFinanceDto finance() {
        ChartDataService.FinanceTotals totals = chartDataService.financeTotals(null);
        return new DashboardFinanceDto(totals.totalDisbursed(), totals.averageDisbursement(),
                chartDataService.financialTrend(null), wafarBalanceService.getSummary());
    }

    public DashboardApprovalsDto approvals() {
        long pending = approvalWorkflowService.countPending();
        // Each entity type resolved through ALERTCORE.ApprovalConfigs (names in both
        // languages); a null key is a request whose entity type is not configured.
        List<CodeCountDto> byType = new ArrayList<>();
        int i = 0;
        for (Map.Entry<String, Long> e : approvalWorkflowService.countPendingByEntityType().entrySet()) {
            int order = i++;
            double count = e.getValue().doubleValue();
            byType.add(codeLists.find("approval-entity-type", e.getKey())
                    .map(r -> CodeCountDto.of(r, count))
                    .orElse(new CodeCountDto(e.getKey(), null, null, null, null, null, order, count)));
        }
        long mine = SecurityRequestUtils.currentUserId()
                .map(approvalWorkflowService::countMyOpenRequests)
                .orElse(0L);
        return new DashboardApprovalsDto(pending, byType, mine);
    }

    public AlertEventCaseSummaryDto alertCases() {
        return alertEventCaseService.getSummary();
    }

    public List<SimpleDataPointDto> countries() {
        return chartDataService.topCountries(null);
    }

    public DashboardUsersDto users() {
        StatisticsService.UsersSummary summary = statisticsService.usersSummary(null);
        return new DashboardUsersDto(summary.total(), summary.newThisMonth(),
                statisticsService.userRegistrationsByMonth(null), summary.scoped());
    }

    public DashboardQuarterDto quarter() {
        return moheQuarterService.getCurrent().map(DashboardQuarterDto::of).orElse(DashboardQuarterDto.none());
    }

    public DashboardNoticesDto notices() {
        return new DashboardNoticesDto(
                alertService.getTodayAlerts().stream().map(this::notice).toList(),
                alertService.getYesterdayAlerts().stream().map(this::notice).toList(),
                false);
    }

    private NoticeDto notice(Alert a) {
        return NoticeDto.of(a,
                NoticeDto.CodeRef.of(a.getAlertType(), codeLists.find("alert-type", a.getAlertType())),
                NoticeDto.CodeRef.of(a.getAlertPriority(), codeLists.find("alert-severity", a.getAlertPriority())),
                NoticeDto.CodeRef.of(a.getAlertStatus(), codeLists.find("notice-status", a.getAlertStatus())));
    }
}
