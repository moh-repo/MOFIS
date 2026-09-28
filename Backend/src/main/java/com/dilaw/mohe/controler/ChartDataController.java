package com.dilaw.mohe.controler;

import com.dilaw.mohe.config.MoheSecurityExpressions;
import com.dilaw.mohe.dto.*;
import com.dilaw.mohe.service.ChartDataService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("api/v1/charts")
@PreAuthorize(MoheSecurityExpressions.REPORT_OTHER)
public class ChartDataController {

    @Autowired
    private ChartDataService service;

    @GetMapping("/enrollment-by-country")
    public ResponseEntity<ChartDataDto> getEnrollmentByCountry(
            @RequestParam(required = false) Integer countryId) {
        return ResponseEntity.ok(service.getStudentEnrollmentByCountry(countryId));
    }

    @GetMapping("/financial-by-year")
    public ResponseEntity<ChartDataDto> getFinancialByYear(
            @RequestParam(required = false) Integer countryId) {
        return ResponseEntity.ok(service.getFinancialDataByYear(countryId));
    }

    @GetMapping("/students-by-degree")
    public ResponseEntity<ChartDataDto> getStudentsByDegree(
            @RequestParam(required = false) Integer countryId) {
        return ResponseEntity.ok(service.getStudentsByDegree(countryId));
    }

    @GetMapping("/financial-by-quarter")
    public ResponseEntity<ChartDataDto> getFinancialByQuarter(
            @RequestParam(required = false) Integer countryId) {
        return ResponseEntity.ok(service.getFinancialByQuarter(countryId));
    }

    @GetMapping("/status-distribution")
    public ResponseEntity<ChartDataDto> getStatusDistribution(
            @RequestParam(required = false) Integer countryId) {
        return ResponseEntity.ok(service.getStudentStatusDistribution(countryId));
    }

    @GetMapping("/pie/major-distribution")
    public ResponseEntity<ChartDataDto> getMajorDistribution(
            @RequestParam(required = false) Integer countryId) {
        return ResponseEntity.ok(service.getStudentsByMajorDistribution(countryId));
    }

    @GetMapping("/pie/gender-distribution")
    public ResponseEntity<ChartDataDto> getGenderDistribution(
            @RequestParam(required = false) Integer countryId) {
        return ResponseEntity.ok(service.getStudentsByGenderDistribution(countryId));
    }

    @GetMapping("/pie/university-type-distribution")
    public ResponseEntity<ChartDataDto> getUniversityTypeDistribution(
            @RequestParam(required = false) Integer countryId) {
        return ResponseEntity.ok(service.getStudentsByUniversityTypeDistribution(countryId));
    }

    @GetMapping("/pie/study-status-distribution")
    public ResponseEntity<ChartDataDto> getStudyStatusDistribution(
            @RequestParam(required = false) Integer countryId) {
        return ResponseEntity.ok(service.getStudentsByStudyStatusDistribution(countryId));
    }

    @GetMapping("/hierarchy/location")
    public ResponseEntity<HierarchyNodeDto> getLocationHierarchy(
            @RequestParam(required = false) Integer countryId) {
        return ResponseEntity.ok(service.getLocationHierarchy(countryId));
    }

    @GetMapping("/hierarchy/organization")
    public ResponseEntity<HierarchyNodeDto> getOrganizationHierarchy(
            @RequestParam(required = false) Integer countryId) {
        return ResponseEntity.ok(service.getOrganizationHierarchy(countryId));
    }

    @GetMapping("/hierarchy/academic")
    public ResponseEntity<HierarchyNodeDto> getAcademicHierarchy(
            @RequestParam(required = false) Integer countryId) {
        return ResponseEntity.ok(service.getAcademicHierarchy(countryId));
    }

    @GetMapping("/geo/distribution")
    public ResponseEntity<List<GeoLocationDto>> getGeographicDistribution(
            @RequestParam(required = false) Integer countryId) {
        return ResponseEntity.ok(service.getGeographicDistribution(countryId));
    }

    /**
     * The overview the comprehensive-dashboards report reads. The landing page
     * itself moved to {@code DashboardController} (one endpoint per section, V070);
     * this handler keeps the wider pre-V070 gate so the report page — reachable by
     * report holders who never had {@code DASHBOARD.VIEW} — goes on working.
     */
    @GetMapping("/dashboard/overview")
    @PreAuthorize(MoheSecurityExpressions.DASHBOARD_OR_REPORT)
    public ResponseEntity<DashboardOverviewDto> getDashboardOverview(
            @RequestParam(required = false) Integer countryId) {
        return ResponseEntity.ok(service.getDashboardOverview(countryId));
    }

    @GetMapping("/line/enrollment-growth")
    public ResponseEntity<ChartDataDto> getEnrollmentGrowthTrend(
            @RequestParam(required = false) Integer countryId) {
        return ResponseEntity.ok(service.getEnrollmentGrowthTrend(countryId));
    }

    @GetMapping("/line/payment-patterns")
    public ResponseEntity<ChartDataDto> getPaymentPatternsTrend(
            @RequestParam(required = false) Integer countryId) {
        return ResponseEntity.ok(service.getPaymentPatternsTrend(countryId));
    }

    @GetMapping("/line/academic-progress")
    public ResponseEntity<ChartDataDto> getAcademicProgressTrend(
            @RequestParam(required = false) Integer countryId) {
        return ResponseEntity.ok(service.getAcademicProgressTrend(countryId));
    }

    @GetMapping("/line/quarterly-disbursement")
    public ResponseEntity<ChartDataDto> getQuarterlyDisbursementTrend(
            @RequestParam(required = false) Integer countryId) {
        return ResponseEntity.ok(service.getQuarterlyDisbursementTrend(countryId));
    }

    @GetMapping("/line/student-retention")
    public ResponseEntity<ChartDataDto> getStudentRetentionTrend(
            @RequestParam(required = false) Integer countryId) {
        return ResponseEntity.ok(service.getStudentRetentionTrend(countryId));
    }
}
