package com.dilaw.mohe.service;

import com.dilaw.mohe.dto.EnrollmentStatisticsMetadataDto;
import com.dilaw.mohe.dto.LookupOptionDto;
import com.dilaw.mohe.model.MoheQuarter;
import com.dilaw.mohe.repository.EnrollmentStatisticsRepository;
import com.dilaw.mohe.ums.service.ReportCountryScopeHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Filter options for the enrollment-statistics report.
 *
 * <p>Every option is derived from {@code V_Enrollment_Statistics} itself, so
 * selecting any of them always returns rows. Filling them from the lookup
 * tables instead offered 444 majors where the view holds two, and listed study
 * destinations against a column that stores the student's nationality.
 */
@Transactional(readOnly = true)
@Service
public class EnrollmentStatisticsMetadataService {

    /**
     * The view's "not recorded" id: {@code V_Enrollment_Statistics} turns a missing
     * country, major, degree or study type into 0 (a key column cannot be null), and
     * its name columns are then empty.
     */
    private static final int NOT_RECORDED = 0;

    @Autowired
    private EnrollmentStatisticsRepository enrollmentStatisticsRepository;

    @Autowired
    private ReportCountryScopeHelper countryScopeHelper;

    @Autowired
    private EnrollmentStatisticsService enrollmentStatisticsService;

    public EnrollmentStatisticsMetadataDto getMetadata() {
        return getMetadata("ar", null, null);
    }

    public EnrollmentStatisticsMetadataDto getMetadata(String lang,
                                                       Integer countryId,
                                                       List<Integer> requestCountryIds) {
        countryScopeHelper.ensureScopeOrThrow();
        List<Integer> countryIds = countryScopeHelper.resolveCountryIds(countryId, requestCountryIds);
        boolean applyCountry = countryScopeHelper.shouldApplyCountryFilter(countryIds);
        List<Integer> safeIds = countryScopeHelper.safeList(countryIds);
        boolean ar = !"en".equalsIgnoreCase(lang);

        return EnrollmentStatisticsMetadataDto.builder()
                .quarters(enrollmentStatisticsRepository.findDistinctQuarters(applyCountry, safeIds))
                .quarterOptions(quarterOptions(lang, applyCountry, safeIds))
                .countries(options(enrollmentStatisticsRepository.findDistinctCountries(applyCountry, safeIds), ar))
                .majors(options(enrollmentStatisticsRepository.findDistinctMajors(applyCountry, safeIds), ar))
                .degrees(options(enrollmentStatisticsRepository.findDistinctDegrees(applyCountry, safeIds), ar))
                .studyTypes(options(enrollmentStatisticsRepository.findDistinctStudyTypes(applyCountry, safeIds), ar))
                .build();
    }

    /**
     * Turns distinct (id, Arabic name, English name) rows from the view into sorted
     * dropdown options named in the report's language, with the other as fallback
     * (live QA M13: the English report offered Arabic names).
     */
    private List<LookupOptionDto> options(List<Object[]> rows, boolean ar) {
        Map<Integer, String> byId = new LinkedHashMap<>();
        for (Object[] row : rows) {
            if (row == null || row.length < 2 || row[0] == null) {
                continue;
            }
            Integer id = ((Number) row[0]).intValue();
            if (id == NOT_RECORDED) {
                // Named like the quarter filter's code 0 rather than printed as a
                // bare "0" (live QA R5-15).
                byId.putIfAbsent(id, enrollmentStatisticsService.unspecified(ar));
                continue;
            }
            Object arabic = row[1];
            Object english = row.length > 2 ? row[2] : null;
            Object chosen = ar ? arabic : english;
            if (chosen == null || String.valueOf(chosen).isBlank()) {
                chosen = ar ? english : arabic;
            }
            String name = chosen != null ? String.valueOf(chosen).trim() : "";
            // A blank name would render as an unpickable empty row.
            byId.putIfAbsent(id, name.isEmpty() ? String.valueOf(id) : name);
        }
        return byId.entrySet().stream()
                .map(e -> new LookupOptionDto(e.getKey(), e.getValue()))
                // "Unspecified" last, as code 0 is in the quarter list.
                .sorted(Comparator.comparing((LookupOptionDto o) -> o.getId() == NOT_RECORDED)
                        .thenComparing(LookupOptionDto::getName, String::compareToIgnoreCase))
                .collect(Collectors.toList());
    }

    /**
     * Quarter options labelled exactly as the report's rows are.
     *
     * <p>{@code enrollment_quarter} holds a packed code such as 20251, so the raw
     * value is meaningless as a label. It is resolved against
     * {@code MOHE_QUARTER.QUARTER} by the row service: looked up by the entity's
     * {@code ID} instead, no code ever matched and every option read "Unspecified"
     * (live QA R5-15).
     */
    private List<LookupOptionDto> quarterOptions(String lang, boolean applyCountry, List<Integer> countryIds) {
        boolean ar = !"en".equalsIgnoreCase(lang);
        Map<Integer, MoheQuarter> dimension = enrollmentStatisticsService.quarterIndex();

        return enrollmentStatisticsRepository.findDistinctQuarters(applyCountry, countryIds).stream()
                .filter(java.util.Objects::nonNull)
                .sorted(Comparator.reverseOrder())
                .map(code -> {
                    MoheQuarter q = enrollmentStatisticsService.resolveQuarter(code, dimension);
                    String label = q != null
                            ? enrollmentStatisticsService.quarterLabel(q, ar)
                            : enrollmentStatisticsService.unspecified(ar);
                    return new LookupOptionDto(code, label);
                })
                .collect(Collectors.toList());
    }
}
