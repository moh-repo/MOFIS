package com.dilaw.mohe.service;

import com.dilaw.mohe.service.codelist.CodeListService;
import com.dilaw.mohe.systemsettings.service.SysParameterService;

import com.dilaw.mohe.audit.MoheAuditLog;
import com.dilaw.mohe.audit.MoheAuditLogService;
import com.dilaw.mohe.dto.AuditTrailReportMetadataDto;
import com.dilaw.mohe.dto.AuditTrailRowDto;
import com.dilaw.mohe.ums.service.CountryScopeService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * The audit trail, as a document rather than a screen.
 *
 * <p>This report is filter-first, and that is the whole design. The trail holds
 * 11,625 rows and grows with every change anyone makes; the last thirty days
 * alone are 9,368. "Print everything" is not a useful default here, it is a
 * thousand-page PDF nobody reads.
 *
 * <p>So a date range is <b>required</b>, and a range that would exceed
 * {@code mohe.report.audit-trail.max-rows} is <b>refused with the actual count</b>
 * rather than silently truncated. Truncating an audit report is the worst of the
 * options available: it produces an incomplete document that looks complete,
 * which is exactly the failure an audit trail exists to prevent.
 *
 * <p>Scope comes from {@link MoheAuditLogService}, which already narrows every
 * read to the caller's attachés — including the rule that a row with no country
 * (a {@code UMSCORE.Users} change, say) is ministry-only.
 */
@Transactional(readOnly = true)
@Service
@RequiredArgsConstructor
public class AuditTrailReportService {

    private final MoheAuditLogService auditLogService;
    private final CountryNameService countryNameService;
    private final CountryScopeService countryScopeService;
    private final SysParameterService sysParameters;
    private final CodeListService codeLists;

    @Value("${mohe.report.audit-trail.max-rows:2000}")
    private int maxRows;

    /** Default window when the caller does not choose one: the {@code AUDIT.DEFAULT_WINDOW_DAYS} parameter. */
    public int defaultWindowDays() {
        return sysParameters.intValue(SysParameterService.AUDIT_DEFAULT_WINDOW_DAYS);
    }

    public AuditTrailReportMetadataDto metadata(String lang) {
        LocalDate today = LocalDate.now();
        return AuditTrailReportMetadataDto.builder()
                .tableNames(auditLogService.getDistinctTableNames())
                .changedByOptions(auditLogService.getDistinctChangedBy())
                // The operations are the request types of LU_APPROVAL_REQUEST_TYPE (INSERT, UPDATE, DELETE).
                .operations(List.copyOf(codeLists.codes("request-type")))
                .defaultDateFrom(today.minusDays(defaultWindowDays()))
                .defaultDateTo(today)
                .maxRows(maxRows)
                .countries(visibleCountries(lang))
                .build();
    }

    /**
     * @throws IllegalArgumentException when the range is absent, inverted, or wide
     *         enough to exceed the row cap — all of which the controller turns
     *         into a 400 carrying the message, so the user is told how to narrow it
     */
    public List<AuditTrailRowDto> rows(LocalDate dateFrom, LocalDate dateTo,
                                       String tableName, String operation, String changedBy,
                                       String recordId, String lang) {
        if (dateFrom == null || dateTo == null) {
            throw new IllegalArgumentException(
                    "A date range is required for the audit trail report.");
        }
        if (dateTo.isBefore(dateFrom)) {
            throw new IllegalArgumentException(
                    "The end of the range is before its start.");
        }

        LocalDateTime start = dateFrom.atStartOfDay();
        LocalDateTime end = dateTo.atTime(LocalTime.MAX);

        // One count first, so an oversized request is refused before anything is
        // materialised rather than after.
        Page<MoheAuditLog> probe = auditLogService.getAuditLogsWithFilters(
                tableName, recordId, operation, changedBy, start, end, 0, 1);
        long total = probe.getTotalElements();

        if (total > maxRows) {
            throw new IllegalArgumentException(
                    "This range covers " + total + " audit records, more than the "
                            + maxRows + " a single report may carry. Narrow the dates, "
                            + "or filter by table or user, and try again.");
        }

        if (total == 0) {
            return List.of();
        }

        Page<MoheAuditLog> page = auditLogService.getAuditLogsWithFilters(
                tableName, recordId, operation, changedBy, start, end, 0, (int) total);

        Map<Integer, String> countryNames = countryNameMap(lang);
        return page.getContent().stream()
                .map(log -> toRow(log, countryNames))
                .toList();
    }

    /** How many records the current filters would produce, for the screen's warning. */
    public long count(LocalDate dateFrom, LocalDate dateTo,
                      String tableName, String operation, String changedBy, String recordId) {
        if (dateFrom == null || dateTo == null || dateTo.isBefore(dateFrom)) {
            return 0L;
        }
        return auditLogService.getAuditLogsWithFilters(
                        tableName, recordId, operation, changedBy,
                        dateFrom.atStartOfDay(), dateTo.atTime(LocalTime.MAX), 0, 1)
                .getTotalElements();
    }

    /** Days between the two dates, inclusive — used only for the report subtitle. */
    public long spanDays(LocalDate dateFrom, LocalDate dateTo) {
        if (dateFrom == null || dateTo == null) {
            return 0L;
        }
        return ChronoUnit.DAYS.between(dateFrom, dateTo) + 1;
    }

    private AuditTrailRowDto toRow(MoheAuditLog log, Map<Integer, String> countryNames) {
        return AuditTrailRowDto.builder()
                .logId(log.getLogId())
                .changedAt(log.getChangedAt())
                .schemaName(log.getSchemaName())
                .tableName(log.getTableName())
                .recordId(log.getRecordId())
                .operation(log.getOperation())
                .changedBy(log.getChangedBy())
                .changedColumns(flattenColumns(log.getChangedColumns()))
                .ipAddress(log.getIpAddress())
                .countryId(log.getCountryId())
                .countryName(log.getCountryId() == null ? null : countryNames.get(log.getCountryId()))
                .build();
    }

    /**
     * CHANGED_COLUMNS is stored as a JSON array by the trigger but as a plain
     * comma-separated list by some older rows. Both end up as one readable string
     * rather than as raw JSON in a table cell.
     */
    private String flattenColumns(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String trimmed = raw.trim();
        if (!trimmed.startsWith("[")) {
            return trimmed;
        }
        return trimmed.substring(1, Math.max(1, trimmed.length() - 1))
                .replace("\"", "")
                .replace(",", ", ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private List<AuditTrailReportMetadataDto.CountryOption> visibleCountries(String lang) {
        var scope = countryScopeService.getScope();
        return countryNameService.all().stream()
                .filter(c -> scope.isMinistryUser() || scope.getAllowedCountryIds().contains(c.id()))
                .map(c -> AuditTrailReportMetadataDto.CountryOption.builder()
                        .id(c.id())
                        .name(countryName(c, lang))
                        .build())
                .toList();
    }

    private Map<Integer, String> countryNameMap(String lang) {
        return countryNameService.all().stream()
                .collect(Collectors.toMap(CountryNameService.CountryRef::id, c -> countryName(c, lang),
                        (a, b) -> a, LinkedHashMap::new));
    }

    private String countryName(CountryNameService.CountryRef c, String lang) {
        boolean arabic = lang == null || "ar".equalsIgnoreCase(lang);
        String first = arabic ? c.nameAr() : c.nameEn();
        String second = arabic ? c.nameEn() : c.nameAr();
        return first != null && !first.isBlank() ? first : second;
    }
}
