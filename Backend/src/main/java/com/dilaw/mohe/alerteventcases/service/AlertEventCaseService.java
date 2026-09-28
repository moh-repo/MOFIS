package com.dilaw.mohe.alerteventcases.service;

import com.dilaw.mohe.alerteventcases.dto.AlertEventCaseDto;
import com.dilaw.mohe.alerteventcases.dto.AlertEventCaseSummaryDto;
import com.dilaw.mohe.alerteventcases.model.AlertEventCase;
import com.dilaw.mohe.alerteventcases.repository.AlertEventCaseRepository;
import com.dilaw.mohe.alerteventcases.repository.spec.AlertEventCaseSpec;
import com.dilaw.mohe.codes.AlertCaseStatus;
import com.dilaw.mohe.codes.AlertSeverity;
import com.dilaw.mohe.service.codelist.CodeListService;
import com.dilaw.mohe.service.codelist.CodeRow;
import com.dilaw.mohe.ums.service.CountryScopeService;
import org.springframework.security.core.context.SecurityContextHolder;
import com.dilaw.mohe.ums.service.CountryScopeService.CountryScope;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AlertEventCaseService {

    @Autowired
    private AlertEventCaseRepository caseRepo;

    @Autowired
    private CountryScopeService countryScopeService;

    @Autowired
    private CodeListService codeLists;

    @Transactional(readOnly = true)
    public Page<AlertEventCaseDto> getAll(String status, String scenarioCode, String severity,
                                          Integer studentId, String studentName,
                                          Integer countryId, Instant dateFrom, Instant dateTo,
                                          Pageable pageable) {
        CountryScope scope = countryScopeService.getScope();

        Specification<AlertEventCase> spec = Specification
                .where(AlertEventCaseSpec.notDeleted())
                .and(AlertEventCaseSpec.countryScope(scope))
                .and(AlertEventCaseSpec.byStatus(status))
                .and(AlertEventCaseSpec.byScenarioCode(scenarioCode))
                .and(AlertEventCaseSpec.bySeverity(severity))
                .and(AlertEventCaseSpec.byStudentId(studentId))
                .and(AlertEventCaseSpec.byStudentName(studentName))
                .and(AlertEventCaseSpec.byCountryId(countryId))
                .and(AlertEventCaseSpec.byDateRange(dateFrom, dateTo));

        return caseRepo.findAll(spec, pageable).map(AlertEventCaseDto::from);
    }

    @Transactional(readOnly = true)
    public AlertEventCaseSummaryDto getSummary() {
        CountryScope scope = countryScopeService.getScope();
        Specification<AlertEventCase> base = Specification
                .where(AlertEventCaseSpec.notDeleted())
                .and(AlertEventCaseSpec.countryScope(scope));

        AlertEventCaseSummaryDto dto = new AlertEventCaseSummaryDto();
        dto.setTotalAll(caseRepo.count(base));

        // The legend is the table: one row per active status and severity code,
        // in the list's order, zero counts included.
        Map<String, Long> byStatus = new LinkedHashMap<>();
        for (CodeRow status : codeLists.active("alert-status")) {
            long n = caseRepo.count(base.and(AlertEventCaseSpec.byStatus(status.code())));
            byStatus.put(status.code(), n);
            dto.getByStatus().add(new AlertEventCaseSummaryDto.CodeCount(status.code(), status.nameAr(), status.nameEn(),
                    status.uiSeverity(), status.icon(), status.sortOrder(), n));
        }
        dto.setTotalOpen(byStatus.getOrDefault(AlertCaseStatus.OPEN, 0L));
        dto.setTotalAcknowledged(byStatus.getOrDefault(AlertCaseStatus.ACKNOWLEDGED, 0L));
        dto.setTotalResolved(byStatus.getOrDefault(AlertCaseStatus.RESOLVED, 0L));
        dto.setTotalDismissed(byStatus.getOrDefault(AlertCaseStatus.DISMISSED, 0L));

        Specification<AlertEventCase> openBase = base.and(AlertEventCaseSpec.byStatus(AlertCaseStatus.OPEN));
        Map<String, Long> bySeverity = new LinkedHashMap<>();
        for (CodeRow sev : codeLists.active("alert-severity")) {
            long n = caseRepo.count(openBase.and(AlertEventCaseSpec.bySeverity(sev.code())));
            bySeverity.put(sev.code(), n);
            dto.getBySeverity().add(new AlertEventCaseSummaryDto.CodeCount(sev.code(), sev.nameAr(), sev.nameEn(),
                    sev.uiSeverity(), sev.icon(), sev.sortOrder(), n));
        }
        dto.setOpenCritical(bySeverity.getOrDefault(AlertSeverity.CRITICAL, 0L));
        dto.setOpenHigh(bySeverity.getOrDefault(AlertSeverity.HIGH, 0L));
        dto.setOpenMedium(bySeverity.getOrDefault(AlertSeverity.MEDIUM, 0L));
        dto.setOpenLow(bySeverity.getOrDefault(AlertSeverity.LOW, 0L));
        return dto;
    }

    @Transactional(readOnly = true)
    public AlertEventCaseDto getById(Integer id) {
        AlertEventCase c = findAndValidateAccess(id);
        return AlertEventCaseDto.from(c);
    }

    @Transactional
    public AlertEventCaseDto acknowledge(Integer id, String notes) {
        AlertEventCase c = findAndValidateAccess(id);
        if (!AlertCaseStatus.OPEN.equals(c.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Case must be OPEN to acknowledge");
        }
        String username = currentUsername();
        c.setStatus(AlertCaseStatus.ACKNOWLEDGED);
        c.setAcknowledgedBy(username);
        c.setAcknowledgedAt(Instant.now());
        if (notes != null && !notes.isBlank()) c.setNotes(notes);
        c.setUpdatedAt(Instant.now());
        return AlertEventCaseDto.from(caseRepo.save(c));
    }

    @Transactional
    public AlertEventCaseDto resolve(Integer id, String notes) {
        AlertEventCase c = findAndValidateAccess(id);
        if (List.of(AlertCaseStatus.RESOLVED, AlertCaseStatus.DISMISSED).contains(c.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Case is already " + c.getStatus());
        }
        String username = currentUsername();
        c.setStatus(AlertCaseStatus.RESOLVED);
        c.setResolvedBy(username);
        c.setResolvedAt(Instant.now());
        if (notes != null && !notes.isBlank()) c.setNotes(notes);
        c.setUpdatedAt(Instant.now());
        return AlertEventCaseDto.from(caseRepo.save(c));
    }

    @Transactional
    public AlertEventCaseDto dismiss(Integer id, String notes) {
        AlertEventCase c = findAndValidateAccess(id);
        if (List.of(AlertCaseStatus.RESOLVED, AlertCaseStatus.DISMISSED).contains(c.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Case is already " + c.getStatus());
        }
        String username = currentUsername();
        c.setStatus(AlertCaseStatus.DISMISSED);
        c.setResolvedBy(username);
        c.setResolvedAt(Instant.now());
        if (notes != null && !notes.isBlank()) c.setNotes(notes);
        c.setUpdatedAt(Instant.now());
        return AlertEventCaseDto.from(caseRepo.save(c));
    }

    private String currentUsername() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return (auth != null && auth.getName() != null) ? auth.getName() : "system";
    }

    private AlertEventCase findAndValidateAccess(Integer id) {
        AlertEventCase c = caseRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Alert case not found: " + id));
        if (Boolean.TRUE.equals(c.getIsDeleted())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Alert case not found: " + id);
        }
        countryScopeService.validateCountryAccess(c.getStudyCountryInId());
        return c;
    }
}
