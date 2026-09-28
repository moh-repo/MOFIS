package com.dilaw.mohe.repository;

import com.dilaw.mohe.model.PaymentSummary;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import com.dilaw.mohe.util.LiveStudentSql;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

/**
 * Payment summary — one row per {@code STUDENT_FINANCE} record, read from the
 * base tables (the same SELECT is {@code V_Payment_Summary}, V078).
 *
 * <p>Every cell is a recorded value or NULL: the currency is the row's
 * {@code STUDY_CURRENCY_IN_ID} (NULL until the ministry backfills it), the
 * status and the "paid to whom" type are the lookup names with their CODE
 * beside them, and the payment method is the one inference the audit accepted —
 * a bank reference means a bank transfer, a cheque number means a cheque,
 * neither means cash — expressed as {@code LU_PAYMENT_METHOD} codes bound from
 * {@link com.dilaw.mohe.codes.PaymentMethodCode}, never as words. The
 * {@code 'Unknown'} / {@code 'Other'} labels that used to stand in for NULL are
 * gone: the screen shows "unspecified" in the reader's language instead.
 */
@Repository
public interface PaymentSummaryRepository extends JpaRepository<PaymentSummary, Integer> {

    /** How the money went out, from what the row references. */
    String METHOD = "CASE WHEN sf.STUDY_FINANCE_BANKE_ID IS NOT NULL THEN :#{T(com.dilaw.mohe.codes.PaymentMethodCode).BANK_TRANSFER} "
            + "WHEN sf.STUDY_CHECK_ID IS NOT NULL AND LTRIM(RTRIM(sf.STUDY_CHECK_ID)) <> '' THEN :#{T(com.dilaw.mohe.codes.PaymentMethodCode).CHECK} "
            + "ELSE :#{T(com.dilaw.mohe.codes.PaymentMethodCode).CASH} END";

    String SELECT = "SELECT sf.STUDENT_FIN_ID AS payment_id, sd.STUDENT_ID AS student_id, sd.STUDENT_NAME AS student_name, "
            + "sd.STUDENT_PASSPORT_NUMBER AS student_passport_number, CAST(sf.STUDY_CHECK_DATE AS DATE) AS payment_date, "
            + "sf.NET_AMOUNT AS payment_amount, cur.STUDY_CURRENCY_IN_NAME AS payment_currency, "
            + "COALESCE(NULLIF(cur.STUDY_CURRENCY_IN_NAME_EN, N''), cur.STUDY_CURRENCY_IN_NAME) AS payment_currency_en, "
            + "sfrs.STUDY_FIN_RECEIVED_STATUS_NAME AS payment_status, sfrs.CODE AS payment_status_code, "
            + "COALESCE(NULLIF(sfrs.STUDY_FIN_RECEIVED_STATUS_NAME_EN, N''), sfrs.STUDY_FIN_RECEIVED_STATUS_NAME) AS payment_status_en, "
            + "srws.STUDY_RECEIVED_WHOM_STATUS_NAME AS payment_type, " + METHOD + " AS payment_method, "
            + "COALESCE(NULLIF(srws.STUDY_RECEIVED_WHOM_STATUS_NAME_EN, N''), srws.STUDY_RECEIVED_WHOM_STATUS_NAME) AS payment_type_en, "
            + "mq.QUARTER_NAME AS quarter_name, sf.STUDENT_FIN_RECIEVED_YEAR AS year_name, "
            + "COALESCE(NULLIF(mq.QUARTER_NAME_EN, N''), mq.QUARTER_NAME) AS quarter_name_en, "
            + "sui.STUDY_UNIVERSITY_IN_NAME AS university_name, smi.STUDY_MAJOR_NAME AS major_name, "
            + "COALESCE(NULLIF(sui.STUDY_UNIVERSITY_IN_NAME_EN, N''), sui.STUDY_UNIVERSITY_IN_NAME) AS university_name_en, "
            + "COALESCE(NULLIF(smi.STUDY_MAJOR_NAME_EN, N''), smi.STUDY_MAJOR_NAME) AS major_name_en, "
            + "sf.STUDENT_FIN_RECIEVED_YEAR AS payment_year, sf.STUDENT_FIN_RECIEVED_MONTH AS payment_month ";

    String FROM = "FROM MOHECORE.STUDENT_FINANCE sf "
            // Payments of live students only (live QA H7).
            + "JOIN MOHECORE.STUDENT_DATA sd ON sf.STUDENT_ID = sd.STUDENT_ID AND " + LiveStudentSql.SD_NOT_DELETED + " "
            + "LEFT JOIN MOHECORE.STUDY_CURRENCY_IN cur ON cur.STUDY_CURRENCY_IN_ID = sf.STUDY_CURRENCY_IN_ID "
            + "LEFT JOIN MOHECORE.STUDY_FIN_RECEIVED_STATUS sfrs ON sf.STUDY_FIN_RECIEVED_STATUS = sfrs.STUDY_FIN_RECEIVED_STATUS_ID "
            + "LEFT JOIN MOHECORE.STUDY_RECEIVED_WHOM_STATUS srws ON sf.STUDY_RECEIVED_WHOM_STATUS_ID = srws.STUDY_RECEIVED_WHOM_STATUS_ID "
            // The finance row's own quarter, by year and number (R5-31): MOHE_QUARTER.QUARTER
            // is the YYYYQ key, and it was compared with a month-folded 1-4 that never matched.
            + "LEFT JOIN MOHECORE.MOHE_QUARTER mq ON mq.QUARTER_YEAR = sf.STUDENT_FIN_RECIEVED_YEAR "
            + "AND mq.QUARTER_NO = sf.STUDENT_FIN_RECIEVED_MONTH "
            + "LEFT JOIN MOHECORE.STUDY_UNIVERSITY_IN sui ON sd.STUDY_UNIVERSITY_IN = sui.STUDY_UNIVERSITY_IN_ID "
            + "LEFT JOIN MOHECORE.STUDY_MAJOR_IN smi ON sd.STUDY_MAJOR_IN = smi.STUDY_MAJOR_ID ";

    String COUNTRY = "(:applyCountryFilter = 0 OR sd.STUDY_COUNTRY_IN IN (:countryIds))";

    String FILTERS = "WHERE (:status IS NULL OR sfrs.STUDY_FIN_RECEIVED_STATUS_NAME = :status OR sfrs.STUDY_FIN_RECEIVED_STATUS_NAME_EN = :status) "
            + "AND (:type IS NULL OR srws.STUDY_RECEIVED_WHOM_STATUS_NAME = :type OR srws.STUDY_RECEIVED_WHOM_STATUS_NAME_EN = :type) "
            + "AND (:method IS NULL OR " + METHOD + " = :method) "
            + "AND (:quarter IS NULL OR mq.QUARTER_NAME = :quarter OR mq.QUARTER_NAME_EN = :quarter) "
            + "AND (:year IS NULL OR sf.STUDENT_FIN_RECIEVED_YEAR = :year) "
            + "AND (:startDate IS NULL OR sf.STUDY_CHECK_DATE >= :startDate) "
            + "AND (:endDate IS NULL OR sf.STUDY_CHECK_DATE <= :endDate) "
            + "AND (:search IS NULL OR (LOWER(sd.STUDENT_NAME) LIKE LOWER(CONCAT('%', :search, '%')) "
            + "OR LOWER(sd.STUDENT_PASSPORT_NUMBER) LIKE LOWER(CONCAT('%', :search, '%')))) "
            + "AND " + COUNTRY;

    @Query(value = SELECT + FROM + FILTERS,
           countQuery = "SELECT COUNT(*) " + FROM + FILTERS,
           nativeQuery = true)
    Page<PaymentSummary> findByFilters(
            @Param("status") String status,
            @Param("type") String type,
            @Param("method") String method,
            @Param("quarter") String quarter,
            @Param("year") Integer year,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("applyCountryFilter") boolean applyCountryFilter,
            @Param("countryIds") List<Integer> countryIds,
            @Param("search") String search,
            Pageable pageable);

    /**
     * The amount per status CODE over every row {@link #findByFilters} selects, not one
     * page of them (round-6 QA R6-05): {@code [code, amount]}, a NULL code for a row
     * with no status.
     */
    @Query(value = "SELECT sfrs.CODE, SUM(sf.NET_AMOUNT) " + FROM + FILTERS + " GROUP BY sfrs.CODE",
           nativeQuery = true)
    List<Object[]> sumByStatusCode(
            @Param("status") String status,
            @Param("type") String type,
            @Param("method") String method,
            @Param("quarter") String quarter,
            @Param("year") Integer year,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("applyCountryFilter") boolean applyCountryFilter,
            @Param("countryIds") List<Integer> countryIds,
            @Param("search") String search);

    /** The statuses the finance rows actually carry (a fallback when the lookup table is empty). */
    @Query(value = "SELECT DISTINCT sfrs.STUDY_FIN_RECEIVED_STATUS_NAME FROM MOHECORE.STUDENT_FINANCE sf "
            + "JOIN MOHECORE.STUDY_FIN_RECEIVED_STATUS sfrs ON sf.STUDY_FIN_RECIEVED_STATUS = sfrs.STUDY_FIN_RECEIVED_STATUS_ID "
            + "WHERE sfrs.STUDY_FIN_RECEIVED_STATUS_NAME IS NOT NULL AND " + LiveStudentSql.SF_OF_LIVE_STUDENT, nativeQuery = true)
    List<String> findDistinctStatuses();

    @Query(value = "SELECT DISTINCT srws.STUDY_RECEIVED_WHOM_STATUS_NAME FROM MOHECORE.STUDENT_FINANCE sf "
            + "JOIN MOHECORE.STUDY_RECEIVED_WHOM_STATUS srws ON sf.STUDY_RECEIVED_WHOM_STATUS_ID = srws.STUDY_RECEIVED_WHOM_STATUS_ID "
            + "WHERE srws.STUDY_RECEIVED_WHOM_STATUS_NAME IS NOT NULL AND " + LiveStudentSql.SF_OF_LIVE_STUDENT, nativeQuery = true)
    List<String> findDistinctTypes();

    @Query(value = "SELECT DISTINCT mq.QUARTER_NAME FROM MOHECORE.STUDENT_FINANCE sf "
            + "LEFT JOIN MOHECORE.MOHE_QUARTER mq ON mq.QUARTER_YEAR = sf.STUDENT_FIN_RECIEVED_YEAR "
            + "AND mq.QUARTER_NO = sf.STUDENT_FIN_RECIEVED_MONTH "
            + "WHERE mq.QUARTER_NAME IS NOT NULL AND " + LiveStudentSql.SF_OF_LIVE_STUDENT
            + " ORDER BY mq.QUARTER_NAME", nativeQuery = true)
    List<String> findDistinctQuarters();

    @Query(value = "SELECT DISTINCT sf.STUDENT_FIN_RECIEVED_YEAR FROM MOHECORE.STUDENT_FINANCE sf "
            + "WHERE sf.STUDENT_FIN_RECIEVED_YEAR IS NOT NULL AND " + LiveStudentSql.SF_OF_LIVE_STUDENT
            + " ORDER BY sf.STUDENT_FIN_RECIEVED_YEAR DESC", nativeQuery = true)
    List<Integer> findDistinctYears();

    @Query(value = SELECT + FROM + "WHERE (:status IS NULL OR sfrs.STUDY_FIN_RECEIVED_STATUS_NAME = :status OR sfrs.STUDY_FIN_RECEIVED_STATUS_NAME_EN = :status) AND " + COUNTRY,
           nativeQuery = true)
    List<PaymentSummary> findByPaymentStatus(@Param("status") String status,
                                             @Param("applyCountryFilter") boolean applyCountryFilter,
                                             @Param("countryIds") List<Integer> countryIds);

    @Query(value = SELECT + FROM + "WHERE (:type IS NULL OR srws.STUDY_RECEIVED_WHOM_STATUS_NAME = :type OR srws.STUDY_RECEIVED_WHOM_STATUS_NAME_EN = :type) AND " + COUNTRY,
           nativeQuery = true)
    List<PaymentSummary> findByPaymentType(@Param("type") String type,
                                           @Param("applyCountryFilter") boolean applyCountryFilter,
                                           @Param("countryIds") List<Integer> countryIds);

    @Query(value = SELECT + FROM + "WHERE (:year IS NULL OR sf.STUDENT_FIN_RECIEVED_YEAR = :year) AND " + COUNTRY,
           nativeQuery = true)
    List<PaymentSummary> findByPaymentYear(@Param("year") Integer year,
                                           @Param("applyCountryFilter") boolean applyCountryFilter,
                                           @Param("countryIds") List<Integer> countryIds);
}
