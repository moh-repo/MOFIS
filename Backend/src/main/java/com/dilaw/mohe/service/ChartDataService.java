package com.dilaw.mohe.service;

import com.dilaw.mohe.codes.ReportLabelCode;
import com.dilaw.mohe.codes.StudyStatusCode;
import com.dilaw.mohe.dto.ChartDataDto;
import com.dilaw.mohe.dto.DashboardOverviewDto;
import com.dilaw.mohe.dto.dashboard.CodeCountDto;
import com.dilaw.mohe.service.codelist.CodeListService;
import com.dilaw.mohe.systemsettings.service.SysParameterService;
import com.dilaw.mohe.dto.DatasetDto;
import com.dilaw.mohe.dto.GeoBreakdownDto;
import com.dilaw.mohe.dto.GeoLocationDto;
import com.dilaw.mohe.dto.HierarchyNodeDto;
import com.dilaw.mohe.dto.SimpleDataPointDto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import com.dilaw.mohe.util.LiveStudentSql;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Transactional(readOnly = true)
@Service
public class ChartDataService {

    /**
     * Join used by every chart that counts students by study status.
     *
     * <p>These queries used to test {@code STUDENT_STUDY_STATUS_ID} against
     * literal IDs, on the assumption that 1=active, 2=graduated, 3=suspended,
     * 4=withdrawn. The table says something else entirely:
     *
     * <pre>
     *   0 Deceased    2 New         4 Suspended   7  Struggling
     *   1 Active      3 Withdrawn   5 Transferred 99 Not Specified
     * </pre>
     *
     * So "graduated" counted New students, and suspended/withdrawn were swapped.
     * Every such predicate now joins this table and tests {@code CODE}, the
     * stable key added by V028.
     *
     * <p>Note there is no GRADUATED code: this ministry does not record
     * graduation. Charts that used to plot a graduation series now plot a real
     * one instead of a permanently-wrong one.
     */
    private static final String STATUS_JOIN =
            "LEFT JOIN MOHECORE.STUDENT_STUDY_STATUS sss ON sss.STUDENT_STUDY_STATUS_ID = sd.STUDENT_STUDY_STATUS_ID ";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ChartCountrySqlHelper chartCountrySqlHelper;

    @Autowired
    private MessageSource messageSource;

    @Autowired
    private SysParameterService sysParameters;

    @Autowired
    private CodeListService codeLists;

    /**
     * A view row's name in the request's language.
     *
     * <p>The VW_* aggregates carry the Arabic name and, since V110, an English
     * sibling beside it. Falls back to Arabic when the lookup has no English
     * translation, so an untranslated row reads as itself rather than blank —
     * the same rule the report views use.
     *
     * <p>The three VW_*-backed charts use this directly; the inline-SQL charts
     * go through {@link #inlineLabel}.
     */
    private String localisedName(Map<String, Object> row, String arColumn, String enColumn) {
        String arabic = (String) row.get(arColumn);
        if (!"en".equalsIgnoreCase(LocaleContextHolder.getLocale().getLanguage())) {
            return arabic;
        }
        String english = (String) row.get(enColumn);
        return english != null && !english.isBlank() ? english : arabic;
    }

    /**
     * A label built in inline SQL, in the request's language (QA B6 / live QA #12).
     *
     * <p>These queries selected the Arabic lookup name with an English literal
     * fallback -- {@code COALESCE(country.STUDY_COUNTRY_IN_NAME, 'Unknown Country')}
     * -- so an English chart drew Arabic labels and an Arabic one said "Unknown".
     * Each now groups on the Arabic name exactly as before and carries
     * {@code MAX} of the English name beside it: the slices are the same in both
     * languages and only their labels change. A row with no Arabic name is
     * "unspecified" in the reader's language, whatever its English column holds.
     */
    private String inlineLabel(Map<String, Object> row, String arColumn, String enColumn) {
        return row.get(arColumn) == null ? unspecified() : localisedName(row, arColumn, enColumn);
    }

    /** LU_REPORT_LABEL's "unspecified", the wording the report views use (V109, V110). */
    private String unspecified() {
        String lang = "en".equalsIgnoreCase(LocaleContextHolder.getLocale().getLanguage()) ? "en" : "ar";
        return codeLists.label("report-label", ReportLabelCode.LBL_UNSPECIFIED, lang);
    }

    /** The root of a hierarchy chart; it was an English literal in both languages. */
    private String rootLabel(String key, String english) {
        return messageSource.getMessage(key, null, english, LocaleContextHolder.getLocale());
    }

    public ChartDataDto getStudentEnrollmentByCountry(Integer countryId) {
        ChartCountrySqlHelper.ScopeContext scope = chartCountrySqlHelper.resolveScope(countryId);
        String sql = "SELECT * FROM MOHECORE.VW_STUDENT_ENROLLMENT_BY_COUNTRY WHERE 1=1"
                + chartCountrySqlHelper.andViewCountryInClause("StudyCountryInId", scope)
                + " ORDER BY StudentCount DESC";
        List<Map<String, Object>> results = jdbcTemplate.queryForList(sql);

        List<String> labels = new ArrayList<>();
        List<Number> studentCounts = new ArrayList<>();
        List<Number> activeStudents = new ArrayList<>();
        List<Number> avgAmounts = new ArrayList<>();

        for (Map<String, Object> row : results) {
            // A null name is a student whose study country was never set; the client says so.
            labels.add(localisedName(row, "CountryName", "CountryNameEn"));
            studentCounts.add(row.get("StudentCount") != null ? ((Number) row.get("StudentCount")).intValue() : 0);
            activeStudents.add(row.get("ActiveStudents") != null ? ((Number) row.get("ActiveStudents")).intValue() : 0);
            avgAmounts.add(row.get("AvgAmount") != null ? ((Number) row.get("AvgAmount")).doubleValue() : 0);
        }

        // No graduated series: this ministry records no graduation (V075 dropped the column that
        // used to be computed from a name match that could never hit).
        List<DatasetDto> datasets = new ArrayList<>();
        datasets.add(
                new DatasetDto("Total Students", studentCounts, "rgba(54, 162, 235, 0.6)", "rgba(54, 162, 235, 1)"));
        datasets.add(
                new DatasetDto("Active Students", activeStudents, "rgba(75, 192, 192, 0.6)", "rgba(75, 192, 192, 1)"));

        return new ChartDataDto(labels, datasets);
    }

    /*
     * The four views below hold one row per label *and study country*. Drawn a row per
     * bar, a year, quarter, degree or status appeared once per country without the
     * country being named, so labels repeated (round-5 QA R5-31). They are totalled per
     * label over the countries in scope. Each student has one finance row, so student
     * counts and amounts add up; an average is total / students, which is what the
     * degree view computes, and a quarter's budget is one figure per quarter (MAX).
     */

    public ChartDataDto getFinancialDataByYear(Integer countryId) {
        ChartCountrySqlHelper.ScopeContext scope = chartCountrySqlHelper.resolveScope(countryId);
        String sql = "SELECT [Year], SUM(TotalAmount) AS TotalAmount, SUM(StudentCount) AS StudentCount, "
                + "CAST(SUM(TotalAmount) / NULLIF(SUM(StudentCount), 0) AS DECIMAL(18,2)) AS AvgAmount "
                + "FROM MOHECORE.VW_FINANCIAL_DATA_BY_YEAR WHERE 1=1"
                + chartCountrySqlHelper.andViewCountryInClause("StudyCountryInId", scope)
                + " GROUP BY [Year] ORDER BY [Year]";
        List<Map<String, Object>> results = jdbcTemplate.queryForList(sql);

        List<String> labels = new ArrayList<>();
        List<Number> totalAmounts = new ArrayList<>();
        List<Number> avgAmounts = new ArrayList<>();
        List<Number> studentCounts = new ArrayList<>();

        for (Map<String, Object> row : results) {
            labels.add(row.get("Year").toString());
            totalAmounts.add(row.get("TotalAmount") != null ? ((Number) row.get("TotalAmount")).doubleValue() : 0);
            avgAmounts.add(row.get("AvgAmount") != null ? ((Number) row.get("AvgAmount")).doubleValue() : 0);
            studentCounts.add(row.get("StudentCount") != null ? ((Number) row.get("StudentCount")).intValue() : 0);
        }

        List<DatasetDto> datasets = new ArrayList<>();
        datasets.add(new DatasetDto("Total Amount", totalAmounts, "rgba(255, 99, 132, 0.6)", "rgba(255, 99, 132, 1)"));
        datasets.add(new DatasetDto("Average Amount", avgAmounts, "rgba(54, 162, 235, 0.6)", "rgba(54, 162, 235, 1)"));
        datasets.add(
                new DatasetDto("Student Count", studentCounts, "rgba(75, 192, 192, 0.6)", "rgba(75, 192, 192, 1)"));

        return new ChartDataDto(labels, datasets);
    }

    public ChartDataDto getStudentsByDegree(Integer countryId) {
        ChartCountrySqlHelper.ScopeContext scope = chartCountrySqlHelper.resolveScope(countryId);
        String sql = "SELECT DegreeName, DegreeNameEn, SUM(StudentCount) AS StudentCount, "
                + "SUM(TotalAmount) AS TotalAmount, "
                + "CAST(SUM(TotalAmount) / NULLIF(SUM(StudentCount), 0) AS DECIMAL(18,2)) AS AvgAmount "
                + "FROM MOHECORE.VW_STUDENT_BY_DEGREE WHERE 1=1"
                + chartCountrySqlHelper.andViewCountryInClause("StudyCountryInId", scope)
                + " GROUP BY DegreeName, DegreeNameEn ORDER BY SUM(StudentCount) DESC";
        List<Map<String, Object>> results = jdbcTemplate.queryForList(sql);

        List<String> labels = new ArrayList<>();
        List<Number> studentCounts = new ArrayList<>();
        List<Number> totalAmounts = new ArrayList<>();
        List<Number> avgAmounts = new ArrayList<>();

        for (Map<String, Object> row : results) {
            String degreeName = localisedName(row, "DegreeName", "DegreeNameEn");
            if (degreeName != null) {
                labels.add(degreeName);
                studentCounts.add(row.get("StudentCount") != null ? ((Number) row.get("StudentCount")).intValue() : 0);
                totalAmounts.add(row.get("TotalAmount") != null ? ((Number) row.get("TotalAmount")).doubleValue() : 0);
                avgAmounts.add(row.get("AvgAmount") != null ? ((Number) row.get("AvgAmount")).doubleValue() : 0);
            }
        }

        List<DatasetDto> datasets = new ArrayList<>();
        datasets.add(
                new DatasetDto("Student Count", studentCounts, "rgba(153, 102, 255, 0.6)", "rgba(153, 102, 255, 1)"));
        datasets.add(new DatasetDto("Total Amount", totalAmounts, "rgba(255, 159, 64, 0.6)", "rgba(255, 159, 64, 1)"));

        return new ChartDataDto(labels, datasets);
    }

    public ChartDataDto getFinancialByQuarter(Integer countryId) {
        ChartCountrySqlHelper.ScopeContext scope = chartCountrySqlHelper.resolveScope(countryId);
        String sql = "SELECT QuarterName, SUM(TotalReceived) AS TotalReceived, MAX(TotalBudget) AS TotalBudget, "
                + "SUM(StudentCount) AS StudentCount "
                + "FROM MOHECORE.VW_FINANCIAL_BY_QUARTER WHERE 1=1"
                + chartCountrySqlHelper.andViewCountryInClause("StudyCountryInId", scope)
                + " GROUP BY QuarterName ORDER BY QuarterName";
        List<Map<String, Object>> results = jdbcTemplate.queryForList(sql);

        List<String> labels = new ArrayList<>();
        List<Number> budgets = new ArrayList<>();
        List<Number> received = new ArrayList<>();
        List<Number> studentCounts = new ArrayList<>();

        for (Map<String, Object> row : results) {
            String quarterName = (String) row.get("QuarterName");
            if (quarterName != null) {
                labels.add(quarterName);
                budgets.add(row.get("TotalBudget") != null ? ((Number) row.get("TotalBudget")).doubleValue() : 0);
                received.add(row.get("TotalReceived") != null ? ((Number) row.get("TotalReceived")).doubleValue() : 0);
                studentCounts.add(row.get("StudentCount") != null ? ((Number) row.get("StudentCount")).intValue() : 0);
            }
        }

        List<DatasetDto> datasets = new ArrayList<>();
        datasets.add(new DatasetDto("Budgeted Amount", budgets, "rgba(255, 206, 86, 0.6)", "rgba(255, 206, 86, 1)"));
        datasets.add(new DatasetDto("Received Amount", received, "rgba(75, 192, 192, 0.6)", "rgba(75, 192, 192, 1)"));
        datasets.add(
                new DatasetDto("Student Count", studentCounts, "rgba(54, 162, 235, 0.6)", "rgba(54, 162, 235, 1)"));

        return new ChartDataDto(labels, datasets);
    }

    public ChartDataDto getStudentStatusDistribution(Integer countryId) {
        ChartCountrySqlHelper.ScopeContext scope = chartCountrySqlHelper.resolveScope(countryId);
        String sql = "SELECT StatusName, StatusNameEn, SUM(StudentCount) AS StudentCount, "
                + "SUM(TotalAmount) AS TotalAmount "
                + "FROM MOHECORE.VW_STUDENT_STATUS_DISTRIBUTION WHERE 1=1"
                + chartCountrySqlHelper.andViewCountryInClause("StudyCountryInId", scope)
                + " GROUP BY StatusName, StatusNameEn ORDER BY SUM(StudentCount) DESC";
        List<Map<String, Object>> results = jdbcTemplate.queryForList(sql);

        List<String> labels = new ArrayList<>();
        List<Number> studentCounts = new ArrayList<>();
        List<Number> totalAmounts = new ArrayList<>();

        for (Map<String, Object> row : results) {
            String statusName = localisedName(row, "StatusName", "StatusNameEn");
            if (statusName != null) {
                labels.add(statusName);
                studentCounts.add(row.get("StudentCount") != null ? ((Number) row.get("StudentCount")).intValue() : 0);
                totalAmounts.add(row.get("TotalAmount") != null ? ((Number) row.get("TotalAmount")).doubleValue() : 0);
            }
        }

        List<DatasetDto> datasets = new ArrayList<>();
        datasets.add(
                new DatasetDto("Student Count", studentCounts, "rgba(255, 99, 132, 0.6)", "rgba(255, 99, 132, 1)"));
        datasets.add(new DatasetDto("Total Amount", totalAmounts, "rgba(54, 162, 235, 0.6)", "rgba(54, 162, 235, 1)"));

        return new ChartDataDto(labels, datasets);
    }

    public ChartDataDto getStudentsByMajorDistribution(Integer countryId) {
        ChartCountrySqlHelper.ScopeContext scope = chartCountrySqlHelper.resolveScope(countryId);
        // Grouped on the free-text major as before; the English name comes from the
        // STUDENT_MAJOR row the student also carries (see inlineLabel).
        String sql = "SELECT TOP 12 " +
                "NULLIF(sd.STUDENT_MAJOR_NAME, '') AS MajorName, " +
                "MAX(sm.STUDENT_MAJOR_NAME_EN) AS MajorNameEn, " +
                "COUNT(*) AS StudentCount " +
                "FROM MOHECORE.STUDENT_DATA sd " +
                "LEFT JOIN MOHECORE.STUDENT_MAJOR sm ON sm.STUDENT_MAJOR_ID = sd.STUDENT_MAJOR_ID " +
                "WHERE ISNULL(sd.IS_DELETED,0)=0" +
                chartCountrySqlHelper.studentDataInClause("sd", scope) +
                " GROUP BY NULLIF(sd.STUDENT_MAJOR_NAME, '') " +
                "ORDER BY StudentCount DESC";

        List<Map<String, Object>> results = jdbcTemplate.queryForList(sql);

        List<String> labels = new ArrayList<>();
        List<Number> studentCounts = new ArrayList<>();

        for (Map<String, Object> row : results) {
            labels.add(inlineLabel(row, "MajorName", "MajorNameEn"));
            studentCounts.add(row.get("StudentCount") != null ? ((Number) row.get("StudentCount")).intValue() : 0);
        }

        List<DatasetDto> datasets = new ArrayList<>();
        datasets.add(new DatasetDto("Students", studentCounts, "rgba(59, 130, 246, 0.7)", "rgba(59, 130, 246, 1)"));

        return new ChartDataDto(labels, datasets);
    }

    public ChartDataDto getStudentsByGenderDistribution(Integer countryId) {
        ChartCountrySqlHelper.ScopeContext scope = chartCountrySqlHelper.resolveScope(countryId);
        /*
         * Students with no gender row come back with a NULL name and are labelled
         * in Java from the bundle. The label used to be a bilingual literal inside
         * the SQL — both languages at once, whatever the caller asked for.
         */
        String sql = "SELECT " +
                "g.STUDENT_GENDER_NAME AS GenderName, " +
                "MAX(g.STUDENT_GENDER_NAME_EN) AS GenderNameEn, " +
                "COUNT(*) AS StudentCount " +
                "FROM MOHECORE.STUDENT_DATA sd " +
                "LEFT JOIN MOHECORE.STUDENT_GENDER g ON sd.STUDENT_GENDER = g.STUDENT_GENDER_ID " +
                "WHERE ISNULL(sd.IS_DELETED,0)=0" +
                chartCountrySqlHelper.studentDataInClause("sd", scope) +
                " GROUP BY g.STUDENT_GENDER_NAME " +
                "ORDER BY StudentCount DESC";

        List<Map<String, Object>> results = jdbcTemplate.queryForList(sql);

        List<String> labels = new ArrayList<>();
        List<Number> studentCounts = new ArrayList<>();

        String unspecified = messageSource.getMessage("chart.label.unspecified", null,
                "Unspecified", LocaleContextHolder.getLocale());
        for (Map<String, Object> row : results) {
            String name = localisedName(row, "GenderName", "GenderNameEn");
            labels.add(name != null && !name.isBlank() ? name : unspecified);
            studentCounts.add(row.get("StudentCount") != null ? ((Number) row.get("StudentCount")).intValue() : 0);
        }

        List<DatasetDto> datasets = new ArrayList<>();
        datasets.add(new DatasetDto("Students", studentCounts, "rgba(16, 185, 129, 0.7)", "rgba(16, 185, 129, 1)"));

        return new ChartDataDto(labels, datasets);
    }

    public ChartDataDto getStudentsByUniversityTypeDistribution(Integer countryId) {
        ChartCountrySqlHelper.ScopeContext scope = chartCountrySqlHelper.resolveScope(countryId);
        String sql = "SELECT " +
                "ut.STUDENT_UNIVERSITY_TYPE_NAME AS UniversityTypeName, " +
                "MAX(ut.STUDENT_UNIVERSITY_TYPE_NAME_EN) AS UniversityTypeNameEn, " +
                "COUNT(*) AS StudentCount " +
                "FROM MOHECORE.STUDENT_DATA sd " +
                "LEFT JOIN MOHECORE.STUDENT_UNIVERSITY_TYPE ut ON sd.STUDENT_UNIVERSITY_TYPE_ID = ut.STUDENT_UNIVERSITY_TYPE_ID "
                +
                "WHERE ISNULL(sd.IS_DELETED,0)=0" +
                chartCountrySqlHelper.studentDataInClause("sd", scope) +
                " GROUP BY ut.STUDENT_UNIVERSITY_TYPE_NAME " +
                "ORDER BY StudentCount DESC";

        List<Map<String, Object>> results = jdbcTemplate.queryForList(sql);

        List<String> labels = new ArrayList<>();
        List<Number> studentCounts = new ArrayList<>();

        for (Map<String, Object> row : results) {
            labels.add(inlineLabel(row, "UniversityTypeName", "UniversityTypeNameEn"));
            studentCounts.add(row.get("StudentCount") != null ? ((Number) row.get("StudentCount")).intValue() : 0);
        }

        List<DatasetDto> datasets = new ArrayList<>();
        datasets.add(new DatasetDto("Students", studentCounts, "rgba(236, 72, 153, 0.7)", "rgba(236, 72, 153, 1)"));

        return new ChartDataDto(labels, datasets);
    }

    /**
     * Students per study status, most numerous first. Each row carries the status
     * row itself (both names, the V072 {@code UI_COLOR}); a student whose status
     * is unset or unknown to the table comes back with a null code and the
     * client says "unspecified" in its own language — no {@code 'Unknown Status'}
     * literal is invented here.
     */
    public List<CodeCountDto> studentsByStatusRows(Integer countryId) {
        ChartCountrySqlHelper.ScopeContext scope = chartCountrySqlHelper.resolveScope(countryId);
        String sql = "SELECT sss.CODE AS StatusCode, COUNT(*) AS StudentCount " +
                "FROM MOHECORE.STUDENT_DATA sd " +
                STATUS_JOIN +
                "WHERE ISNULL(sd.IS_DELETED,0)=0" +
                chartCountrySqlHelper.studentDataInClause("sd", scope) +
                " GROUP BY sss.CODE " +
                "ORDER BY StudentCount DESC";

        List<CodeCountDto> rows = new ArrayList<>();
        int i = 0;
        for (Map<String, Object> row : jdbcTemplate.queryForList(sql)) {
            String code = (String) row.get("StatusCode");
            double count = row.get("StudentCount") != null ? ((Number) row.get("StudentCount")).doubleValue() : 0.0;
            rows.add(codeLists.find("study-status", code)
                    .map(r -> CodeCountDto.of(r, count))
                    .orElse(new CodeCountDto(code, null, null, null, null, null, i, count)));
            i++;
        }
        return rows;
    }

    /** The legacy pie-chart body: labels are the status names in the request's language. */
    public ChartDataDto getStudentsByStudyStatusDistribution(Integer countryId) {
        String lang = LocaleContextHolder.getLocale().getLanguage();
        List<String> labels = new ArrayList<>();
        List<Number> studentCounts = new ArrayList<>();
        for (CodeCountDto row : studentsByStatusRows(countryId)) {
            String name = "en".equalsIgnoreCase(lang) ? row.nameEn() : row.nameAr();
            labels.add(name != null ? name : unspecifiedLabel());
            studentCounts.add((int) row.count());
        }
        List<DatasetDto> datasets = new ArrayList<>();
        datasets.add(new DatasetDto("Students", studentCounts, "rgba(249, 115, 22, 0.7)", "rgba(249, 115, 22, 1)"));
        return new ChartDataDto(labels, datasets);
    }

    private String unspecifiedLabel() {
        return messageSource.getMessage("chart.label.unspecified", null, LocaleContextHolder.getLocale());
    }

    public ChartDataDto getEnrollmentGrowthTrend(Integer countryId) {
        ChartCountrySqlHelper.ScopeContext scope = chartCountrySqlHelper.resolveScope(countryId);
        // Second series is new enrolments, not graduates: an enrolment-growth
        // chart wants intake, and graduation is not recorded (see STATUS_JOIN).
        String sql = "SELECT sd.STUDENT_DELEGATION_DECISION_YEAR AS Year, " +
                "COUNT(*) AS StudentCount, " +
                "SUM(CASE WHEN sss.CODE = 'ACTIVE' THEN 1 ELSE 0 END) AS ActiveCount, " +
                "SUM(CASE WHEN sss.CODE = 'NEW' THEN 1 ELSE 0 END) AS NewCount " +
                "FROM MOHECORE.STUDENT_DATA sd " +
                STATUS_JOIN +
                "WHERE sd.STUDENT_DELEGATION_DECISION_YEAR IS NOT NULL " +
                "AND ISNULL(sd.IS_DELETED,0)=0" +
                chartCountrySqlHelper.studentDataInClause("sd", scope) +
                " GROUP BY sd.STUDENT_DELEGATION_DECISION_YEAR " +
                "ORDER BY sd.STUDENT_DELEGATION_DECISION_YEAR";

        List<Map<String, Object>> results = jdbcTemplate.queryForList(sql);

        List<String> labels = new ArrayList<>();
        List<Number> studentCounts = new ArrayList<>();
        List<Number> activeCounts = new ArrayList<>();
        List<Number> newCounts = new ArrayList<>();

        for (Map<String, Object> row : results) {
            Object yearValue = row.get("Year");
            if (yearValue != null) {
                labels.add(yearValue.toString());
                studentCounts.add(row.get("StudentCount") != null ? ((Number) row.get("StudentCount")).intValue() : 0);
                activeCounts.add(row.get("ActiveCount") != null ? ((Number) row.get("ActiveCount")).intValue() : 0);
                newCounts.add(row.get("NewCount") != null ? ((Number) row.get("NewCount")).intValue() : 0);
            }
        }

        List<DatasetDto> datasets = new ArrayList<>();
        datasets.add(
                new DatasetDto("Student Count", studentCounts, "rgba(99, 102, 241, 0.25)", "rgba(99, 102, 241, 1)"));
        datasets.add(
                new DatasetDto("Active Students", activeCounts, "rgba(16, 185, 129, 0.2)", "rgba(16, 185, 129, 1)"));
        datasets.add(new DatasetDto("New Students", newCounts, "rgba(249, 115, 22, 0.2)",
                "rgba(249, 115, 22, 1)"));

        return new ChartDataDto(labels, datasets);
    }

    public ChartDataDto getPaymentPatternsTrend(Integer countryId) {
        ChartCountrySqlHelper.ScopeContext scope = chartCountrySqlHelper.resolveScope(countryId);
        String sql = "SELECT sf.STUDENT_FIN_RECIEVED_YEAR AS Year, " +
                "SUM(COALESCE(sf.NET_AMOUNT, 0)) AS TotalAmount, " +
                "AVG(COALESCE(sf.NET_AMOUNT, 0)) AS AverageAmount, " +
                "COUNT(*) AS PaymentCount " +
                "FROM MOHECORE.STUDENT_FINANCE sf " +
                "WHERE sf.STUDENT_FIN_RECIEVED_YEAR IS NOT NULL AND " + LiveStudentSql.ofLiveStudent("sf") +
                chartCountrySqlHelper.financeDirectInClause("sf", scope) +
                " GROUP BY sf.STUDENT_FIN_RECIEVED_YEAR " +
                "ORDER BY sf.STUDENT_FIN_RECIEVED_YEAR";

        List<Map<String, Object>> results = jdbcTemplate.queryForList(sql);

        List<String> labels = new ArrayList<>();
        List<Number> totalAmounts = new ArrayList<>();
        List<Number> averageAmounts = new ArrayList<>();
        List<Number> paymentCounts = new ArrayList<>();

        for (Map<String, Object> row : results) {
            Object yearValue = row.get("Year");
            if (yearValue != null) {
                labels.add(yearValue.toString());
                totalAmounts.add(row.get("TotalAmount") != null ? ((Number) row.get("TotalAmount")).doubleValue() : 0);
                averageAmounts
                        .add(row.get("AverageAmount") != null ? ((Number) row.get("AverageAmount")).doubleValue() : 0);
                paymentCounts.add(row.get("PaymentCount") != null ? ((Number) row.get("PaymentCount")).intValue() : 0);
            }
        }

        List<DatasetDto> datasets = new ArrayList<>();
        datasets.add(
                new DatasetDto("Total Disbursed", totalAmounts, "rgba(59, 130, 246, 0.25)", "rgba(59, 130, 246, 1)"));
        datasets.add(new DatasetDto("Average Disbursement", averageAmounts, "rgba(244, 114, 182, 0.25)",
                "rgba(244, 114, 182, 1)"));
        datasets.add(
                new DatasetDto("Payment Volume", paymentCounts, "rgba(34, 197, 94, 0.25)", "rgba(34, 197, 94, 1)"));

        return new ChartDataDto(labels, datasets);
    }

    public ChartDataDto getAcademicProgressTrend(Integer countryId) {
        ChartCountrySqlHelper.ScopeContext scope = chartCountrySqlHelper.resolveScope(countryId);
        String sql = "SELECT sd.STUDENT_DELEGATION_DECISION_YEAR AS Year, " +
                "AVG(COALESCE(sd.STUDENT_CURRENT_LEVEL_ID, 0)) AS AvgLevel, " +
                "SUM(CASE WHEN sss.CODE = 'ACTIVE' THEN 1 ELSE 0 END) AS ActiveCount, " +
                "SUM(CASE WHEN sss.CODE = 'NEW' THEN 1 ELSE 0 END) AS NewCount " +
                "FROM MOHECORE.STUDENT_DATA sd " +
                STATUS_JOIN +
                "WHERE sd.STUDENT_DELEGATION_DECISION_YEAR IS NOT NULL " +
                "AND ISNULL(sd.IS_DELETED,0)=0" +
                chartCountrySqlHelper.studentDataInClause("sd", scope) +
                " GROUP BY sd.STUDENT_DELEGATION_DECISION_YEAR " +
                "ORDER BY sd.STUDENT_DELEGATION_DECISION_YEAR";

        List<Map<String, Object>> results = jdbcTemplate.queryForList(sql);

        List<String> labels = new ArrayList<>();
        List<Number> averageLevels = new ArrayList<>();
        List<Number> activeCounts = new ArrayList<>();
        List<Number> newCounts = new ArrayList<>();

        for (Map<String, Object> row : results) {
            Object yearValue = row.get("Year");
            if (yearValue != null) {
                labels.add(yearValue.toString());
                averageLevels.add(row.get("AvgLevel") != null ? ((Number) row.get("AvgLevel")).doubleValue() : 0);
                activeCounts.add(row.get("ActiveCount") != null ? ((Number) row.get("ActiveCount")).intValue() : 0);
                newCounts.add(row.get("NewCount") != null ? ((Number) row.get("NewCount")).intValue() : 0);
            }
        }

        List<DatasetDto> datasets = new ArrayList<>();
        datasets.add(
                new DatasetDto("Average Level", averageLevels, "rgba(168, 85, 247, 0.25)", "rgba(168, 85, 247, 1)"));
        datasets.add(
                new DatasetDto("Active Students", activeCounts, "rgba(34, 197, 94, 0.25)", "rgba(34, 197, 94, 1)"));
        datasets.add(new DatasetDto("New Students", newCounts, "rgba(250, 204, 21, 0.25)",
                "rgba(250, 204, 21, 1)"));

        return new ChartDataDto(labels, datasets);
    }

    public ChartDataDto getQuarterlyDisbursementTrend(Integer countryId) {
        ChartCountrySqlHelper.ScopeContext scope = chartCountrySqlHelper.resolveScope(countryId);
        // STUDENT_FIN_RECIEVED_MONTH holds the quarter, 1-4, despite its name: the
        // period key is YEAR * 10 + MONTH (see StudentFinance). It was folded as if it
        // were a calendar month, ((m - 1) / 3) + 1, which puts quarters 1, 2 and 3 all
        // in Q1: round-5 QA R5-31 saw "2024 Q1, 2025 Q1, 2026 Q1" for money received in
        // Q2 2024, Q1 2025 and Q3 2026.
        String sql = "SELECT sf.STUDENT_FIN_RECIEVED_YEAR AS Year, " +
                "sf.STUDENT_FIN_RECIEVED_MONTH AS Quarter, " +
                "SUM(COALESCE(sf.NET_AMOUNT, 0)) AS TotalAmount, " +
                "AVG(COALESCE(sf.NET_AMOUNT, 0)) AS AverageAmount, " +
                "COUNT(*) AS PaymentCount " +
                "FROM MOHECORE.STUDENT_FINANCE sf " +
                "WHERE sf.STUDENT_FIN_RECIEVED_YEAR IS NOT NULL " +
                "AND sf.STUDENT_FIN_RECIEVED_MONTH BETWEEN 1 AND 4 AND " + LiveStudentSql.ofLiveStudent("sf") +
                chartCountrySqlHelper.financeDirectInClause("sf", scope) +
                " GROUP BY sf.STUDENT_FIN_RECIEVED_YEAR, sf.STUDENT_FIN_RECIEVED_MONTH " +
                "ORDER BY sf.STUDENT_FIN_RECIEVED_YEAR, sf.STUDENT_FIN_RECIEVED_MONTH";

        List<Map<String, Object>> results = jdbcTemplate.queryForList(sql);

        List<String> labels = new ArrayList<>();
        List<Number> totalAmounts = new ArrayList<>();
        List<Number> averageAmounts = new ArrayList<>();
        List<Number> paymentCounts = new ArrayList<>();

        for (Map<String, Object> row : results) {
            Object yearValue = row.get("Year");
            Object quarterValue = row.get("Quarter");
            if (yearValue != null && quarterValue != null) {
                String label = yearValue.toString() + " Q" + quarterValue.toString();
                labels.add(label);
                totalAmounts.add(row.get("TotalAmount") != null ? ((Number) row.get("TotalAmount")).doubleValue() : 0);
                averageAmounts
                        .add(row.get("AverageAmount") != null ? ((Number) row.get("AverageAmount")).doubleValue() : 0);
                paymentCounts.add(row.get("PaymentCount") != null ? ((Number) row.get("PaymentCount")).intValue() : 0);
            }
        }

        List<DatasetDto> datasets = new ArrayList<>();
        datasets.add(
                new DatasetDto("Total Disbursed", totalAmounts, "rgba(37, 99, 235, 0.25)", "rgba(37, 99, 235, 1)"));
        datasets.add(new DatasetDto("Average Disbursement", averageAmounts, "rgba(236, 72, 153, 0.25)",
                "rgba(236, 72, 153, 1)"));
        datasets.add(
                new DatasetDto("Payment Count", paymentCounts, "rgba(16, 185, 129, 0.25)", "rgba(16, 185, 129, 1)"));

        return new ChartDataDto(labels, datasets);
    }

    public ChartDataDto getStudentRetentionTrend(Integer countryId) {
        ChartCountrySqlHelper.ScopeContext scope = chartCountrySqlHelper.resolveScope(countryId);
        String sql = "SELECT sd.STUDENT_DELEGATION_DECISION_YEAR AS Year, " +
                // IDs 3 and 4 were the wrong way round here: 3 is Withdrawn and
                // 4 is Suspended, so the two series were swapped.
                "SUM(CASE WHEN sss.CODE = 'ACTIVE' THEN 1 ELSE 0 END) AS ActiveCount, " +
                "SUM(CASE WHEN sss.CODE = 'SUSPENDED' THEN 1 ELSE 0 END) AS SuspendedCount, " +
                "SUM(CASE WHEN sss.CODE = 'WITHDRAWN' THEN 1 ELSE 0 END) AS WithdrawnCount " +
                "FROM MOHECORE.STUDENT_DATA sd " +
                STATUS_JOIN +
                "WHERE sd.STUDENT_DELEGATION_DECISION_YEAR IS NOT NULL " +
                "AND ISNULL(sd.IS_DELETED,0)=0" +
                chartCountrySqlHelper.studentDataInClause("sd", scope) +
                " GROUP BY sd.STUDENT_DELEGATION_DECISION_YEAR " +
                "ORDER BY sd.STUDENT_DELEGATION_DECISION_YEAR";

        List<Map<String, Object>> results = jdbcTemplate.queryForList(sql);

        List<String> labels = new ArrayList<>();
        List<Number> activeCounts = new ArrayList<>();
        List<Number> suspendedCounts = new ArrayList<>();
        List<Number> withdrawnCounts = new ArrayList<>();

        for (Map<String, Object> row : results) {
            Object yearValue = row.get("Year");
            if (yearValue != null) {
                labels.add(yearValue.toString());
                activeCounts.add(row.get("ActiveCount") != null ? ((Number) row.get("ActiveCount")).intValue() : 0);
                suspendedCounts
                        .add(row.get("SuspendedCount") != null ? ((Number) row.get("SuspendedCount")).intValue() : 0);
                withdrawnCounts
                        .add(row.get("WithdrawnCount") != null ? ((Number) row.get("WithdrawnCount")).intValue() : 0);
            }
        }

        List<DatasetDto> datasets = new ArrayList<>();
        datasets.add(
                new DatasetDto("Active Students", activeCounts, "rgba(16, 185, 129, 0.25)", "rgba(16, 185, 129, 1)"));
        datasets.add(new DatasetDto("Suspended Students", suspendedCounts, "rgba(234, 179, 8, 0.25)",
                "rgba(234, 179, 8, 1)"));
        datasets.add(new DatasetDto("Withdrawn Students", withdrawnCounts, "rgba(248, 113, 113, 0.25)",
                "rgba(248, 113, 113, 1)"));

        return new ChartDataDto(labels, datasets);
    }

    public HierarchyNodeDto getLocationHierarchy(Integer countryId) {
        ChartCountrySqlHelper.ScopeContext scope = chartCountrySqlHelper.resolveScope(countryId);
        String sql = "SELECT " +
                "country.STUDY_COUNTRY_IN_NAME AS CountryName, " +
                "MAX(country.STUDY_COUNTRY_IN_NAME_EN) AS CountryNameEn, " +
                "gov.STUDY_GOV_IN_NAME AS GovernmentName, " +
                "MAX(gov.STUDY_GOV_IN_NAME_EN) AS GovernmentNameEn, " +
                "COUNT(*) AS StudentCount " +
                "FROM MOHECORE.STUDENT_DATA sd " +
                "LEFT JOIN MOHECORE.STUDY_COUNTRY_IN country ON sd.STUDY_COUNTRY_IN = country.STUDY_COUNTRY_IN_ID " +
                "LEFT JOIN MOHECORE.STUDY_GOV_IN gov ON sd.SYUDY_GOV_IN = gov.STUDY_GOV_IN_ID " +
                "WHERE ISNULL(sd.IS_DELETED,0)=0" +
                chartCountrySqlHelper.studentDataInClause("sd", scope) +
                " GROUP BY country.STUDY_COUNTRY_IN_NAME, gov.STUDY_GOV_IN_NAME " +
                "ORDER BY StudentCount DESC";

        List<Map<String, Object>> results = jdbcTemplate.queryForList(sql);

        Map<String, HierarchyNodeDto> countryMap = new LinkedHashMap<>();
        long total = 0L;

        for (Map<String, Object> row : results) {
            String countryName = inlineLabel(row, "CountryName", "CountryNameEn");
            String governmentName = inlineLabel(row, "GovernmentName", "GovernmentNameEn");
            long studentCount = row.get("StudentCount") != null ? ((Number) row.get("StudentCount")).longValue() : 0L;

            total += studentCount;

            HierarchyNodeDto countryNode = countryMap.computeIfAbsent(countryName, key -> HierarchyNodeDto.builder()
                    .label(key)
                    .value(0L)
                    .children(new ArrayList<>())
                    .build());

            countryNode.setValue(countryNode.getValue() + studentCount);
            countryNode.addChild(HierarchyNodeDto.builder()
                    .label(governmentName)
                    .value(studentCount)
                    .children(new ArrayList<>())
                    .build());
        }

        return HierarchyNodeDto.builder()
                .label(rootLabel("chart.hierarchy.location.root", "All Countries"))
                .value(total)
                .children(new ArrayList<>(countryMap.values()))
                .build();
    }

    public HierarchyNodeDto getOrganizationHierarchy(Integer countryId) {
        ChartCountrySqlHelper.ScopeContext scope = chartCountrySqlHelper.resolveScope(countryId);
        String sql = "SELECT " +
                "agency.STUDENT_AGENCY_TYPE_NAME AS AgencyName, " +
                "MAX(agency.STUDENT_AGENCY_TYPE_NAME_EN) AS AgencyNameEn, " +
                "ministry.STUDENT_MINISTRY_TYPE_NAME AS MinistryName, " +
                "MAX(ministry.STUDENT_MINISTRY_TYPE_NAME_EN) AS MinistryNameEn, " +
                "university.STUDENT_UNIVERSITY_TYPE_NAME AS UniversityTypeName, " +
                "MAX(university.STUDENT_UNIVERSITY_TYPE_NAME_EN) AS UniversityTypeNameEn, " +
                "COUNT(*) AS StudentCount " +
                "FROM MOHECORE.STUDENT_DATA sd " +
                "LEFT JOIN MOHECORE.STUDENT_AGENCY_TYPE agency ON sd.STUDENT_AGENCY_TYPE_ID = agency.STUDENT_AGENCY_TYPE_ID "
                +
                "LEFT JOIN MOHECORE.STUDENT_MINISTRY_TYPE ministry ON sd.STUDENT_MINISTRY_TYPE_ID = ministry.STUDENT_MINISTRY_TYPE_ID "
                +
                "LEFT JOIN MOHECORE.STUDENT_UNIVERSITY_TYPE university ON sd.STUDENT_UNIVERSITY_TYPE_ID = university.STUDENT_UNIVERSITY_TYPE_ID "
                +
                "WHERE ISNULL(sd.IS_DELETED,0)=0" +
                chartCountrySqlHelper.studentDataInClause("sd", scope) +
                " GROUP BY agency.STUDENT_AGENCY_TYPE_NAME, ministry.STUDENT_MINISTRY_TYPE_NAME, " +
                "university.STUDENT_UNIVERSITY_TYPE_NAME " +
                "ORDER BY StudentCount DESC";

        List<Map<String, Object>> results = jdbcTemplate.queryForList(sql);

        Map<String, HierarchyNodeDto> agencyMap = new LinkedHashMap<>();
        long total = 0L;

        for (Map<String, Object> row : results) {
            String agencyName = inlineLabel(row, "AgencyName", "AgencyNameEn");
            String ministryName = inlineLabel(row, "MinistryName", "MinistryNameEn");
            String universityTypeName = inlineLabel(row, "UniversityTypeName", "UniversityTypeNameEn");
            long studentCount = row.get("StudentCount") != null ? ((Number) row.get("StudentCount")).longValue() : 0L;

            total += studentCount;

            HierarchyNodeDto agencyNode = agencyMap.computeIfAbsent(agencyName, key -> HierarchyNodeDto.builder()
                    .label(key)
                    .value(0L)
                    .children(new ArrayList<>())
                    .build());
            agencyNode.setValue(agencyNode.getValue() + studentCount);

            HierarchyNodeDto ministryNode = agencyNode.getChildren().stream()
                    .filter(child -> child.getLabel().equals(ministryName))
                    .findFirst()
                    .orElseGet(() -> {
                        HierarchyNodeDto node = HierarchyNodeDto.builder()
                                .label(ministryName)
                                .value(0L)
                                .children(new ArrayList<>())
                                .build();
                        agencyNode.addChild(node);
                        return node;
                    });

            ministryNode.setValue(ministryNode.getValue() + studentCount);
            ministryNode.addChild(HierarchyNodeDto.builder()
                    .label(universityTypeName)
                    .value(studentCount)
                    .children(new ArrayList<>())
                    .build());
        }

        return HierarchyNodeDto.builder()
                .label(rootLabel("chart.hierarchy.organization.root", "Scholarship Agencies"))
                .value(total)
                .children(new ArrayList<>(agencyMap.values()))
                .build();
    }

    public HierarchyNodeDto getAcademicHierarchy(Integer countryId) {
        ChartCountrySqlHelper.ScopeContext scope = chartCountrySqlHelper.resolveScope(countryId);
        String sql = "SELECT " +
                "NULLIF(sd.STUDENT_MAJOR_NAME, '') AS MajorName, " +
                "MAX(sm.STUDENT_MAJOR_NAME_EN) AS MajorNameEn, " +
                "status.STUDENT_STUDY_STATUS_NAME AS StatusName, " +
                "MAX(status.STUDENT_STUDY_STATUS_NAME_EN) AS StatusNameEn, " +
                "gender.STUDENT_GENDER_NAME AS GenderName, " +
                "MAX(gender.STUDENT_GENDER_NAME_EN) AS GenderNameEn, " +
                "COUNT(*) AS StudentCount " +
                "FROM MOHECORE.STUDENT_DATA sd " +
                "LEFT JOIN MOHECORE.STUDENT_MAJOR sm ON sm.STUDENT_MAJOR_ID = sd.STUDENT_MAJOR_ID " +
                "LEFT JOIN MOHECORE.STUDENT_STUDY_STATUS status ON sd.STUDENT_STUDY_STATUS_ID = status.STUDENT_STUDY_STATUS_ID "
                +
                "LEFT JOIN MOHECORE.STUDENT_GENDER gender ON sd.STUDENT_GENDER = gender.STUDENT_GENDER_ID " +
                "WHERE ISNULL(sd.IS_DELETED,0)=0" +
                chartCountrySqlHelper.studentDataInClause("sd", scope) +
                " GROUP BY NULLIF(sd.STUDENT_MAJOR_NAME, ''), status.STUDENT_STUDY_STATUS_NAME, " +
                "gender.STUDENT_GENDER_NAME " +
                "ORDER BY StudentCount DESC";

        List<Map<String, Object>> results = jdbcTemplate.queryForList(sql);

        Map<String, HierarchyNodeDto> majorMap = new LinkedHashMap<>();
        long total = 0L;

        for (Map<String, Object> row : results) {
            String majorName = inlineLabel(row, "MajorName", "MajorNameEn");
            String statusName = inlineLabel(row, "StatusName", "StatusNameEn");
            String genderName = inlineLabel(row, "GenderName", "GenderNameEn");
            long studentCount = row.get("StudentCount") != null ? ((Number) row.get("StudentCount")).longValue() : 0L;

            total += studentCount;

            HierarchyNodeDto majorNode = majorMap.computeIfAbsent(majorName, key -> HierarchyNodeDto.builder()
                    .label(key)
                    .value(0L)
                    .children(new ArrayList<>())
                    .build());
            majorNode.setValue(majorNode.getValue() + studentCount);

            HierarchyNodeDto statusNode = majorNode.getChildren().stream()
                    .filter(child -> child.getLabel().equals(statusName))
                    .findFirst()
                    .orElseGet(() -> {
                        HierarchyNodeDto node = HierarchyNodeDto.builder()
                                .label(statusName)
                                .value(0L)
                                .children(new ArrayList<>())
                                .build();
                        majorNode.addChild(node);
                        return node;
                    });

            statusNode.setValue(statusNode.getValue() + studentCount);
            statusNode.addChild(HierarchyNodeDto.builder()
                    .label(genderName)
                    .value(studentCount)
                    .children(new ArrayList<>())
                    .build());
        }

        return HierarchyNodeDto.builder()
                .label(rootLabel("chart.hierarchy.academic.root", "Academic Distribution"))
                .value(total)
                .children(new ArrayList<>(majorMap.values()))
                .build();
    }

    public List<GeoLocationDto> getGeographicDistribution(Integer countryId) {
        ChartCountrySqlHelper.ScopeContext scope = chartCountrySqlHelper.resolveScope(countryId);
        // Coordinates come from STUDY_COUNTRY_IN (V028). They used to come from
        // an 18-entry HashMap in this class keyed on English country names,
        // matched against STUDY_COUNTRY_IN_NAME, which holds Arabic — so every
        // lookup missed and every country was pinned at (0,0), in the Gulf of
        // Guinea. The map also had no entry for the UAE or Sudan, both of which
        // this ministry sends students to.
        String sql = "SELECT " +
                "country.STUDY_COUNTRY_IN_NAME AS CountryName, " +
                "MAX(country.STUDY_COUNTRY_IN_NAME_EN) AS CountryNameEn, " +
                "country.LATITUDE AS Latitude, " +
                "country.LONGITUDE AS Longitude, " +
                "gov.STUDY_GOV_IN_NAME AS GovernorateName, " +
                "MAX(gov.STUDY_GOV_IN_NAME_EN) AS GovernorateNameEn, " +
                "muni.STUDENT_MUD_NAME AS MunicipalityName, " +
                "MAX(muni.STUDENT_MUD_NAME_EN) AS MunicipalityNameEn, " +
                "COUNT(*) AS StudentCount " +
                "FROM MOHECORE.STUDENT_DATA sd " +
                "LEFT JOIN MOHECORE.STUDY_COUNTRY_IN country ON sd.STUDY_COUNTRY_IN = country.STUDY_COUNTRY_IN_ID " +
                "LEFT JOIN MOHECORE.STUDY_GOV_IN gov ON sd.SYUDY_GOV_IN = gov.STUDY_GOV_IN_ID " +
                "LEFT JOIN MOHECORE.STUDENT_GOV_MUD muni ON sd.STUDENT_MUD_ID = muni.STUDENT_MUD_ID " +
                "WHERE ISNULL(sd.IS_DELETED,0)=0" +
                chartCountrySqlHelper.studentDataInClause("sd", scope) +
                " GROUP BY country.STUDY_COUNTRY_IN_NAME, country.LATITUDE, country.LONGITUDE, " +
                "gov.STUDY_GOV_IN_NAME, muni.STUDENT_MUD_NAME " +
                "ORDER BY StudentCount DESC";

        List<Map<String, Object>> results = jdbcTemplate.queryForList(sql);

        Map<String, GeoLocationDto> countryMap = new LinkedHashMap<>();
        Map<String, Map<String, GeoBreakdownDto>> governorateMap = new HashMap<>();

        for (Map<String, Object> row : results) {
            String countryName = inlineLabel(row, "CountryName", "CountryNameEn");
            String governorateName = inlineLabel(row, "GovernorateName", "GovernorateNameEn");
            // No municipality recorded: the governorate gets no child for it, as before.
            boolean hasMunicipality = row.get("MunicipalityName") != null;
            String municipalityName = inlineLabel(row, "MunicipalityName", "MunicipalityNameEn");
            long studentCount = row.get("StudentCount") != null ? ((Number) row.get("StudentCount")).longValue() : 0L;

            // Null when the country has no coordinates recorded (e.g. the
            // "Not Specified" placeholder). The map omits such a country rather
            // than drawing it at (0,0).
            Number latitude = (Number) row.get("Latitude");
            Number longitude = (Number) row.get("Longitude");

            GeoLocationDto country = countryMap.computeIfAbsent(countryName, key -> GeoLocationDto.builder()
                    .country(key)
                    .latitude(latitude != null ? latitude.doubleValue() : null)
                    .longitude(longitude != null ? longitude.doubleValue() : null)
                    .studentCount(0L)
                    .governorates(new ArrayList<>())
                    .build());
            country.setStudentCount(country.getStudentCount() + studentCount);

            Map<String, GeoBreakdownDto> govChildren = governorateMap.computeIfAbsent(countryName,
                    key -> new LinkedHashMap<>());
            GeoBreakdownDto governorate = govChildren.computeIfAbsent(governorateName, key -> GeoBreakdownDto.builder()
                    .name(key)
                    .value(0L)
                    .children(new ArrayList<>())
                    .build());
            governorate.setValue(governorate.getValue() + studentCount);

            if (hasMunicipality) {
                governorate.addChild(GeoBreakdownDto.builder()
                        .name(municipalityName)
                        .value(studentCount)
                        .children(new ArrayList<>())
                        .build());
            }
        }

        for (Map.Entry<String, GeoLocationDto> entry : countryMap.entrySet()) {
            Map<String, GeoBreakdownDto> govs = governorateMap.getOrDefault(entry.getKey(), new LinkedHashMap<>());
            entry.getValue().setGovernorates(new ArrayList<>(govs.values()));
        }

        return new ArrayList<>(countryMap.values());
    }


    /**
     * The overview the comprehensive-dashboards report reads. The landing page
     * itself (V070) reads the same figures through {@link DashboardService}, one
     * section at a time, via the public pieces below.
     */
    public DashboardOverviewDto getDashboardOverview(Integer countryId) {
        DashboardOverviewDto.DashboardOverviewDtoBuilder builder = DashboardOverviewDto.builder();

        StudentTotals students = studentTotals(countryId);
        builder.totalStudents(students.total());
        builder.activeStudents(students.active());
        builder.newStudents(students.newStatus());
        builder.suspendedStudents(students.suspended());

        FinanceTotals finance = financeTotals(countryId);
        builder.totalDisbursedAmount(finance.totalDisbursed());
        builder.averageDisbursement(finance.averageDisbursement());

        builder.enrollmentTrend(enrollmentTrend(countryId));
        builder.financialTrend(financialTrend(countryId));
        builder.topCountries(topCountries(countryId));

        return builder.build();
    }

    /** Head-counts by study-status CODE (see {@link #STATUS_JOIN}), in the caller's scope. */
    public record StudentTotals(long total, long active, long newStatus, long suspended) {}

    public StudentTotals studentTotals(Integer countryId) {
        ChartCountrySqlHelper.ScopeContext scope = chartCountrySqlHelper.resolveScope(countryId);
        // The status codes are bound, not written into the statement (codes/StudyStatusCode).
        String studentSql = "SELECT " +
                "COUNT(*) AS TotalStudents, " +
                "SUM(CASE WHEN sss.CODE = ? THEN 1 ELSE 0 END) AS ActiveStudents, " +
                "SUM(CASE WHEN sss.CODE = ? THEN 1 ELSE 0 END) AS NewStudents, " +
                "SUM(CASE WHEN sss.CODE = ? THEN 1 ELSE 0 END) AS SuspendedStudents " +
                "FROM MOHECORE.STUDENT_DATA sd " +
                STATUS_JOIN +
                "WHERE ISNULL(sd.IS_DELETED,0)=0" +
                chartCountrySqlHelper.studentDataInClause("sd", scope);

        Map<String, Object> row = jdbcTemplate.queryForMap(studentSql,
                StudyStatusCode.ACTIVE, StudyStatusCode.NEW, StudyStatusCode.SUSPENDED);
        return new StudentTotals(mapLong(row, "TotalStudents"), mapLong(row, "ActiveStudents"),
                mapLong(row, "NewStudents"), mapLong(row, "SuspendedStudents"));
    }

    /**
     * Net amount disbursed, all time, in the caller's scope.
     *
     * <p>Finance rows whose country was never set are outside a country user's
     * {@code IN (…)} clause here, while the row-level predicate shows them to
     * everyone (V031: NULL belongs to the ministry). The two layers disagree only
     * on those unattributed rows, and only the ministry's total includes them.
     */
    public record FinanceTotals(double totalDisbursed, double averageDisbursement) {}

    public FinanceTotals financeTotals(Integer countryId) {
        ChartCountrySqlHelper.ScopeContext scope = chartCountrySqlHelper.resolveScope(countryId);
        String financeSql = "SELECT " +
                "SUM(COALESCE(sf.NET_AMOUNT, 0)) AS TotalAmount, " +
                "AVG(COALESCE(sf.NET_AMOUNT, 0)) AS AverageAmount " +
                "FROM MOHECORE.STUDENT_FINANCE sf " +
                "WHERE " + LiveStudentSql.ofLiveStudent("sf") +
                chartCountrySqlHelper.financeDirectInClause("sf", scope);

        Map<String, Object> row = jdbcTemplate.queryForMap(financeSql);
        return new FinanceTotals(mapDouble(row, "TotalAmount"), mapDouble(row, "AverageAmount"));
    }

    /**
     * Students by delegation year over the {@code DASHBOARD.YEARS_WINDOW}
     * parameter, oldest first, ending at the ministry's own latest quarter year.
     */
    public List<SimpleDataPointDto> enrollmentTrend(Integer countryId) {
        return fetchEnrollmentTrend(countryId);
    }

    /** Net amount disbursed by received-year over the {@code DASHBOARD.YEARS_WINDOW} parameter, oldest first. */
    public List<SimpleDataPointDto> financialTrend(Integer countryId) {
        return fetchFinancialTrend(countryId);
    }

    /** The {@code DASHBOARD.TOP_COUNTRIES} most populous study countries, in the caller's scope. */
    public List<SimpleDataPointDto> topCountries(Integer countryId) {
        return fetchTopCountries(countryId);
    }

    /**
     * Both halves of this chart are read from what the ministry records: the
     * years and their counts from {@code STUDENT_DATA}, the end of the axis from
     * {@code MOHE_QUARTER}.
     *
     * <p>{@code STUDENT_DELEGATION_DECISION_YEAR} is an {@code nvarchar} typed in
     * by hand, and on the live data it holds a tail of years far in the future —
     * one student in each of 2027 … 2042. Asking for the six highest years, as
     * this did, returned exactly that tail: six bars of one student, while the
     * years that hold the students (2017–2023, sixty to a hundred and sixty each)
     * were off the chart. The delegation year is still the figure being counted —
     * it is what "students by delegation year" means — but a year later than the
     * newest quarter the ministry has opened cannot have happened, so
     * {@code MOHE_QUARTER} ends the axis. That table carries no country predicate,
     * so the bound is the same for every reader, whatever their scope.
     *
     * <p>The cast to {@code INT} matters twice over: it groups '2020' and ' 2020'
     * as one year, and it sorts the axis by year rather than by text.
     * {@code COALESCE} leaves the bound off when no quarter has been opened at
     * all, rather than emptying the chart.
     */
    private List<SimpleDataPointDto> fetchEnrollmentTrend(Integer countryId) {
        ChartCountrySqlHelper.ScopeContext scope = chartCountrySqlHelper.resolveScope(countryId);
        int years = sysParameters.intValue(SysParameterService.DASHBOARD_YEARS_WINDOW);
        String year = "TRY_CAST(sd.STUDENT_DELEGATION_DECISION_YEAR AS INT)";
        String sql = "SELECT TOP (?) " + year + " AS Year, COUNT(*) AS StudentCount " +
                "FROM MOHECORE.STUDENT_DATA sd " +
                "WHERE " + year + " IS NOT NULL " +
                "AND ISNULL(sd.IS_DELETED,0)=0 " +
                "AND " + year + " <= COALESCE((SELECT MAX(q.QUARTER_YEAR) FROM MOHECORE.MOHE_QUARTER q), " + year + ")" +
                chartCountrySqlHelper.studentDataInClause("sd", scope) +
                " GROUP BY " + year +
                " ORDER BY 1 DESC";

        List<Map<String, Object>> results = jdbcTemplate.queryForList(sql, years);
        List<SimpleDataPointDto> points = new ArrayList<>();
        for (int i = results.size() - 1; i >= 0; i--) {
            Map<String, Object> row = results.get(i);
            // The year is never null here (the WHERE says so); a null label would mean "unspecified".
            String label = row.get("Year") != null ? row.get("Year").toString() : null;
            Double value = row.get("StudentCount") != null ? ((Number) row.get("StudentCount")).doubleValue() : 0.0;
            points.add(new SimpleDataPointDto(label, value));
        }
        return points;
    }

    private List<SimpleDataPointDto> fetchFinancialTrend(Integer countryId) {
        ChartCountrySqlHelper.ScopeContext scope = chartCountrySqlHelper.resolveScope(countryId);
        int years = sysParameters.intValue(SysParameterService.DASHBOARD_YEARS_WINDOW);
        String sql = "SELECT TOP (?) sf.STUDENT_FIN_RECIEVED_YEAR AS Year, " +
                "SUM(COALESCE(sf.NET_AMOUNT, 0)) AS TotalAmount " +
                "FROM MOHECORE.STUDENT_FINANCE sf " +
                "WHERE sf.STUDENT_FIN_RECIEVED_YEAR IS NOT NULL AND " + LiveStudentSql.ofLiveStudent("sf") +
                chartCountrySqlHelper.financeDirectInClause("sf", scope) +
                " GROUP BY sf.STUDENT_FIN_RECIEVED_YEAR " +
                "ORDER BY sf.STUDENT_FIN_RECIEVED_YEAR DESC";

        List<Map<String, Object>> results = jdbcTemplate.queryForList(sql, years);
        List<SimpleDataPointDto> points = new ArrayList<>();
        for (int i = results.size() - 1; i >= 0; i--) {
            Map<String, Object> row = results.get(i);
            // The year is never null here (the WHERE says so); a null label would mean "unspecified".
            String label = row.get("Year") != null ? row.get("Year").toString() : null;
            Double value = row.get("TotalAmount") != null ? ((Number) row.get("TotalAmount")).doubleValue() : 0.0;
            points.add(new SimpleDataPointDto(label, value));
        }
        return points;
    }

    /**
     * A null label is a student whose study country was never set; the client
     * names it "unspecified" in its own language rather than this method
     * inventing an English string. The name is in the request's language (QA B6).
     */
    private List<SimpleDataPointDto> fetchTopCountries(Integer countryId) {
        ChartCountrySqlHelper.ScopeContext scope = chartCountrySqlHelper.resolveScope(countryId);
        int top = sysParameters.intValue(SysParameterService.DASHBOARD_TOP_COUNTRIES);
        String sql = "SELECT TOP (?) country.STUDY_COUNTRY_IN_NAME AS CountryName, " +
                "MAX(country.STUDY_COUNTRY_IN_NAME_EN) AS CountryNameEn, " +
                "COUNT(*) AS StudentCount " +
                "FROM MOHECORE.STUDENT_DATA sd " +
                "LEFT JOIN MOHECORE.STUDY_COUNTRY_IN country ON sd.STUDY_COUNTRY_IN = country.STUDY_COUNTRY_IN_ID " +
                "WHERE ISNULL(sd.IS_DELETED,0)=0" +
                chartCountrySqlHelper.studentDataInClause("sd", scope) +
                " GROUP BY country.STUDY_COUNTRY_IN_NAME " +
                "ORDER BY StudentCount DESC";

        List<Map<String, Object>> results = jdbcTemplate.queryForList(sql, top);
        List<SimpleDataPointDto> points = new ArrayList<>();
        for (Map<String, Object> row : results) {
            String label = row.get("CountryName") == null ? null : localisedName(row, "CountryName", "CountryNameEn");
            Double value = row.get("StudentCount") != null ? ((Number) row.get("StudentCount")).doubleValue() : 0.0;
            points.add(new SimpleDataPointDto(label, value));
        }
        return points;
    }

    private static long mapLong(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value instanceof Number number ? number.longValue() : 0L;
    }

    private static double mapDouble(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value instanceof Number number ? number.doubleValue() : 0.0;
    }
}
