package com.dilaw.mohe.service;

import com.dilaw.mohe.codes.ReportLabelCode;
import com.dilaw.mohe.dto.EnrollmentStatisticsRowDto;
import com.dilaw.mohe.service.codelist.CodeListService;
import com.dilaw.mohe.model.EnrollmentStatistics;
import com.dilaw.mohe.model.MoheQuarter;
import com.dilaw.mohe.repository.EnrollmentStatisticsRepository;
import com.dilaw.mohe.repository.MoheQuarterRepository;
import com.dilaw.mohe.ums.service.ReportCountryScopeHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Transactional(readOnly = true)
@Service
public class EnrollmentStatisticsService {

    @Autowired
    private EnrollmentStatisticsRepository repository;

    @Autowired
    private MoheQuarterRepository moheQuarterRepository;

    @Autowired
    private ReportCountryScopeHelper countryScopeHelper;

    @Autowired
    private CodeListService codeLists;

    public List<EnrollmentStatisticsRowDto> search(Integer quarter,
                                                   Integer countryId,
                                                   List<Integer> requestCountryIds,
                                                   Integer majorId,
                                                   Integer degreeId,
                                                   Integer studyTypeId,
                                                   String lang) {
        countryScopeHelper.ensureScopeOrThrow();
        List<Integer> countryIds = countryScopeHelper.resolveCountryIds(countryId, requestCountryIds);
        boolean applyCountry = countryScopeHelper.shouldApplyCountryFilter(countryIds);
        List<EnrollmentStatistics> rows = repository.search(
                quarter,
                applyCountry,
                countryScopeHelper.safeList(countryIds),
                majorId,
                degreeId,
                studyTypeId);

        Map<Integer, MoheQuarter> quarters = quarterIndex();
        boolean ar = !"en".equalsIgnoreCase(lang);

        return rows.stream()
                .map(row -> toDto(row, quarters, ar))
                // Newest quarter first, then the largest cohorts, so the report
                // opens on what matters instead of the view's arbitrary order.
                .sorted(Comparator
                        .comparing(EnrollmentStatisticsRowDto::getQuarterCode,
                                Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(EnrollmentStatisticsRowDto::getStudentCount,
                                Comparator.nullsLast(Comparator.reverseOrder())))
                .collect(Collectors.toList());
    }

    private EnrollmentStatisticsRowDto toDto(EnrollmentStatistics row,
                                             Map<Integer, MoheQuarter> quarters,
                                             boolean ar) {
        EnrollmentStatisticsRowDto dto = new EnrollmentStatisticsRowDto();
        Integer code = row.getEnrollmentQuarter();
        dto.setQuarterCode(code);

        MoheQuarter q = resolveQuarter(code, quarters);
        if (q != null) {
            dto.setEnrollmentYear(q.getQuarterYear());
            dto.setEnrollmentQuarter(q.getQuarterNumber());
            dto.setQuarterLabel(quarterLabel(q, ar));
        } else {
            // Code 0 means the student has no recorded enrollment quarter; say so
            // rather than printing the raw code, which reads as "Q0".
            dto.setQuarterLabel(unspecified(ar));
        }

        dto.setStudentCountryId(row.getStudentCountryId());
        dto.setStudentCountryName(row.getStudentCountryName());
        dto.setStudentCountryNameEn(row.getStudentCountryNameEn());
        dto.setStudentMajorId(row.getStudentMajorId());
        dto.setStudentMajorName(row.getStudentMajorName());
        dto.setStudentMajorNameEn(row.getStudentMajorNameEn());
        dto.setStudentDegreeId(row.getStudentDegreeId());
        dto.setStudentDegreeName(row.getStudentDegreeName());
        dto.setStudentDegreeNameEn(row.getStudentDegreeNameEn());
        dto.setStudentStudyTypeId(row.getStudentStudyTypeId());
        dto.setStudentStudyTypeName(row.getStudentStudyTypeName());
        dto.setStudentStudyTypeNameEn(row.getStudentStudyTypeNameEn());
        dto.setStudentCount(row.getStudentCount());
        return dto;
    }

    /**
     * Label for a resolved quarter.
     *
     * <p>Arabic uses the ministry's own {@code QUARTER_NAME}. English is composed
     * from the {@code LU_QUARTER_NUMBER} name and {@code QUARTER_YEAR} rather than
     * {@code QUARTER_NAME_EN}, because that column holds Arabic text in this
     * database — the same Arabic-only-names gap recorded in the report data audit.
     */
    String quarterLabel(MoheQuarter q, boolean ar) {
        if (ar) {
            String name = q.getQuarterName();
            if (name != null && !name.isBlank()) {
                return name;
            }
        }
        if (q.getQuarterNumber() == null) {
            return unspecified(ar);
        }
        String number = codeLists.label("quarter-number", String.valueOf(q.getQuarterNumber()), ar ? "ar" : "en");
        return number + (q.getQuarterYear() != null ? " " + q.getQuarterYear() : "");
    }

    /** The "unspecified" word of LU_REPORT_LABEL in the report's language. */
    String unspecified(boolean ar) {
        return codeLists.label("report-label", ReportLabelCode.LBL_UNSPECIFIED, ar ? "ar" : "en");
    }

    /**
     * The quarter a packed {@code YYYYQ} code stands for: its MOHE_QUARTER row, or,
     * when the ministry has no row for the code, the year and quarter read from the
     * code itself. {@code null} for 0 (no recorded quarter) and for anything that is
     * not a YYYYQ code.
     */
    MoheQuarter resolveQuarter(Integer code, Map<Integer, MoheQuarter> quarters) {
        if (code == null) {
            return null;
        }
        MoheQuarter q = quarters.get(code);
        if (q != null) {
            return q;
        }
        int year = code / 10;
        int number = code % 10;
        if (year < 1000 || year > 9999 || number < 1 || number > 4) {
            return null;
        }
        MoheQuarter fromCode = new MoheQuarter();
        fromCode.setQuarter(code);
        fromCode.setQuarterYear(year);
        fromCode.setQuarterNumber(number);
        return fromCode;
    }

    /**
     * MOHE quarter dimension keyed by its packed code ({@code QUARTER}), not by the
     * {@code ID} identity column the entity is mapped on.
     */
    Map<Integer, MoheQuarter> quarterIndex() {
        return moheQuarterRepository.findAll().stream()
                .filter(q -> q.getQuarter() != null)
                .collect(Collectors.toMap(
                        MoheQuarter::getQuarter,
                        Function.identity(),
                        (a, b) -> a,
                        LinkedHashMap::new));
    }

    /** Quarter codes visible to the caller, honouring country scope. */
    public List<Integer> getDistinctQuarters() {
        countryScopeHelper.ensureScopeOrThrow();
        List<Integer> countryIds = countryScopeHelper.resolveCountryIds(null);
        return repository.findDistinctQuarters(
                countryScopeHelper.shouldApplyCountryFilter(countryIds),
                countryScopeHelper.safeList(countryIds));
    }
}
