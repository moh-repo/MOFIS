package com.dilaw.mohe.repository;

import com.dilaw.mohe.model.CustomFilterReport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Custom filter report.
 *
 * <p>The rows were always real, but three date columns carried real data from
 * the wrong column, which is worse than an empty cell because it looks
 * plausible:
 *
 * <ul>
 *   <li>{@code study_start_date} was {@code STUDENT_BIRTHDATE} — so the report
 *       claimed students began their studies as newborns.</li>
 *   <li>{@code study_end_date} was {@code STUDENT_PASSPORT_EXPIRE_DATE}.</li>
 *   <li>{@code enrollment_date} was synthesised as
 *       {@code STUDENT_DELEGATION_DECISION_YEAR + '-01-01'}, so every student
 *       appeared to enrol on 1 January.</li>
 * </ul>
 *
 * <p>STUDENT_DATA records no study start date, study end date or enrolment date,
 * so all three now report NULL. The year is not lost: it was always available in
 * {@code enrollment_year}, which is a real column and still populated.
 *
 * <p>{@code status_category} keyed on display names, which are user-editable and
 * language-specific, and mapped everything that was not Active/Graduated/
 * Suspended to 'other'. It now keys on {@code STUDENT_STUDY_STATUS.CODE} (V028)
 * and distinguishes the statuses this ministry actually uses. The 'graduated'
 * branch is gone — no such status exists.
 *
 * <p>Every query selects the entity's {@code *_name_en} columns: Hibernate maps a
 * native result by column name, so one left out fails the call, not just the
 * English report (QA B6).
 */
@Repository
public interface CustomFilterReportRepository extends JpaRepository<CustomFilterReport, Long> {

    @Query(value = """
            SELECT
                CAST(sd.STUDENT_ID AS BIGINT) AS student_id,
                sd.STUDENT_NAME AS student_name,
                sd.STUDENT_PASSPORT_NUMBER AS passport_number,
                sui.STUDY_UNIVERSITY_IN_NAME AS university_name,
                COALESCE(NULLIF(sui.STUDY_UNIVERSITY_IN_NAME_EN, N''), sui.STUDY_UNIVERSITY_IN_NAME) AS university_name_en,
                sm.STUDENT_MAJOR_NAME AS major_name,
                COALESCE(NULLIF(sm.STUDENT_MAJOR_NAME_EN, N''), sm.STUDENT_MAJOR_NAME) AS major_name_en,
                sci.STUDY_COUNTRY_IN_NAME AS country_name,
                COALESCE(NULLIF(sci.STUDY_COUNTRY_IN_NAME_EN, N''), sci.STUDY_COUNTRY_IN_NAME) AS country_name_en,
                sd.STUDY_COUNTRY_IN AS student_country_id,
                sd.STUDENT_DEGREE_ID AS degree_level_id,
                sdeg.STUDENT_DEGREE_NAME AS degree_level_name,
                COALESCE(NULLIF(sdeg.STUDENT_DEGREE_NAME_EN, N''), sdeg.STUDENT_DEGREE_NAME) AS degree_level_name_en,
                sd.STUDENT_STUDY_STATUS_ID AS study_status_id,
                sss.STUDENT_STUDY_STATUS_NAME AS study_status_name,
                COALESCE(NULLIF(sss.STUDENT_STUDY_STATUS_NAME_EN, N''), sss.STUDENT_STUDY_STATUS_NAME) AS study_status_name_en,
                sd.STUDENT_STUDY_TYPE_ID AS study_type_id,
                sst.STUDENT_STUDY_TYPE_NAME AS study_type_name,
                COALESCE(NULLIF(sst.STUDENT_STUDY_TYPE_NAME_EN, N''), sst.STUDENT_STUDY_TYPE_NAME) AS study_type_name_en,
                sd.STUDENT_GENDER AS gender_id,
                CAST(NULL AS DATE) AS enrollment_date,
                CAST(NULL AS DATE) AS study_start_date,
                CAST(NULL AS DATE) AS study_end_date,
                CAST(ISNULL(fin.total_amount, 0) AS DECIMAL(18,2)) AS financial_amount,
                sd.STUDENT_STUDY_NO_OF_YEARS AS study_years,
                sd.STUDENT_DELEGATION_DECISION_YEAR AS enrollment_year,
                CASE
                    WHEN sss.CODE = 'ACTIVE'      THEN 'active'
                    WHEN sss.CODE = 'NEW'         THEN 'new'
                    WHEN sss.CODE = 'SUSPENDED'   THEN 'suspended'
                    WHEN sss.CODE = 'WITHDRAWN'   THEN 'withdrawn'
                    WHEN sss.CODE = 'TRANSFERRED' THEN 'transferred'
                    ELSE 'other'
                END AS status_category
            FROM MOHECORE.STUDENT_DATA sd
            LEFT JOIN MOHECORE.STUDY_UNIVERSITY_IN sui ON sui.STUDY_UNIVERSITY_IN_ID = sd.STUDY_UNIVERSITY_IN
            LEFT JOIN MOHECORE.STUDENT_MAJOR sm ON sm.STUDENT_MAJOR_ID = sd.STUDENT_MAJOR_ID
            LEFT JOIN MOHECORE.STUDY_COUNTRY_IN sci ON sci.STUDY_COUNTRY_IN_ID = sd.STUDY_COUNTRY_IN
            LEFT JOIN MOHECORE.STUDENT_DEGREE sdeg ON sdeg.STUDENT_DEGREE_ID = sd.STUDENT_DEGREE_ID
            LEFT JOIN MOHECORE.STUDENT_STUDY_STATUS sss ON sss.STUDENT_STUDY_STATUS_ID = sd.STUDENT_STUDY_STATUS_ID
            LEFT JOIN MOHECORE.STUDENT_STUDY_TYPE sst ON sst.STUDENT_STUDY_TYPE_ID = sd.STUDENT_STUDY_TYPE_ID
            LEFT JOIN (
                SELECT STUDENT_ID, SUM(NET_AMOUNT) AS total_amount
                FROM MOHECORE.STUDENT_FINANCE
                GROUP BY STUDENT_ID
            ) fin ON fin.STUDENT_ID = sd.STUDENT_ID
            WHERE ISNULL(sd.IS_DELETED, 0) = 0
              AND (:applyCountryFilter = 0 OR sd.STUDY_COUNTRY_IN IN (:countryIds))
            ORDER BY sd.STUDENT_NAME
            """, nativeQuery = true)
    List<CustomFilterReport> findAllFiltered(
            @Param("applyCountryFilter") boolean applyCountryFilter,
            @Param("countryIds") List<Integer> countryIds);

    @Query(value = """
            SELECT
                CAST(sd.STUDENT_ID AS BIGINT) AS student_id,
                sd.STUDENT_NAME AS student_name,
                sd.STUDENT_PASSPORT_NUMBER AS passport_number,
                sui.STUDY_UNIVERSITY_IN_NAME AS university_name,
                COALESCE(NULLIF(sui.STUDY_UNIVERSITY_IN_NAME_EN, N''), sui.STUDY_UNIVERSITY_IN_NAME) AS university_name_en,
                sm.STUDENT_MAJOR_NAME AS major_name,
                COALESCE(NULLIF(sm.STUDENT_MAJOR_NAME_EN, N''), sm.STUDENT_MAJOR_NAME) AS major_name_en,
                sci.STUDY_COUNTRY_IN_NAME AS country_name,
                COALESCE(NULLIF(sci.STUDY_COUNTRY_IN_NAME_EN, N''), sci.STUDY_COUNTRY_IN_NAME) AS country_name_en,
                sd.STUDY_COUNTRY_IN AS student_country_id,
                sd.STUDENT_DEGREE_ID AS degree_level_id,
                sdeg.STUDENT_DEGREE_NAME AS degree_level_name,
                COALESCE(NULLIF(sdeg.STUDENT_DEGREE_NAME_EN, N''), sdeg.STUDENT_DEGREE_NAME) AS degree_level_name_en,
                sd.STUDENT_STUDY_STATUS_ID AS study_status_id,
                sss.STUDENT_STUDY_STATUS_NAME AS study_status_name,
                COALESCE(NULLIF(sss.STUDENT_STUDY_STATUS_NAME_EN, N''), sss.STUDENT_STUDY_STATUS_NAME) AS study_status_name_en,
                sd.STUDENT_STUDY_TYPE_ID AS study_type_id,
                sst.STUDENT_STUDY_TYPE_NAME AS study_type_name,
                COALESCE(NULLIF(sst.STUDENT_STUDY_TYPE_NAME_EN, N''), sst.STUDENT_STUDY_TYPE_NAME) AS study_type_name_en,
                sd.STUDENT_GENDER AS gender_id,
                CAST(NULL AS DATE) AS enrollment_date,
                CAST(NULL AS DATE) AS study_start_date,
                CAST(NULL AS DATE) AS study_end_date,
                CAST(ISNULL(fin.total_amount, 0) AS DECIMAL(18,2)) AS financial_amount,
                sd.STUDENT_STUDY_NO_OF_YEARS AS study_years,
                sd.STUDENT_DELEGATION_DECISION_YEAR AS enrollment_year,
                CASE
                    WHEN sss.CODE = 'ACTIVE'      THEN 'active'
                    WHEN sss.CODE = 'NEW'         THEN 'new'
                    WHEN sss.CODE = 'SUSPENDED'   THEN 'suspended'
                    WHEN sss.CODE = 'WITHDRAWN'   THEN 'withdrawn'
                    WHEN sss.CODE = 'TRANSFERRED' THEN 'transferred'
                    ELSE 'other'
                END AS status_category
            FROM MOHECORE.STUDENT_DATA sd
            LEFT JOIN MOHECORE.STUDY_UNIVERSITY_IN sui ON sui.STUDY_UNIVERSITY_IN_ID = sd.STUDY_UNIVERSITY_IN
            LEFT JOIN MOHECORE.STUDENT_MAJOR sm ON sm.STUDENT_MAJOR_ID = sd.STUDENT_MAJOR_ID
            LEFT JOIN MOHECORE.STUDY_COUNTRY_IN sci ON sci.STUDY_COUNTRY_IN_ID = sd.STUDY_COUNTRY_IN
            LEFT JOIN MOHECORE.STUDENT_DEGREE sdeg ON sdeg.STUDENT_DEGREE_ID = sd.STUDENT_DEGREE_ID
            LEFT JOIN MOHECORE.STUDENT_STUDY_STATUS sss ON sss.STUDENT_STUDY_STATUS_ID = sd.STUDENT_STUDY_STATUS_ID
            LEFT JOIN MOHECORE.STUDENT_STUDY_TYPE sst ON sst.STUDENT_STUDY_TYPE_ID = sd.STUDENT_STUDY_TYPE_ID
            LEFT JOIN (
                SELECT STUDENT_ID, SUM(NET_AMOUNT) AS total_amount
                FROM MOHECORE.STUDENT_FINANCE
                GROUP BY STUDENT_ID
            ) fin ON fin.STUDENT_ID = sd.STUDENT_ID
            WHERE ISNULL(sd.IS_DELETED, 0) = 0
              AND sd.STUDENT_STUDY_STATUS_ID = :statusId
              AND (:applyCountryFilter = 0 OR sd.STUDY_COUNTRY_IN IN (:countryIds))
            ORDER BY sd.STUDENT_NAME
            """, nativeQuery = true)
    List<CustomFilterReport> findByStudyStatus(
            @Param("statusId") Integer statusId,
            @Param("applyCountryFilter") boolean applyCountryFilter,
            @Param("countryIds") List<Integer> countryIds);

    @Query(value = """
            SELECT
                CAST(sd.STUDENT_ID AS BIGINT) AS student_id,
                sd.STUDENT_NAME AS student_name,
                sd.STUDENT_PASSPORT_NUMBER AS passport_number,
                sui.STUDY_UNIVERSITY_IN_NAME AS university_name,
                COALESCE(NULLIF(sui.STUDY_UNIVERSITY_IN_NAME_EN, N''), sui.STUDY_UNIVERSITY_IN_NAME) AS university_name_en,
                sm.STUDENT_MAJOR_NAME AS major_name,
                COALESCE(NULLIF(sm.STUDENT_MAJOR_NAME_EN, N''), sm.STUDENT_MAJOR_NAME) AS major_name_en,
                sci.STUDY_COUNTRY_IN_NAME AS country_name,
                COALESCE(NULLIF(sci.STUDY_COUNTRY_IN_NAME_EN, N''), sci.STUDY_COUNTRY_IN_NAME) AS country_name_en,
                sd.STUDY_COUNTRY_IN AS student_country_id,
                sd.STUDENT_DEGREE_ID AS degree_level_id,
                sdeg.STUDENT_DEGREE_NAME AS degree_level_name,
                COALESCE(NULLIF(sdeg.STUDENT_DEGREE_NAME_EN, N''), sdeg.STUDENT_DEGREE_NAME) AS degree_level_name_en,
                sd.STUDENT_STUDY_STATUS_ID AS study_status_id,
                sss.STUDENT_STUDY_STATUS_NAME AS study_status_name,
                COALESCE(NULLIF(sss.STUDENT_STUDY_STATUS_NAME_EN, N''), sss.STUDENT_STUDY_STATUS_NAME) AS study_status_name_en,
                sd.STUDENT_STUDY_TYPE_ID AS study_type_id,
                sst.STUDENT_STUDY_TYPE_NAME AS study_type_name,
                COALESCE(NULLIF(sst.STUDENT_STUDY_TYPE_NAME_EN, N''), sst.STUDENT_STUDY_TYPE_NAME) AS study_type_name_en,
                sd.STUDENT_GENDER AS gender_id,
                CAST(NULL AS DATE) AS enrollment_date,
                CAST(NULL AS DATE) AS study_start_date,
                CAST(NULL AS DATE) AS study_end_date,
                CAST(ISNULL(fin.total_amount, 0) AS DECIMAL(18,2)) AS financial_amount,
                sd.STUDENT_STUDY_NO_OF_YEARS AS study_years,
                sd.STUDENT_DELEGATION_DECISION_YEAR AS enrollment_year,
                CASE
                    WHEN sss.CODE = 'ACTIVE'      THEN 'active'
                    WHEN sss.CODE = 'NEW'         THEN 'new'
                    WHEN sss.CODE = 'SUSPENDED'   THEN 'suspended'
                    WHEN sss.CODE = 'WITHDRAWN'   THEN 'withdrawn'
                    WHEN sss.CODE = 'TRANSFERRED' THEN 'transferred'
                    ELSE 'other'
                END AS status_category
            FROM MOHECORE.STUDENT_DATA sd
            LEFT JOIN MOHECORE.STUDY_UNIVERSITY_IN sui ON sui.STUDY_UNIVERSITY_IN_ID = sd.STUDY_UNIVERSITY_IN
            LEFT JOIN MOHECORE.STUDENT_MAJOR sm ON sm.STUDENT_MAJOR_ID = sd.STUDENT_MAJOR_ID
            LEFT JOIN MOHECORE.STUDY_COUNTRY_IN sci ON sci.STUDY_COUNTRY_IN_ID = sd.STUDY_COUNTRY_IN
            LEFT JOIN MOHECORE.STUDENT_DEGREE sdeg ON sdeg.STUDENT_DEGREE_ID = sd.STUDENT_DEGREE_ID
            LEFT JOIN MOHECORE.STUDENT_STUDY_STATUS sss ON sss.STUDENT_STUDY_STATUS_ID = sd.STUDENT_STUDY_STATUS_ID
            LEFT JOIN MOHECORE.STUDENT_STUDY_TYPE sst ON sst.STUDENT_STUDY_TYPE_ID = sd.STUDENT_STUDY_TYPE_ID
            LEFT JOIN (
                SELECT STUDENT_ID, SUM(NET_AMOUNT) AS total_amount
                FROM MOHECORE.STUDENT_FINANCE
                GROUP BY STUDENT_ID
            ) fin ON fin.STUDENT_ID = sd.STUDENT_ID
            WHERE ISNULL(sd.IS_DELETED, 0) = 0
              AND sd.STUDENT_DEGREE_ID = :degreeLevelId
              AND (:applyCountryFilter = 0 OR sd.STUDY_COUNTRY_IN IN (:countryIds))
            ORDER BY sd.STUDENT_NAME
            """, nativeQuery = true)
    List<CustomFilterReport> findByDegreeLevel(
            @Param("degreeLevelId") Integer degreeLevelId,
            @Param("applyCountryFilter") boolean applyCountryFilter,
            @Param("countryIds") List<Integer> countryIds);

    @Query(value = """
            SELECT
                CAST(sd.STUDENT_ID AS BIGINT) AS student_id,
                sd.STUDENT_NAME AS student_name,
                sd.STUDENT_PASSPORT_NUMBER AS passport_number,
                sui.STUDY_UNIVERSITY_IN_NAME AS university_name,
                COALESCE(NULLIF(sui.STUDY_UNIVERSITY_IN_NAME_EN, N''), sui.STUDY_UNIVERSITY_IN_NAME) AS university_name_en,
                sm.STUDENT_MAJOR_NAME AS major_name,
                COALESCE(NULLIF(sm.STUDENT_MAJOR_NAME_EN, N''), sm.STUDENT_MAJOR_NAME) AS major_name_en,
                sci.STUDY_COUNTRY_IN_NAME AS country_name,
                COALESCE(NULLIF(sci.STUDY_COUNTRY_IN_NAME_EN, N''), sci.STUDY_COUNTRY_IN_NAME) AS country_name_en,
                sd.STUDY_COUNTRY_IN AS student_country_id,
                sd.STUDENT_DEGREE_ID AS degree_level_id,
                sdeg.STUDENT_DEGREE_NAME AS degree_level_name,
                COALESCE(NULLIF(sdeg.STUDENT_DEGREE_NAME_EN, N''), sdeg.STUDENT_DEGREE_NAME) AS degree_level_name_en,
                sd.STUDENT_STUDY_STATUS_ID AS study_status_id,
                sss.STUDENT_STUDY_STATUS_NAME AS study_status_name,
                COALESCE(NULLIF(sss.STUDENT_STUDY_STATUS_NAME_EN, N''), sss.STUDENT_STUDY_STATUS_NAME) AS study_status_name_en,
                sd.STUDENT_STUDY_TYPE_ID AS study_type_id,
                sst.STUDENT_STUDY_TYPE_NAME AS study_type_name,
                COALESCE(NULLIF(sst.STUDENT_STUDY_TYPE_NAME_EN, N''), sst.STUDENT_STUDY_TYPE_NAME) AS study_type_name_en,
                sd.STUDENT_GENDER AS gender_id,
                CAST(NULL AS DATE) AS enrollment_date,
                CAST(NULL AS DATE) AS study_start_date,
                CAST(NULL AS DATE) AS study_end_date,
                CAST(ISNULL(fin.total_amount, 0) AS DECIMAL(18,2)) AS financial_amount,
                sd.STUDENT_STUDY_NO_OF_YEARS AS study_years,
                sd.STUDENT_DELEGATION_DECISION_YEAR AS enrollment_year,
                CASE
                    WHEN sss.CODE = 'ACTIVE'      THEN 'active'
                    WHEN sss.CODE = 'NEW'         THEN 'new'
                    WHEN sss.CODE = 'SUSPENDED'   THEN 'suspended'
                    WHEN sss.CODE = 'WITHDRAWN'   THEN 'withdrawn'
                    WHEN sss.CODE = 'TRANSFERRED' THEN 'transferred'
                    ELSE 'other'
                END AS status_category
            FROM MOHECORE.STUDENT_DATA sd
            LEFT JOIN MOHECORE.STUDY_UNIVERSITY_IN sui ON sui.STUDY_UNIVERSITY_IN_ID = sd.STUDY_UNIVERSITY_IN
            LEFT JOIN MOHECORE.STUDENT_MAJOR sm ON sm.STUDENT_MAJOR_ID = sd.STUDENT_MAJOR_ID
            LEFT JOIN MOHECORE.STUDY_COUNTRY_IN sci ON sci.STUDY_COUNTRY_IN_ID = sd.STUDY_COUNTRY_IN
            LEFT JOIN MOHECORE.STUDENT_DEGREE sdeg ON sdeg.STUDENT_DEGREE_ID = sd.STUDENT_DEGREE_ID
            LEFT JOIN MOHECORE.STUDENT_STUDY_STATUS sss ON sss.STUDENT_STUDY_STATUS_ID = sd.STUDENT_STUDY_STATUS_ID
            LEFT JOIN MOHECORE.STUDENT_STUDY_TYPE sst ON sst.STUDENT_STUDY_TYPE_ID = sd.STUDENT_STUDY_TYPE_ID
            LEFT JOIN (
                SELECT STUDENT_ID, SUM(NET_AMOUNT) AS total_amount
                FROM MOHECORE.STUDENT_FINANCE
                GROUP BY STUDENT_ID
            ) fin ON fin.STUDENT_ID = sd.STUDENT_ID
            WHERE ISNULL(sd.IS_DELETED, 0) = 0
              AND sd.STUDENT_STUDY_TYPE_ID = :typeId
              AND (:applyCountryFilter = 0 OR sd.STUDY_COUNTRY_IN IN (:countryIds))
            ORDER BY sd.STUDENT_NAME
            """, nativeQuery = true)
    List<CustomFilterReport> findByStudyType(
            @Param("typeId") Integer typeId,
            @Param("applyCountryFilter") boolean applyCountryFilter,
            @Param("countryIds") List<Integer> countryIds);

    @Query(value = """
            SELECT
                CAST(sd.STUDENT_ID AS BIGINT) AS student_id,
                sd.STUDENT_NAME AS student_name,
                sd.STUDENT_PASSPORT_NUMBER AS passport_number,
                sui.STUDY_UNIVERSITY_IN_NAME AS university_name,
                COALESCE(NULLIF(sui.STUDY_UNIVERSITY_IN_NAME_EN, N''), sui.STUDY_UNIVERSITY_IN_NAME) AS university_name_en,
                sm.STUDENT_MAJOR_NAME AS major_name,
                COALESCE(NULLIF(sm.STUDENT_MAJOR_NAME_EN, N''), sm.STUDENT_MAJOR_NAME) AS major_name_en,
                sci.STUDY_COUNTRY_IN_NAME AS country_name,
                COALESCE(NULLIF(sci.STUDY_COUNTRY_IN_NAME_EN, N''), sci.STUDY_COUNTRY_IN_NAME) AS country_name_en,
                sd.STUDY_COUNTRY_IN AS student_country_id,
                sd.STUDENT_DEGREE_ID AS degree_level_id,
                sdeg.STUDENT_DEGREE_NAME AS degree_level_name,
                COALESCE(NULLIF(sdeg.STUDENT_DEGREE_NAME_EN, N''), sdeg.STUDENT_DEGREE_NAME) AS degree_level_name_en,
                sd.STUDENT_STUDY_STATUS_ID AS study_status_id,
                sss.STUDENT_STUDY_STATUS_NAME AS study_status_name,
                COALESCE(NULLIF(sss.STUDENT_STUDY_STATUS_NAME_EN, N''), sss.STUDENT_STUDY_STATUS_NAME) AS study_status_name_en,
                sd.STUDENT_STUDY_TYPE_ID AS study_type_id,
                sst.STUDENT_STUDY_TYPE_NAME AS study_type_name,
                COALESCE(NULLIF(sst.STUDENT_STUDY_TYPE_NAME_EN, N''), sst.STUDENT_STUDY_TYPE_NAME) AS study_type_name_en,
                sd.STUDENT_GENDER AS gender_id,
                CAST(NULL AS DATE) AS enrollment_date,
                CAST(NULL AS DATE) AS study_start_date,
                CAST(NULL AS DATE) AS study_end_date,
                CAST(ISNULL(fin.total_amount, 0) AS DECIMAL(18,2)) AS financial_amount,
                sd.STUDENT_STUDY_NO_OF_YEARS AS study_years,
                sd.STUDENT_DELEGATION_DECISION_YEAR AS enrollment_year,
                CASE
                    WHEN sss.CODE = 'ACTIVE'      THEN 'active'
                    WHEN sss.CODE = 'NEW'         THEN 'new'
                    WHEN sss.CODE = 'SUSPENDED'   THEN 'suspended'
                    WHEN sss.CODE = 'WITHDRAWN'   THEN 'withdrawn'
                    WHEN sss.CODE = 'TRANSFERRED' THEN 'transferred'
                    ELSE 'other'
                END AS status_category
            FROM MOHECORE.STUDENT_DATA sd
            LEFT JOIN MOHECORE.STUDY_UNIVERSITY_IN sui ON sui.STUDY_UNIVERSITY_IN_ID = sd.STUDY_UNIVERSITY_IN
            LEFT JOIN MOHECORE.STUDENT_MAJOR sm ON sm.STUDENT_MAJOR_ID = sd.STUDENT_MAJOR_ID
            LEFT JOIN MOHECORE.STUDY_COUNTRY_IN sci ON sci.STUDY_COUNTRY_IN_ID = sd.STUDY_COUNTRY_IN
            LEFT JOIN MOHECORE.STUDENT_DEGREE sdeg ON sdeg.STUDENT_DEGREE_ID = sd.STUDENT_DEGREE_ID
            LEFT JOIN MOHECORE.STUDENT_STUDY_STATUS sss ON sss.STUDENT_STUDY_STATUS_ID = sd.STUDENT_STUDY_STATUS_ID
            LEFT JOIN MOHECORE.STUDENT_STUDY_TYPE sst ON sst.STUDENT_STUDY_TYPE_ID = sd.STUDENT_STUDY_TYPE_ID
            LEFT JOIN (
                SELECT STUDENT_ID, SUM(NET_AMOUNT) AS total_amount
                FROM MOHECORE.STUDENT_FINANCE
                GROUP BY STUDENT_ID
            ) fin ON fin.STUDENT_ID = sd.STUDENT_ID
            WHERE ISNULL(sd.IS_DELETED, 0) = 0
              AND sui.STUDY_UNIVERSITY_IN_NAME LIKE CONCAT('%', :universityName, '%')
              AND (:applyCountryFilter = 0 OR sd.STUDY_COUNTRY_IN IN (:countryIds))
            ORDER BY sd.STUDENT_NAME
            """, nativeQuery = true)
    List<CustomFilterReport> findByUniversityName(
            @Param("universityName") String universityName,
            @Param("applyCountryFilter") boolean applyCountryFilter,
            @Param("countryIds") List<Integer> countryIds);

    @Query(value = """
            SELECT
                CAST(sd.STUDENT_ID AS BIGINT) AS student_id,
                sd.STUDENT_NAME AS student_name,
                sd.STUDENT_PASSPORT_NUMBER AS passport_number,
                sui.STUDY_UNIVERSITY_IN_NAME AS university_name,
                COALESCE(NULLIF(sui.STUDY_UNIVERSITY_IN_NAME_EN, N''), sui.STUDY_UNIVERSITY_IN_NAME) AS university_name_en,
                sm.STUDENT_MAJOR_NAME AS major_name,
                COALESCE(NULLIF(sm.STUDENT_MAJOR_NAME_EN, N''), sm.STUDENT_MAJOR_NAME) AS major_name_en,
                sci.STUDY_COUNTRY_IN_NAME AS country_name,
                COALESCE(NULLIF(sci.STUDY_COUNTRY_IN_NAME_EN, N''), sci.STUDY_COUNTRY_IN_NAME) AS country_name_en,
                sd.STUDY_COUNTRY_IN AS student_country_id,
                sd.STUDENT_DEGREE_ID AS degree_level_id,
                sdeg.STUDENT_DEGREE_NAME AS degree_level_name,
                COALESCE(NULLIF(sdeg.STUDENT_DEGREE_NAME_EN, N''), sdeg.STUDENT_DEGREE_NAME) AS degree_level_name_en,
                sd.STUDENT_STUDY_STATUS_ID AS study_status_id,
                sss.STUDENT_STUDY_STATUS_NAME AS study_status_name,
                COALESCE(NULLIF(sss.STUDENT_STUDY_STATUS_NAME_EN, N''), sss.STUDENT_STUDY_STATUS_NAME) AS study_status_name_en,
                sd.STUDENT_STUDY_TYPE_ID AS study_type_id,
                sst.STUDENT_STUDY_TYPE_NAME AS study_type_name,
                COALESCE(NULLIF(sst.STUDENT_STUDY_TYPE_NAME_EN, N''), sst.STUDENT_STUDY_TYPE_NAME) AS study_type_name_en,
                sd.STUDENT_GENDER AS gender_id,
                CAST(NULL AS DATE) AS enrollment_date,
                CAST(NULL AS DATE) AS study_start_date,
                CAST(NULL AS DATE) AS study_end_date,
                CAST(ISNULL(fin.total_amount, 0) AS DECIMAL(18,2)) AS financial_amount,
                sd.STUDENT_STUDY_NO_OF_YEARS AS study_years,
                sd.STUDENT_DELEGATION_DECISION_YEAR AS enrollment_year,
                CASE
                    WHEN sss.CODE = 'ACTIVE'      THEN 'active'
                    WHEN sss.CODE = 'NEW'         THEN 'new'
                    WHEN sss.CODE = 'SUSPENDED'   THEN 'suspended'
                    WHEN sss.CODE = 'WITHDRAWN'   THEN 'withdrawn'
                    WHEN sss.CODE = 'TRANSFERRED' THEN 'transferred'
                    ELSE 'other'
                END AS status_category
            FROM MOHECORE.STUDENT_DATA sd
            LEFT JOIN MOHECORE.STUDY_UNIVERSITY_IN sui ON sui.STUDY_UNIVERSITY_IN_ID = sd.STUDY_UNIVERSITY_IN
            LEFT JOIN MOHECORE.STUDENT_MAJOR sm ON sm.STUDENT_MAJOR_ID = sd.STUDENT_MAJOR_ID
            LEFT JOIN MOHECORE.STUDY_COUNTRY_IN sci ON sci.STUDY_COUNTRY_IN_ID = sd.STUDY_COUNTRY_IN
            LEFT JOIN MOHECORE.STUDENT_DEGREE sdeg ON sdeg.STUDENT_DEGREE_ID = sd.STUDENT_DEGREE_ID
            LEFT JOIN MOHECORE.STUDENT_STUDY_STATUS sss ON sss.STUDENT_STUDY_STATUS_ID = sd.STUDENT_STUDY_STATUS_ID
            LEFT JOIN MOHECORE.STUDENT_STUDY_TYPE sst ON sst.STUDENT_STUDY_TYPE_ID = sd.STUDENT_STUDY_TYPE_ID
            LEFT JOIN (
                SELECT STUDENT_ID, SUM(NET_AMOUNT) AS total_amount
                FROM MOHECORE.STUDENT_FINANCE
                GROUP BY STUDENT_ID
            ) fin ON fin.STUDENT_ID = sd.STUDENT_ID
            WHERE ISNULL(sd.IS_DELETED, 0) = 0
              AND sd.STUDENT_DELEGATION_DECISION_YEAR = :year
              AND (:applyCountryFilter = 0 OR sd.STUDY_COUNTRY_IN IN (:countryIds))
            ORDER BY sd.STUDENT_NAME
            """, nativeQuery = true)
    List<CustomFilterReport> findByEnrollmentYear(
            @Param("year") Integer year,
            @Param("applyCountryFilter") boolean applyCountryFilter,
            @Param("countryIds") List<Integer> countryIds);
}
