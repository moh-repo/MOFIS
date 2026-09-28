package com.dilaw.mohe.service;

import com.dilaw.mohe.codes.FinReceivedStatusCode;
import com.dilaw.mohe.codes.MinistryTypeCode;
import com.dilaw.mohe.codes.ReportLabelCode;
import com.dilaw.mohe.codes.UniversityTypeCode;
import com.dilaw.mohe.service.codelist.CodeListService;

import com.dilaw.mohe.dto.QuarterlyFinancialReportDto;
import com.dilaw.mohe.dto.QuarterlyFinancialReportDto.EntityFinancialSummary;
import com.dilaw.mohe.ums.service.ReportCountryScopeHelper;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Amounts reported here are net of wafar (STUDENT_FINANCE.NET_AMOUNT), because that is
 * what the ministry actually disbursed.
 *
 * The {@code WHERE COALESCE(sf.STUDENT_FIN_RECIEVED_AMOUNT, 0) <> 0} guards below stay
 * on the gross column on purpose: they mean "a disbursement happened". Switching them to
 * NET_AMOUNT would silently drop every record whose wafar consumed the whole amount —
 * the row would vanish from the report rather than appear as zero.
 */
@Transactional(readOnly = true)
@Service
public class QuarterlyFinancialReportService {

    private static final Logger log = LoggerFactory.getLogger(QuarterlyFinancialReportService.class);

    private final JdbcTemplate jdbcTemplate;
    private final ReportCountryScopeHelper countryScopeHelper;
    private final CodeListService codeLists;

    public QuarterlyFinancialReportService(JdbcTemplate jdbcTemplate,
                                           ReportCountryScopeHelper countryScopeHelper,
                                           CodeListService codeLists) {
        this.jdbcTemplate = jdbcTemplate;
        this.countryScopeHelper = countryScopeHelper;
        this.codeLists = codeLists;
    }

    public List<Integer> getDistinctYears(Integer countryId) {
        countryScopeHelper.ensureScopeOrThrow();
        List<Integer> countryIds = countryScopeHelper.resolveCountryIds(countryId);
        boolean applyCountry = countryScopeHelper.shouldApplyCountryFilter(countryIds);
        final String sql = """
            SELECT DISTINCT
                COALESCE(YEAR(sf.STUDY_CHECK_DATE), sf.STUDENT_FIN_RECIEVED_YEAR) AS txn_year
            FROM MOHECORE.STUDENT_FINANCE sf
            LEFT JOIN MOHECORE.STUDENT_DATA sd ON sf.STUDENT_ID = sd.STUDENT_ID
            WHERE COALESCE(sf.STUDENT_FIN_RECIEVED_AMOUNT, 0) <> 0
              AND COALESCE(YEAR(sf.STUDY_CHECK_DATE), sf.STUDENT_FIN_RECIEVED_YEAR) IS NOT NULL
              AND ISNULL(sd.IS_DELETED, 0) = 0
              AND (? = 0 OR sd.STUDY_COUNTRY_IN IN (%s))
            ORDER BY txn_year DESC
            """.formatted(countryIdPlaceholders(countryScopeHelper.safeList(countryIds)));
        try {
            return jdbcTemplate.queryForList(sql, Integer.class, queryParams(applyCountry, countryScopeHelper.safeList(countryIds)));
        } catch (Exception ex) {
            log.error("Error querying distinct years: {}", ex.getMessage());
            return new ArrayList<>();
        }
    }

    public QuarterlyFinancialReportDto getQuarterlyReport(Integer countryId, Integer quarter, Integer year) {
        QuarterlyFinancialReportDto report = new QuarterlyFinancialReportDto();

        report.setReportDate(new SimpleDateFormat("dd-MM-yyyy").format(new Date()));
        report.setReportNumber("QR-" + System.currentTimeMillis() % 10000);

        PeriodResult result = getEntityFinancialSummaries(countryId, quarter, year);
        List<EntityFinancialSummary> entities = result.entities();

        // Label the report with the period the figures came from, not the one that
        // was asked for. The two differ whenever the fallback below kicks in, and a
        // quarterly summary that names the wrong quarter is worse than an empty one.
        report.setRequestedQuarter(quarter);
        report.setRequestedYear(year);
        report.setQuarter(result.quarter());
        report.setYear(result.year());
        report.setQuarterName(getQuarterName(result.quarter()));
        report.setPeriodSubstituted(!Objects.equals(quarter, result.quarter())
                || !Objects.equals(year, result.year()));

        report.setEntities(entities);
        report.setEntityCount(entities.size());

        long totalReceivedAmount = 0;
        int totalReceivedCount = 0;
        long totalDisbursedAmount = 0;
        int totalDisbursedCount = 0;
        long totalRemainingAmount = 0;
        int totalRemainingCount = 0;

        for (EntityFinancialSummary entity : entities) {
            totalReceivedAmount += defaultLong(entity.getReceivedAmount());
            totalReceivedCount += defaultInteger(entity.getReceivedCount());
            totalDisbursedAmount += defaultLong(entity.getDisbursedAmount());
            totalDisbursedCount += defaultInteger(entity.getDisbursedCount());
            totalRemainingAmount += defaultLong(entity.getRemainingAmount());
            totalRemainingCount += defaultInteger(entity.getRemainingCount());
        }

        report.setTotalReceivedAmount(totalReceivedAmount);
        report.setTotalReceivedCount(totalReceivedCount);
        report.setTotalDisbursedAmount(totalDisbursedAmount);
        report.setTotalDisbursedCount(totalDisbursedCount);
        report.setTotalRemainingAmount(totalRemainingAmount);
        report.setTotalRemainingCount(totalRemainingCount);

        report.setDisbursementRatio(totalReceivedAmount > 0
                ? (double) totalDisbursedAmount * 100 / totalReceivedAmount
                : 0.0);

        return report;
    }

    /** Rows, plus the period they were actually read from. */
    private record PeriodResult(List<EntityFinancialSummary> entities, Integer year, Integer quarter) {}

    private PeriodResult getEntityFinancialSummaries(Integer countryId, Integer quarter, Integer year) {
        countryScopeHelper.ensureScopeOrThrow();
        List<Integer> countryIds = countryScopeHelper.resolveCountryIds(countryId);
        boolean applyCountry = countryScopeHelper.shouldApplyCountryFilter(countryIds);
        List<Integer> safeCountryIds = countryScopeHelper.safeList(countryIds);
        /*
         * TYPE_ALL_IS reproduces the ministry's original Access expression: the
         * entity is the university type, falling back to the ministry type, then
         * to the agency type. The "not specified" sentinel is 99 in Access, but
         * this database mostly stores NULL for the same meaning (793 of 941
         * students have a NULL university type and none carry 99), so both have
         * to be treated as unspecified or every row would collapse onto the
         * university level.
         *
         * Grouping by the agency type alone — as this report did — produced three
         * rows instead of the ~25 universities, ministries and agencies the
         * report is supposed to list.
         *
         * Disbursement follows STUDY_FIN_RECEIVED_STATUS.CODE = RECEIVED (تم
         * الاستلام), which is what the report calls المنصرف. Remaining is the
         * balance, so the columns always reconcile even where the status is not
         * recorded. The "not specified" university/ministry rows are recognised
         * by their CODE (V072), not by the id 99; the codes are bound parameters.
         *
         * entity_name_en is the same choice over the _NAME_EN columns, falling
         * back to the Arabic where a lookup has no English (V104's rule), so an
         * English report prints English entities (QA B6). The rows still group on
         * the Arabic name, so the Arabic report is unchanged row for row.
         */
        final String sql = """
            WITH base AS (
                SELECT
                    COALESCE(sat.STUDENT_AGENCY_TYPE_ORDER, 9999) AS agency_order,
                    COALESCE(
                        CASE
                            WHEN sut.STUDENT_UNIVERSITY_TYPE_ID IS NULL OR sut.CODE = ?
                                THEN CASE
                                    WHEN smt.STUDENT_MINISTRY_TYPE_ID IS NULL OR smt.CODE = ?
                                        THEN sat.STUDENT_AGENCY_TYPE_NAME
                                    ELSE smt.STUDENT_MINISTRY_TYPE_NAME
                                END
                            ELSE sut.STUDENT_UNIVERSITY_TYPE_NAME
                        END,
                        ?
                    ) AS entity_name,
                    COALESCE(
                        CASE
                            WHEN sut.STUDENT_UNIVERSITY_TYPE_ID IS NULL OR sut.CODE = ?
                                THEN CASE
                                    WHEN smt.STUDENT_MINISTRY_TYPE_ID IS NULL OR smt.CODE = ?
                                        THEN COALESCE(NULLIF(sat.STUDENT_AGENCY_TYPE_NAME_EN, N''), sat.STUDENT_AGENCY_TYPE_NAME)
                                    ELSE COALESCE(NULLIF(smt.STUDENT_MINISTRY_TYPE_NAME_EN, N''), smt.STUDENT_MINISTRY_TYPE_NAME)
                                END
                            ELSE COALESCE(NULLIF(sut.STUDENT_UNIVERSITY_TYPE_NAME_EN, N''), sut.STUDENT_UNIVERSITY_TYPE_NAME)
                        END,
                        ?
                    ) AS entity_name_en,
                    frs.CODE AS status_code,
                    -- Net of wafar: this is what the ministry actually disbursed.
                    COALESCE(sf.NET_AMOUNT, 0) AS amount,
                    COALESCE(
                        YEAR(sf.STUDY_CHECK_DATE),
                        sf.STUDENT_FIN_RECIEVED_YEAR
                    ) AS txn_year,
                    COALESCE(
                        DATEPART(QUARTER, sf.STUDY_CHECK_DATE),
                        -- The column holds the quarter (1-4), not a month (R5-31).
                        CASE
                            WHEN sf.STUDENT_FIN_RECIEVED_MONTH BETWEEN 1 AND 4
                                THEN sf.STUDENT_FIN_RECIEVED_MONTH
                            ELSE NULL
                        END
                    ) AS txn_quarter
                FROM MOHECORE.STUDENT_FINANCE sf
                INNER JOIN MOHECORE.STUDENT_DATA sd ON sf.STUDENT_ID = sd.STUDENT_ID
                LEFT JOIN MOHECORE.STUDENT_AGENCY_TYPE sat ON sd.STUDENT_AGENCY_TYPE_ID = sat.STUDENT_AGENCY_TYPE_ID
                LEFT JOIN MOHECORE.STUDENT_MINISTRY_TYPE smt ON sd.STUDENT_MINISTRY_TYPE_ID = smt.STUDENT_MINISTRY_TYPE_ID
                LEFT JOIN MOHECORE.STUDENT_UNIVERSITY_TYPE sut ON sd.STUDENT_UNIVERSITY_TYPE_ID = sut.STUDENT_UNIVERSITY_TYPE_ID
                LEFT JOIN MOHECORE.STUDY_FIN_RECEIVED_STATUS frs ON frs.STUDY_FIN_RECEIVED_STATUS_ID = sf.STUDY_FIN_RECIEVED_STATUS
                WHERE COALESCE(sf.STUDENT_FIN_RECIEVED_AMOUNT, 0) <> 0
                  AND ISNULL(sd.IS_DELETED, 0) = 0
                  AND (? = 0 OR sd.STUDY_COUNTRY_IN IN (%s))
            )
            SELECT agency_order,
                   entity_name,
                   MAX(entity_name_en) AS entity_name_en,
                   SUM(amount) AS received_amount,
                   COUNT(*)    AS received_count,
                   SUM(CASE WHEN status_code = ? THEN amount ELSE 0 END) AS disbursed_amount,
                   SUM(CASE WHEN status_code = ? THEN 1 ELSE 0 END)      AS disbursed_count
            FROM base
            WHERE txn_year = ? AND txn_quarter = ?
            GROUP BY agency_order, entity_name
            ORDER BY agency_order, entity_name
            """.formatted(countryIdPlaceholders(safeCountryIds));

        Object[] params = summaryParams(applyCountry, safeCountryIds, year, quarter);
        List<Map<String, Object>> rows = executeSummaryQuery(sql, params);

        Integer effectiveYear = year;
        Integer effectiveQuarter = quarter;
        if (rows.isEmpty()) {
            LatestPeriod latest = findLatestPeriod(applyCountry, safeCountryIds);
            if (latest != null && latest.year() != null && latest.quarter() != null) {
                params = summaryParams(applyCountry, safeCountryIds, latest.year(), latest.quarter());
                List<Map<String, Object>> fallbackRows = executeSummaryQuery(sql, params);
                // Only claim a different period if the fallback actually produced rows.
                if (!fallbackRows.isEmpty()) {
                    rows = fallbackRows;
                    effectiveYear = latest.year();
                    effectiveQuarter = latest.quarter();
                }
            }
        }

        // One row per entity now, with the split already done in SQL, so there is
        // no bucket label to match on and no way for a mismatch to silently zero
        // the report as the Arabic bucket keys once did.
        Map<AgencyKey, AgencyAggregate> aggregates = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            AgencyKey key = new AgencyKey(
                    (Integer) row.get("agency_order"),
                    (String) row.get("entity_name"));
            AgencyAggregate aggregate = aggregates.computeIfAbsent(
                    key,
                    k -> new AgencyAggregate(k.agencyName(), (String) row.get("entity_name_en")));

            long receivedAmount = defaultLong(row.get("received_amount"));
            int receivedCount = defaultInteger(row.get("received_count"));
            long disbursedAmount = defaultLong(row.get("disbursed_amount"));
            int disbursedCount = defaultInteger(row.get("disbursed_count"));

            aggregate.addReceived(receivedAmount, receivedCount);
            aggregate.addDisbursed(disbursedAmount, disbursedCount);
            // Remaining is the undisbursed balance. Deriving it rather than
            // reading a second status set keeps the three columns reconciled even
            // for rows whose received status was never recorded.
            aggregate.addRemaining(receivedAmount - disbursedAmount, receivedCount - disbursedCount);
        }

        List<EntityFinancialSummary> summaries = aggregates.entrySet().stream()
                .sorted(Comparator
                        .comparing((Map.Entry<AgencyKey, AgencyAggregate> entry) -> defaultOrder(entry.getKey().agencyOrder()))
                        .thenComparing(entry -> entry.getKey().agencyName()))
                .map(entry -> entry.getValue().toSummary())
                .collect(Collectors.toList());
        return new PeriodResult(summaries, effectiveYear, effectiveQuarter);
    }

    private List<Map<String, Object>> executeSummaryQuery(String sql, Object[] params) {
        try {
            return jdbcTemplate.queryForList(sql, params);
        } catch (Exception ex) {
            log.error("Error querying quarterly financial summary: {}", ex.getMessage());
            return new ArrayList<>();
        }
    }

    private LatestPeriod findLatestPeriod(boolean applyCountry, List<Integer> countryIds) {
        final String sql = """
            SELECT TOP 1
                COALESCE(
                    YEAR(sf.STUDY_CHECK_DATE),
                    sf.STUDENT_FIN_RECIEVED_YEAR
                ) AS txn_year,
                COALESCE(
                    DATEPART(QUARTER, sf.STUDY_CHECK_DATE),
                    -- The column holds the quarter (1-4), not a month (R5-31).
                    CASE
                        WHEN sf.STUDENT_FIN_RECIEVED_MONTH BETWEEN 1 AND 4
                            THEN sf.STUDENT_FIN_RECIEVED_MONTH
                        ELSE NULL
                    END
                ) AS txn_quarter
            FROM MOHECORE.STUDENT_FINANCE sf
            LEFT JOIN MOHECORE.STUDENT_DATA sd ON sf.STUDENT_ID = sd.STUDENT_ID
            WHERE COALESCE(sf.STUDENT_FIN_RECIEVED_AMOUNT, 0) <> 0
              AND ISNULL(sd.IS_DELETED, 0) = 0
              AND (? = 0 OR sd.STUDY_COUNTRY_IN IN (%s))
              AND COALESCE(
                    DATEPART(QUARTER, sf.STUDY_CHECK_DATE),
                    CASE
                        WHEN sf.STUDENT_FIN_RECIEVED_MONTH BETWEEN 1 AND 4
                            THEN sf.STUDENT_FIN_RECIEVED_MONTH
                        ELSE NULL
                    END
                ) IS NOT NULL
            ORDER BY txn_year DESC, txn_quarter DESC
            """.formatted(countryIdPlaceholders(countryIds));

        try {
            return jdbcTemplate.query(sql, rs -> {
                if (rs.next()) {
                    return new LatestPeriod(rs.getInt("txn_year"), rs.getInt("txn_quarter"));
                }
                return null;
            }, queryParams(applyCountry, countryIds));
        } catch (Exception ex) {
            log.error("Error retrieving latest quarterly period: {}", ex.getMessage());
            return null;
        }
    }

    private static String countryIdPlaceholders(List<Integer> countryIds) {
        if (countryIds == null || countryIds.isEmpty()) {
            return "-1";
        }
        return countryIds.stream().map(id -> "?").collect(Collectors.joining(", "));
    }

    private static Object[] queryParams(boolean applyCountry, List<Integer> countryIds) {
        List<Object> params = new ArrayList<>();
        params.add(applyCountry ? 1 : 0);
        if (applyCountry && countryIds != null) {
            params.addAll(countryIds);
        }
        return params.toArray();
    }

    /** The binds of the summary query, in the order its placeholders appear. */
    private Object[] summaryParams(boolean applyCountry, List<Integer> countryIds, Integer year, Integer quarter) {
        List<Object> params = new ArrayList<>();
        params.add(UniversityTypeCode.NOT_SPECIFIED);
        params.add(MinistryTypeCode.NOT_SPECIFIED);
        params.add(codeLists.label("report-label", ReportLabelCode.LBL_UNSPECIFIED, "ar"));
        // entity_name_en: the same two codes, and "unspecified" in English.
        params.add(UniversityTypeCode.NOT_SPECIFIED);
        params.add(MinistryTypeCode.NOT_SPECIFIED);
        params.add(codeLists.label("report-label", ReportLabelCode.LBL_UNSPECIFIED, "en"));
        params.add(applyCountry ? 1 : 0);
        if (applyCountry && countryIds != null) {
            params.addAll(countryIds);
        }
        params.add(FinReceivedStatusCode.RECEIVED);
        params.add(FinReceivedStatusCode.RECEIVED);
        params.add(year);
        params.add(quarter);
        return params.toArray();
    }

    /** The quarter's Arabic name from LU_QUARTER_NUMBER; the number itself when the list does not know it. */
    private String getQuarterName(Integer quarter) {
        if (quarter == null) {
            return "";
        }
        return codeLists.label("quarter-number", String.valueOf(quarter), "ar");
    }

    private long defaultLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return 0L;
    }

    private int defaultInteger(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        return 0;
    }

    private long defaultLong(Long value) {
        return value != null ? value : 0L;
    }

    private int defaultInteger(Integer value) {
        return value != null ? value : 0;
    }

    private int defaultOrder(Integer order) {
        return order != null ? order : Integer.MAX_VALUE;
    }

    private record LatestPeriod(Integer year, Integer quarter) {}

    /**
     * Map key grouping the three bucket rows (received / disbursed / remaining)
     * a single agency produces.
     *
     * <p>Must be a record, or any value-equal type: as a plain class without
     * equals/hashCode every row hashed differently, so an agency was never
     * grouped and appeared three times, each occurrence carrying one bucket and
     * zeros elsewhere.
     */
    private record AgencyKey(Integer agencyOrder, String agencyName) {}

    private static class AgencyAggregate {
        private final String agencyName;
        private final String agencyNameEn;
        private long receivedAmount;
        private int receivedCount;
        private long disbursedAmount;
        private int disbursedCount;
        private long remainingAmount;
        private int remainingCount;

        AgencyAggregate(String agencyName, String agencyNameEn) {
            this.agencyName = agencyName;
            this.agencyNameEn = agencyNameEn;
        }

        void addReceived(long amount, int count) {
            this.receivedAmount += amount;
            this.receivedCount += count;
        }

        void addDisbursed(long amount, int count) {
            this.disbursedAmount += amount;
            this.disbursedCount += count;
        }

        void addRemaining(long amount, int count) {
            this.remainingAmount += amount;
            this.remainingCount += count;
        }

        EntityFinancialSummary toSummary() {
            EntityFinancialSummary summary = new EntityFinancialSummary();
            summary.setEntityName(agencyName);
            summary.setEntityNameEn(agencyNameEn);
            summary.setReceivedAmount(receivedAmount);
            summary.setReceivedCount(receivedCount);
            summary.setDisbursedAmount(disbursedAmount);
            summary.setDisbursedCount(disbursedCount);
            summary.setRemainingAmount(remainingAmount);
            summary.setRemainingCount(remainingCount);
            return summary;
        }
    }
}
