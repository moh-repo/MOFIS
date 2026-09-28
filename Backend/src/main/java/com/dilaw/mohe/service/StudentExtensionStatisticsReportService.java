package com.dilaw.mohe.service;

import com.dilaw.mohe.codes.ExtensionStatusCode;
import com.dilaw.mohe.service.codelist.CodeListService;
import com.dilaw.mohe.service.codelist.CodeRow;

import com.dilaw.mohe.dto.StudentExtensionReportMetadataDto;
import com.dilaw.mohe.dto.StudentExtensionStatisticsDto;
import com.dilaw.mohe.dto.StudentExtensionTrendDto;
import com.dilaw.mohe.model.StudentExtension;
import com.dilaw.mohe.repository.StudentExtensionRepository;
import com.dilaw.mohe.ums.service.ReportCountryScopeHelper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Transactional(readOnly = true)
@Service
@RequiredArgsConstructor
public class StudentExtensionStatisticsReportService {

    private final StudentExtensionRepository repository;
    private final ReportCountryScopeHelper countryScopeHelper;
    private final CodeListService codeLists;

    public StudentExtensionReportMetadataDto metadata() {
        return StudentExtensionReportMetadataDto.builder()
                .extensionTypes(repository.findDistinctExtensionTypes())
                .build();
    }

    public List<StudentExtensionStatisticsDto> statistics(Integer countryId, List<Integer> requestCountryIds) {
        List<StudentExtension> all = findScopedExtensions(countryId, requestCountryIds);
        Map<String, List<StudentExtension>> byType = all.stream()
                .collect(Collectors.groupingBy(this::typeKey));

        List<StudentExtensionStatisticsDto> out = new ArrayList<>();
        for (Map.Entry<String, List<StudentExtension>> e : byType.entrySet()) {
            out.add(buildStatsRow(e.getKey(), e.getValue()));
        }
        out.sort(Comparator.comparing(StudentExtensionStatisticsDto::getExtensionType, String.CASE_INSENSITIVE_ORDER));
        return out;
    }

    public List<StudentExtensionTrendDto> trends(Integer countryId, List<Integer> requestCountryIds) {
        List<StudentExtension> all = findScopedExtensions(countryId, requestCountryIds);
        record YearQuarter(int year, int quarter) implements Comparable<YearQuarter> {
            @Override
            public int compareTo(YearQuarter o) {
                int c = Integer.compare(year, o.year);
                return c != 0 ? c : Integer.compare(quarter, o.quarter);
            }

            String quarterLabel() {
                return "Q" + quarter;
            }
        }

        Map<YearQuarter, List<StudentExtension>> buckets = new HashMap<>();
        for (StudentExtension se : all) {
            LocalDate d = resolveDate(se);
            if (d == null) {
                continue;
            }
            YearQuarter yq = new YearQuarter(d.getYear(), (d.getMonthValue() - 1) / 3 + 1);
            buckets.computeIfAbsent(yq, k -> new ArrayList<>()).add(se);
        }

        List<YearQuarter> keys = new ArrayList<>(buckets.keySet());
        keys.sort(Comparator.naturalOrder());

        List<StudentExtensionTrendDto> rows = new ArrayList<>();
        long prevCount = -1;
        for (YearQuarter yq : keys) {
            List<StudentExtension> list = buckets.get(yq);
            long total = list.size();
            long approved = list.stream().filter(s -> ExtensionStatusCode.APPROVED.equals(statusBucket(s))).count();
            String trend = trendLabel(prevCount, total);
            prevCount = total;
            rows.add(StudentExtensionTrendDto.builder()
                    .year(yq.year())
                    .quarter(yq.quarterLabel())
                    .extensionCount(total)
                    .approvedCount(approved)
                    .trend(trend)
                    .build());
        }
        return rows;
    }

    public List<StudentExtensionStatisticsDto> statisticsFiltered(Integer countryId, List<Integer> requestCountryIds, String extensionType) {
        if (extensionType == null || extensionType.isBlank()) {
            return statistics(countryId, requestCountryIds);
        }
        return statistics(countryId, requestCountryIds).stream()
                .filter(s -> extensionType.trim().equalsIgnoreCase(s.getExtensionType()))
                .collect(Collectors.toList());
    }

    public List<StudentExtensionTrendDto> trendsFiltered(Integer countryId, List<Integer> requestCountryIds, Integer year) {
        if (year == null) {
            return trends(countryId, requestCountryIds);
        }
        return trends(countryId, requestCountryIds).stream()
                .filter(t -> t.getYear() == year)
                .collect(Collectors.toList());
    }

    private List<StudentExtension> findScopedExtensions(Integer countryId, List<Integer> requestCountryIds) {
        countryScopeHelper.ensureScopeOrThrow();
        List<Integer> countryIds = countryScopeHelper.resolveCountryIds(countryId, requestCountryIds);
        boolean applyCountry = countryScopeHelper.shouldApplyCountryFilter(countryIds);
        return repository.findAllScoped(applyCountry, countryScopeHelper.safeList(countryIds));
    }

    private static String trendLabel(long previousCount, long currentCount) {
        if (previousCount < 0) {
            return "Stable";
        }
        if (previousCount == 0) {
            return currentCount > 0 ? "Increasing" : "Stable";
        }
        double ratio = (double) currentCount / (double) previousCount;
        if (ratio > 1.05) {
            return "Increasing";
        }
        if (ratio < 0.95) {
            return "Decreasing";
        }
        return "Stable";
    }

    private StudentExtensionStatisticsDto buildStatsRow(String typeKey, List<StudentExtension> list) {
        long total = list.size();
        long approved = list.stream().filter(s -> ExtensionStatusCode.APPROVED.equals(statusBucket(s))).count();
        long rejected = list.stream().filter(s -> ExtensionStatusCode.REJECTED.equals(statusBucket(s))).count();
        long pending = total - approved - rejected;
        if (pending < 0) {
            pending = 0;
        }
        double avgQuarters = list.stream()
                .map(StudentExtension::getExtensionQuarters)
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .average()
                .orElse(0.0);
        double rate = total > 0 ? Math.round(1000.0 * approved / total) / 10.0 : 0.0;
        return StudentExtensionStatisticsDto.builder()
                .extensionType(typeKey)
                .totalExtensions(total)
                .approvedCount(approved)
                .pendingCount(pending)
                .rejectedCount(rejected)
                .averageQuarters(Math.round(avgQuarters * 10.0) / 10.0)
                .approvalRate(rate)
                .build();
    }

    private String typeKey(StudentExtension se) {
        if (se.getExtensionType() == null || se.getExtensionType().isBlank()) {
            return "Unknown";
        }
        return se.getExtensionType().trim();
    }

    private static LocalDate resolveDate(StudentExtension se) {
        if (se.getExtensionStartDate() != null) {
            return se.getExtensionStartDate();
        }
        if (se.getCreatedDate() != null) {
            return se.getCreatedDate().toLocalDate();
        }
        return null;
    }

    /**
     * The bucket an extension falls in, resolved through {@code EXTENSION_STATUS}:
     * the stored value is matched against the table's code and both names (older
     * rows carry a name), so an Arabic status buckets as its code says rather
     * than as a substring of an English word. Anything the table does not know
     * counts as pending.
     */
    private String statusBucket(StudentExtension se) {
        String status = se.getExtensionStatus();
        if (status == null || status.isBlank()) {
            return ExtensionStatusCode.PENDING;
        }
        String wanted = status.trim();
        for (CodeRow row : codeLists.rows("extension-status")) {
            if (wanted.equalsIgnoreCase(row.code())
                    || wanted.equalsIgnoreCase(row.nameEn())
                    || wanted.equals(row.nameAr())) {
                if (ExtensionStatusCode.REJECTED.equals(row.code())) return ExtensionStatusCode.REJECTED;
                if (ExtensionStatusCode.APPROVED.equals(row.code())) return ExtensionStatusCode.APPROVED;
                return ExtensionStatusCode.PENDING;
            }
        }
        return ExtensionStatusCode.PENDING;
    }
}
