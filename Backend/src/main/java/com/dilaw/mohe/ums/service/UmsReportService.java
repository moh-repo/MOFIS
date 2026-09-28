package com.dilaw.mohe.ums.service;

import com.dilaw.mohe.exception.ResourceNotFoundException;
import com.dilaw.mohe.ums.model.UmsReport;
import com.dilaw.mohe.ums.repository.UmsReportRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class UmsReportService {

    @Autowired
    private UmsReportRepository reportRepository;

    public List<UmsReport> getAllActiveReports() {
        return reportRepository.findByIsActiveTrue();
    }

    /** The active catalogue in display order — hubs and cards alike; the client builds the tree. */
    public List<UmsReport> catalogue() {
        return reportRepository.findByIsActiveTrueOrderBySortOrderAscIdAsc();
    }

    public Page<UmsReport> listReports(String keyword, String category, Pageable pageable) {
        return reportRepository.listReports(trimNull(keyword), trimNull(category), pageable);
    }

    private static String trimNull(String value) {
        if (value == null) {
            return null;
        }
        String t = value.trim();
        return t.isEmpty() ? null : t;
    }

    public Page<UmsReport> getAllReports(Pageable pageable) {
        return listReports(null, null, pageable);
    }

    public Optional<UmsReport> getReportById(Long id) {
        return reportRepository.findById(id);
    }

    public UmsReport createReport(UmsReport report) {
        // SEC-01: a create never merges onto an existing row, whatever id the body carried.
        report.setId(null);
        report.setIsActive(true);
        return reportRepository.save(report);
    }

    public UmsReport updateReport(Long id, UmsReport reportUpdate) {
        return reportRepository.findById(id)
                .map(existing -> {
                    existing.setReportCode(reportUpdate.getReportCode());
                    existing.setReportName(reportUpdate.getReportName());
                    existing.setReportNameAr(reportUpdate.getReportNameAr());
                    existing.setDescription(reportUpdate.getDescription());
                    existing.setDescriptionAr(reportUpdate.getDescriptionAr());
                    existing.setCategory(reportUpdate.getCategory());
                    existing.setRoute(reportUpdate.getRoute());
                    existing.setParentCode(reportUpdate.getParentCode());
                    existing.setIcon(reportUpdate.getIcon());
                    existing.setColor(reportUpdate.getColor());
                    existing.setQueryParams(reportUpdate.getQueryParams());
                    existing.setPageCode(reportUpdate.getPageCode());
                    existing.setPermissionCodes(reportUpdate.getPermissionCodes());
                    if (reportUpdate.getSortOrder() != null) {
                        existing.setSortOrder(reportUpdate.getSortOrder());
                    }
                    if (reportUpdate.getIsActive() != null) {
                        existing.setIsActive(reportUpdate.getIsActive());
                    }
                    return reportRepository.save(existing);
                })
                .orElseThrow(() -> new ResourceNotFoundException("Report not found with id: " + id));
    }

    public void deleteReport(Long id) {
        UmsReport report = reportRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Report not found with id: " + id));
        report.setIsActive(false);
        reportRepository.save(report);
    }
}
