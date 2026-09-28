package com.dilaw.mohe.controler;

import com.dilaw.mohe.config.MoheSecurityExpressions;
import com.dilaw.mohe.service.StatisticsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/statistics")
@PreAuthorize(MoheSecurityExpressions.REPORT_OTHER)
public class StatisticsController {

    @Autowired
    private StatisticsService service;

    @GetMapping("/operations")
    public ResponseEntity<Map<String, Object>> getOperationsStatistics(
            @RequestParam(required = false) Integer countryId) {
        return ResponseEntity.ok(service.getOperationsStatistics(countryId));
    }

    /*
     * The three handlers below fed the landing page until V070 (29 Aug 2026); the
     * page now reads DashboardController, one endpoint per section. They stay for
     * anything else that reads them, under the wider pre-V070 gate. Since V070 the
     * user counters they return are scoped to the caller's countries (D3) like
     * the student counters always were.
     */

    @GetMapping({"/monthly-students", "/students/monthly"})
    @PreAuthorize(MoheSecurityExpressions.DASHBOARD_OR_REPORT)
    public ResponseEntity<Map<String, Object>> getMonthlyStudentStatistics(
            @RequestParam(required = false) Integer countryId) {
        return ResponseEntity.ok(service.getMonthlyStudentStatistics(countryId));
    }

    @GetMapping({"/monthly-users", "/users/monthly"})
    @PreAuthorize(MoheSecurityExpressions.DASHBOARD_OR_REPORT)
    public ResponseEntity<Map<String, Object>> getMonthlyUserStatistics(
            @RequestParam(required = false) Integer countryId) {
        return ResponseEntity.ok(service.getMonthlyUserStatistics(countryId));
    }

    @GetMapping("/table-counts")
    @PreAuthorize(MoheSecurityExpressions.DASHBOARD_OR_REPORT)
    public ResponseEntity<Map<String, Object>> getTableCountsSummary(
            @RequestParam(required = false) Integer countryId) {
        return ResponseEntity.ok(service.getTableCountsSummary(countryId));
    }
}
