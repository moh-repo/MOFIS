package com.dilaw.mohe.service;

import com.dilaw.mohe.approval.model.ApprovalRequest;
import com.dilaw.mohe.approval.model.ApprovalStatus;
import com.dilaw.mohe.approval.service.ApprovalWorkflowService;
import com.dilaw.mohe.codes.ReportLabelCode;
import com.dilaw.mohe.service.codelist.CodeListService;
import com.dilaw.mohe.dto.ApprovalsReportMetadataDto;
import com.dilaw.mohe.dto.ApprovalsReportRowDto;
import com.dilaw.mohe.ums.service.CountryScopeService;
import com.dilaw.mohe.ums.service.ReportCountryScopeHelper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * The approvals queue, as a document.
 *
 * <p>The workflow has been fully built for several rounds — 44 requests, 201
 * history entries, 133 steps — and has never had a printable readout. What a
 * queue report is for is the age column: which requests have been waiting, with
 * whom, and for how long.
 *
 * <p>Both halves are read through {@link ApprovalWorkflowService}, which already
 * narrows pending and completed requests to the caller's attachés. Nothing about
 * scope is restated here.
 */
@Transactional(readOnly = true)
@Service
@RequiredArgsConstructor
public class ApprovalsReportService {

    /** PENDING, COMPLETED, or null for both. */
    public static final String STATE_PENDING = ApprovalStatus.PENDING.name();
    public static final String STATE_COMPLETED = "COMPLETED"; // a report bucket, not a status code

    private final ApprovalWorkflowService workflowService;
    private final CountryNameService countryNameService;
    private final ReportCountryScopeHelper countryScopeHelper;
    private final CountryScopeService countryScopeService;
    private final CodeListService codeLists;
    private final com.dilaw.mohe.config.AppTimeZone appTimeZone;

    public ApprovalsReportMetadataDto metadata(String lang) {
        // The bucket names are LU_REPORT_LABEL rows (V077); the codes stay the
        // report's own state keys.
        String l = isArabic(lang) ? "ar" : "en";
        return ApprovalsReportMetadataDto.builder()
                .states(List.of(
                        ApprovalsReportMetadataDto.StateOption.builder()
                                .code(STATE_PENDING)
                                .name(codeLists.label("report-label", ReportLabelCode.LBL_APPROVALS_STATE_PENDING, l))
                                .build(),
                        ApprovalsReportMetadataDto.StateOption.builder()
                                .code(STATE_COMPLETED)
                                .name(codeLists.label("report-label", ReportLabelCode.LBL_APPROVALS_STATE_COMPLETED, l))
                                .build()))
                .countries(visibleCountries(lang))
                .build();
    }

    public List<ApprovalsReportRowDto> rows(String state, Integer countryId,
                                            List<Integer> countryIds, String lang) {
        List<Integer> resolved = countryScopeHelper.resolveCountryIds(countryId, countryIds);
        Map<Integer, String> countryNames = countryNameMap(lang);

        List<ApprovalRequest> requests = new ArrayList<>();
        if (state == null || STATE_PENDING.equalsIgnoreCase(state)) {
            requests.addAll(workflowService.getAllPending());
        }
        if (state == null || STATE_COMPLETED.equalsIgnoreCase(state)) {
            // Integer.MAX_VALUE would overflow the page arithmetic; the queue is
            // small and a report over it is bounded by what the workflow holds.
            requests.addAll(workflowService
                    .getCompletedHistory(PageRequest.of(0, 5000))
                    .getContent());
        }

        LocalDateTime now = LocalDateTime.now(appTimeZone.zone());
        return requests.stream()
                .filter(r -> matchesCountry(r, resolved))
                .map(r -> toRow(r, countryNames, now, lang))
                .sorted(Comparator.comparingLong(ApprovalsReportRowDto::getAgeDays).reversed())
                .toList();
    }

    /** How many requests sit in each status, by the status's name, for the footer. */
    public Map<String, Long> countsByStatus(List<ApprovalsReportRowDto> rows) {
        return rows.stream()
                .filter(r -> r.getStatus() != null)
                .collect(Collectors.groupingBy(r -> r.getStatusName() != null ? r.getStatusName() : r.getStatus(),
                        LinkedHashMap::new, Collectors.counting()));
    }

    private ApprovalsReportRowDto toRow(ApprovalRequest r, Map<Integer, String> countryNames, LocalDateTime now,
                                        String lang) {
        String l = "en".equalsIgnoreCase(lang) ? "en" : "ar";
        String requestType = r.getRequestType() == null ? null : r.getRequestType().name();
        String status = r.getStatus() == null ? null : r.getStatus().name();
        // Wall-clock time in the application's zone: the PDF prints it as is (review A3).
        LocalDateTime requestedAt = r.getRequestedAt() == null ? null
                : LocalDateTime.ofInstant(r.getRequestedAt(), appTimeZone.zone()).truncatedTo(ChronoUnit.SECONDS);
        return ApprovalsReportRowDto.builder()
                .id(r.getId())
                .requestType(requestType)
                .entityType(r.getEntityType())
                .entityId(r.getEntityId())
                .status(status)
                .requestTypeName(requestType == null ? null : codeLists.label("request-type", requestType, l))
                .entityTypeName(r.getEntityType() == null ? null
                        : codeLists.label("approval-entity-type", r.getEntityType(), l))
                .statusName(status == null ? null : codeLists.label("approval-status", status, l))
                .currentLevel(r.getCurrentLevel())
                .requestedBy(r.getRequesterFullName())
                .requestedAt(requestedAt)
                .ageDays(requestedAt == null ? 0L : ChronoUnit.DAYS.between(requestedAt, now))
                .countryId(r.getCountryId())
                .countryName(r.getCountryId() == null ? null : countryNames.get(r.getCountryId()))
                .note(r.getRejectionReason())
                .build();
    }

    /**
     * A request with no country is ministry-level, so narrowing to an attaché
     * drops it rather than showing it under every one.
     */
    private boolean matchesCountry(ApprovalRequest r, List<Integer> resolved) {
        if (resolved == null) {
            return true;
        }
        return r.getCountryId() != null && resolved.contains(r.getCountryId());
    }

    private List<ApprovalsReportMetadataDto.CountryOption> visibleCountries(String lang) {
        var scope = countryScopeService.getScope();
        return countryNameService.all().stream()
                .filter(c -> scope.isMinistryUser() || scope.getAllowedCountryIds().contains(c.id()))
                .map(c -> ApprovalsReportMetadataDto.CountryOption.builder()
                        .id(c.id())
                        .name(pick(isArabic(lang), c.nameAr(), c.nameEn()))
                        .build())
                .sorted(Comparator.comparing(ApprovalsReportMetadataDto.CountryOption::getName,
                        Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                .toList();
    }

    private Map<Integer, String> countryNameMap(String lang) {
        return countryNameService.all().stream()
                .collect(Collectors.toMap(CountryNameService.CountryRef::id,
                        c -> pick(isArabic(lang), c.nameAr(), c.nameEn()),
                        (a, b) -> a, LinkedHashMap::new));
    }

    private static boolean isArabic(String lang) {
        return lang == null || "ar".equalsIgnoreCase(lang);
    }

    private static String pick(boolean arabic, String ar, String en) {
        String first = arabic ? ar : en;
        String second = arabic ? en : ar;
        return first != null && !first.isBlank() ? first : second;
    }
}
