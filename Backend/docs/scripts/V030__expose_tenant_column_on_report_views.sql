/*
    V030 — expose the tenant key on the report views, and stop two reports
    silently dropping 99.7% of their students.

    Problem 1 — the wrong country is being used as the tenant key
    -------------------------------------------------------------
    UMSCORE.UserCountries holds STUDY_COUNTRY_IN ids (CountryScopeService loads
    ministry scope from STUDY_COUNTRY_IN), but six student report views expose
    only STUDENT_COUNTRY_ID — the student's home/nationality country — and their
    repositories filter the user's scope against it:

        ... AND s.studentCountryId IN :countryIds     -- STUDENT_COUNTRY_ID

    These are two different lookup tables with overlapping ids:

        id      STUDY_COUNTRY_IN (tenant)    STUDENT_COUNTRY (home)
        1       Egypt                        Yemen
        1004    Jordan                       Bahrain
        1007    United States of America     Jordan

    The current data hides it: all 941 students are home=1 AND study=1, so the
    numbers coincide. In production a user scoped to Jordan (1004) would be shown
    students whose *nationality* is Bahrain, and none of the students actually
    studying in Jordan.

    Each of these views is built on STUDENT_DATA, which carries the real tenant
    key in STUDY_COUNTRY_IN (941/941 populated). This script exposes it as
    study_country_in_id so the repositories can filter on the right column. The
    home country stays exactly where it was — it is legitimate report content, it
    is simply not the tenant key.

    Problem 2 — V_Enrollment_Statistics and V_Major_Distribution return 3 of 941
    ----------------------------------------------------------------------------
    Both INNER JOIN STUDENT_DEGREE and STUDENT_STUDY_TYPE. STUDENT_DEGREE_ID is
    set on 4 of 941 students, so the inner join discards everyone else:

        SELECT SUM(student_count) FROM MOHECORE.V_Enrollment_Statistics;  -- 3
        SELECT SUM(student_count) FROM MOHECORE.V_Major_Distribution;     -- 3

    An inner join to a lookup is only ever correct when the column is mandatory.
    These are optional, so the joins become LEFT and the unset values surface as
    the existing ISNULL(..., N'') placeholder instead of removing the student.
    V_Student_Status_Report gets the same treatment: it happens to return all 941
    today, but it inner-joins STUDENT_COUNTRY and STUDENT_MAJOR and would start
    dropping students the moment either is left unset.

    Not addressed here
    ------------------
    V_Academic_Performance keeps NULL AS GPA and its is_graduated column, and
    V_Payment_Summary / V_Financial_Audit / V_Bank_Transaction /
    V_Scholarship_Tracking contain further hardcoded literals ('USD', 'LYD',
    'System Auditor', 'Debit', and a scholarship_amount computed as
    STUDENT_STUDY_NO_OF_YEARS * 10000). Those are data-provenance defects, not
    tenancy ones; they are recorded in docs/report-data-audit.md and want their own
    migration rather than being folded into a security fix.

    Expected result
    ---------------
        SELECT SUM(student_count) FROM MOHECORE.V_Enrollment_Statistics;  -- 941
        SELECT SUM(student_count) FROM MOHECORE.V_Major_Distribution;     -- 941
        -- and every view below now has a study_country_in_id column

    Apply in SSMS, after V029.
*/

SET NOCOUNT ON;
SET QUOTED_IDENTIFIER ON;
GO

/* ─────────────────────────────────────────────────────────────────────────────
 * 1. V_Student_Demographics — add the tenant key.
 * ──────────────────────────────────────────────────────────────────────────── */
CREATE OR ALTER VIEW MOHECORE.V_Student_Demographics AS
SELECT
    sd.STUDENT_ID,
    sd.STUDENT_NAME,
    -- Tenant key. STUDENT_COUNTRY_ID below is the student's nationality and is
    -- deliberately NOT the column scope filters on.
    sd.STUDY_COUNTRY_IN                  AS study_country_in_id,
    sd.STUDENT_COUNTRY_ID,
    ISNULL(sc.STUDENT_COUNTRY_NAME, N'') AS STUDENT_COUNTRY_NAME,
    ISNULL(sg.STUDENT_GENDER_NAME, N'')  AS STUDENT_GENDER,
    TRY_CAST(sd.STUDENT_AGE AS INT)      AS student_age,
    sd.STUDENT_CURRENT_LEVEL_ID,
    ISNULL(scl.STUDENT_CURRENT_LEVEL_NAME, N'') AS STUDENT_CURRENT_LEVEL_NAME,
    sd.STUDENT_STUDY_TYPE_ID,
    ISNULL(sst.STUDENT_STUDY_TYPE_NAME, N'') AS STUDENT_STUDY_TYPE_NAME,
    sd.STUDENT_DEGREE_ID,
    ISNULL(sdeg.STUDENT_DEGREE_NAME, N'') AS STUDENT_DEGREE_NAME,
    sd.STUDENT_MAJOR_ID,
    ISNULL(sm.STUDENT_MAJOR_NAME, N'') AS STUDENT_MAJOR_NAME,
    sd.STUDENT_STUDY_STATUS_ID,
    ISNULL(sss.STUDENT_STUDY_STATUS_NAME, N'') AS STUDENT_STUDY_STATUS_NAME
FROM MOHECORE.STUDENT_DATA sd
LEFT JOIN MOHECORE.STUDENT_COUNTRY       sc   ON sc.STUDENT_COUNTRY_ID         = sd.STUDENT_COUNTRY_ID
LEFT JOIN MOHECORE.STUDENT_GENDER        sg   ON sg.STUDENT_GENDER_ID          = sd.STUDENT_GENDER
LEFT JOIN MOHECORE.STUDENT_CURRENT_LEVEL scl  ON scl.STUDENT_CURRENT_LEVEL_ID  = sd.STUDENT_CURRENT_LEVEL_ID
LEFT JOIN MOHECORE.STUDENT_STUDY_TYPE    sst  ON sst.STUDENT_STUDY_TYPE_ID     = sd.STUDENT_STUDY_TYPE_ID
LEFT JOIN MOHECORE.STUDENT_DEGREE        sdeg ON sdeg.STUDENT_DEGREE_ID        = sd.STUDENT_DEGREE_ID
LEFT JOIN MOHECORE.STUDENT_MAJOR         sm   ON sm.STUDENT_MAJOR_ID           = sd.STUDENT_MAJOR_ID
LEFT JOIN MOHECORE.STUDENT_STUDY_STATUS  sss  ON sss.STUDENT_STUDY_STATUS_ID   = sd.STUDENT_STUDY_STATUS_ID
WHERE ISNULL(sd.IS_DELETED, 0) = 0;
GO

/* ─────────────────────────────────────────────────────────────────────────────
 * 2. V_Student_List_Detailed — add the tenant key.
 * ──────────────────────────────────────────────────────────────────────────── */
CREATE OR ALTER VIEW MOHECORE.V_Student_List_Detailed AS
SELECT
    sd.STUDENT_ID,
    sd.STUDENT_NAME,
    sd.STUDENT_PASSPORT_NUMBER,
    sd.STUDENT_BIRTHDATE,
    TRY_CAST(sd.STUDENT_AGE AS INT)      AS student_age,
    sd.STUDY_COUNTRY_IN                  AS study_country_in_id,   -- tenant key
    sd.STUDENT_COUNTRY_ID,                                          -- nationality
    ISNULL(sc.STUDENT_COUNTRY_NAME, N'') AS STUDENT_COUNTRY_NAME,
    ISNULL(sg.STUDENT_GENDER_NAME, N'')  AS STUDENT_GENDER,
    sd.STUDENT_MAJOR_ID,
    ISNULL(sm.STUDENT_MAJOR_NAME, N'') AS STUDENT_MAJOR_NAME,
    sd.STUDENT_DEGREE_ID,
    ISNULL(sdeg.STUDENT_DEGREE_NAME, N'') AS STUDENT_DEGREE_NAME,
    sd.STUDENT_CURRENT_LEVEL_ID,
    ISNULL(scl.STUDENT_CURRENT_LEVEL_NAME, N'') AS STUDENT_CURRENT_LEVEL_NAME,
    sd.STUDENT_STUDY_TYPE_ID,
    ISNULL(sst.STUDENT_STUDY_TYPE_NAME, N'') AS STUDENT_STUDY_TYPE_NAME,
    sd.STUDENT_STUDY_STATUS_ID,
    ISNULL(sss.STUDENT_STUDY_STATUS_NAME, N'') AS STUDENT_STUDY_STATUS_NAME,
    sd.STUDENT_UNIVERSITY_TYPE_ID,
    ISNULL(sut.STUDENT_UNIVERSITY_TYPE_NAME, N'') AS STUDENT_UNIVERSITY_TYPE_NAME,
    sd.STUDENT_AGENCY_TYPE_ID,
    ISNULL(sat.STUDENT_AGENCY_TYPE_NAME, N'') AS STUDENT_AGENCY_TYPE_NAME,
    sd.STUDENT_MINISTRY_TYPE_ID,
    ISNULL(smt.STUDENT_MINISTRY_TYPE_NAME, N'') AS STUDENT_MINISTRY_TYPE_NAME,
    sd.STUDENT_STUDY_NO_OF_YEARS,
    sd.STUDENT_DELEGATION_DECISION_YEAR,
    sd.STUDENT_ADDED_QUARTER,
    sd.STUDENT_PASSPORT_ISSUE_DATE,
    sd.STUDENT_PASSPORT_EXPIRE_DATE,
    NULL AS GPA
FROM MOHECORE.STUDENT_DATA sd
LEFT JOIN MOHECORE.STUDENT_COUNTRY          sc   ON sc.STUDENT_COUNTRY_ID          = sd.STUDENT_COUNTRY_ID
LEFT JOIN MOHECORE.STUDENT_GENDER           sg   ON sg.STUDENT_GENDER_ID           = sd.STUDENT_GENDER
LEFT JOIN MOHECORE.STUDENT_MAJOR            sm   ON sm.STUDENT_MAJOR_ID            = sd.STUDENT_MAJOR_ID
LEFT JOIN MOHECORE.STUDENT_DEGREE           sdeg ON sdeg.STUDENT_DEGREE_ID         = sd.STUDENT_DEGREE_ID
LEFT JOIN MOHECORE.STUDENT_CURRENT_LEVEL    scl  ON scl.STUDENT_CURRENT_LEVEL_ID   = sd.STUDENT_CURRENT_LEVEL_ID
LEFT JOIN MOHECORE.STUDENT_STUDY_TYPE       sst  ON sst.STUDENT_STUDY_TYPE_ID      = sd.STUDENT_STUDY_TYPE_ID
LEFT JOIN MOHECORE.STUDENT_STUDY_STATUS     sss  ON sss.STUDENT_STUDY_STATUS_ID    = sd.STUDENT_STUDY_STATUS_ID
LEFT JOIN MOHECORE.STUDENT_UNIVERSITY_TYPE  sut  ON sut.STUDENT_UNIVERSITY_TYPE_ID = sd.STUDENT_UNIVERSITY_TYPE_ID
LEFT JOIN MOHECORE.STUDENT_AGENCY_TYPE      sat  ON sat.STUDENT_AGENCY_TYPE_ID     = sd.STUDENT_AGENCY_TYPE_ID
LEFT JOIN MOHECORE.STUDENT_MINISTRY_TYPE    smt  ON smt.STUDENT_MINISTRY_TYPE_ID   = sd.STUDENT_MINISTRY_TYPE_ID
WHERE ISNULL(sd.IS_DELETED, 0) = 0;
GO

/* ─────────────────────────────────────────────────────────────────────────────
 * 3. V_Academic_Performance — add the tenant key.
 *
 * is_graduated and GPA are left exactly as they were. Both are known-broken
 * (there is no 'Graduated' status in STUDENT_STUDY_STATUS, so is_graduated is 0
 * for all 941 students, and GPA is a literal NULL), but they are provenance
 * defects rather than tenancy ones and changing them here would mix two concerns.
 * ──────────────────────────────────────────────────────────────────────────── */
CREATE OR ALTER VIEW MOHECORE.V_Academic_Performance AS
SELECT
    sd.STUDENT_ID,
    sd.STUDENT_NAME,
    sd.STUDY_COUNTRY_IN                  AS study_country_in_id,   -- tenant key
    sd.STUDENT_MAJOR_ID,
    ISNULL(sm.STUDENT_MAJOR_NAME, N'') AS STUDENT_MAJOR_NAME,
    sd.STUDENT_CURRENT_LEVEL_ID,
    ISNULL(scl.STUDENT_CURRENT_LEVEL_NAME, N'') AS STUDENT_CURRENT_LEVEL_NAME,
    sd.STUDENT_STUDY_NO_OF_YEARS,
    sd.STUDENT_STUDY_STATUS_ID,
    ISNULL(sss.STUDENT_STUDY_STATUS_NAME, N'') AS STUDENT_STUDY_STATUS_NAME,
    sd.STUDENT_DEGREE_ID,
    ISNULL(sdeg.STUDENT_DEGREE_NAME, N'') AS STUDENT_DEGREE_NAME,
    sd.STUDENT_COUNTRY_ID,                                          -- nationality
    ISNULL(sc.STUDENT_COUNTRY_NAME, N'') AS STUDENT_COUNTRY_NAME,
    CASE WHEN sss.STUDENT_STUDY_STATUS_NAME IN (N'Graduated', N'متخرج') THEN 1 ELSE 0 END AS is_graduated,
    NULL AS GPA
FROM MOHECORE.STUDENT_DATA sd
LEFT JOIN MOHECORE.STUDENT_MAJOR         sm   ON sm.STUDENT_MAJOR_ID          = sd.STUDENT_MAJOR_ID
LEFT JOIN MOHECORE.STUDENT_CURRENT_LEVEL scl  ON scl.STUDENT_CURRENT_LEVEL_ID = sd.STUDENT_CURRENT_LEVEL_ID
LEFT JOIN MOHECORE.STUDENT_STUDY_STATUS  sss  ON sss.STUDENT_STUDY_STATUS_ID  = sd.STUDENT_STUDY_STATUS_ID
LEFT JOIN MOHECORE.STUDENT_DEGREE        sdeg ON sdeg.STUDENT_DEGREE_ID       = sd.STUDENT_DEGREE_ID
LEFT JOIN MOHECORE.STUDENT_COUNTRY       sc   ON sc.STUDENT_COUNTRY_ID        = sd.STUDENT_COUNTRY_ID
WHERE ISNULL(sd.IS_DELETED, 0) = 0;
GO

/* ─────────────────────────────────────────────────────────────────────────────
 * 4. V_Student_Status_Report — tenant key, and lookups made LEFT.
 * ──────────────────────────────────────────────────────────────────────────── */
CREATE OR ALTER VIEW MOHECORE.V_Student_Status_Report AS
SELECT
    sd.STUDY_COUNTRY_IN AS study_country_in_id,                     -- tenant key
    -- Composite key components, coalesced for the same reason as above.
    ISNULL(sd.STUDENT_STUDY_STATUS_ID, 0) AS STUDENT_STUDY_STATUS_ID,
    ISNULL(sd.STUDENT_COUNTRY_ID, 0)      AS STUDENT_COUNTRY_ID,     -- nationality
    ISNULL(sd.STUDENT_MAJOR_ID, 0)        AS STUDENT_MAJOR_ID,
    ISNULL(sss.STUDENT_STUDY_STATUS_NAME, N'') AS STUDENT_STUDY_STATUS_NAME,
    ISNULL(sc.STUDENT_COUNTRY_NAME, N'') AS STUDENT_COUNTRY_NAME,
    ISNULL(sm.STUDENT_MAJOR_NAME, N'') AS STUDENT_MAJOR_NAME,
    COUNT(sd.STUDENT_ID) AS student_count,
    -- Keyed on CODE (V028) rather than the display name, which is user-editable
    -- and language-specific. There is no GRADUATED code in this database.
    CASE
        WHEN sss.CODE = 'ACTIVE'      THEN 'active'
        WHEN sss.CODE = 'NEW'         THEN 'new'
        WHEN sss.CODE = 'SUSPENDED'   THEN 'suspended'
        WHEN sss.CODE = 'WITHDRAWN'   THEN 'withdrawn'
        WHEN sss.CODE = 'TRANSFERRED' THEN 'transferred'
        ELSE 'other'
    END AS status_category
FROM MOHECORE.STUDENT_DATA sd
LEFT JOIN MOHECORE.STUDENT_STUDY_STATUS sss ON sss.STUDENT_STUDY_STATUS_ID = sd.STUDENT_STUDY_STATUS_ID
LEFT JOIN MOHECORE.STUDENT_COUNTRY      sc  ON sc.STUDENT_COUNTRY_ID       = sd.STUDENT_COUNTRY_ID
LEFT JOIN MOHECORE.STUDENT_MAJOR        sm  ON sm.STUDENT_MAJOR_ID         = sd.STUDENT_MAJOR_ID
WHERE ISNULL(sd.IS_DELETED, 0) = 0
GROUP BY sd.STUDY_COUNTRY_IN, ISNULL(sd.STUDENT_STUDY_STATUS_ID, 0), ISNULL(sd.STUDENT_COUNTRY_ID, 0), ISNULL(sd.STUDENT_MAJOR_ID, 0),
         sss.STUDENT_STUDY_STATUS_NAME, sc.STUDENT_COUNTRY_NAME, sm.STUDENT_MAJOR_NAME, sss.CODE;
GO

/* ─────────────────────────────────────────────────────────────────────────────
 * 5. V_Enrollment_Statistics — tenant key, and the inner joins that were
 *    discarding 938 of 941 students made LEFT.
 * ──────────────────────────────────────────────────────────────────────────── */
CREATE OR ALTER VIEW MOHECORE.V_Enrollment_Statistics AS
SELECT
    ISNULL(sd.STUDENT_ADDED_QUARTER, 0) AS enrollment_quarter,
    sd.STUDY_COUNTRY_IN AS study_country_in_id,                     -- tenant key
    -- These five are the entity's composite key. Hibernate returns a null entity
    -- for any row where a key component is null, and with the lookup joins now
    -- LEFT they can be: STUDENT_DEGREE_ID is unset on 937 of 941 students. 0 is
    -- the existing "unset" sentinel here, already used for enrollment_quarter.
    ISNULL(sd.STUDENT_COUNTRY_ID, 0)    AS STUDENT_COUNTRY_ID,       -- nationality
    ISNULL(sd.STUDENT_MAJOR_ID, 0)      AS STUDENT_MAJOR_ID,
    ISNULL(sd.STUDENT_DEGREE_ID, 0)     AS STUDENT_DEGREE_ID,
    ISNULL(sd.STUDENT_STUDY_TYPE_ID, 0) AS STUDENT_STUDY_TYPE_ID,
    ISNULL(sc.STUDENT_COUNTRY_NAME, N'') AS STUDENT_COUNTRY_NAME,
    ISNULL(sm.STUDENT_MAJOR_NAME, N'') AS STUDENT_MAJOR_NAME,
    ISNULL(sdeg.STUDENT_DEGREE_NAME, N'') AS STUDENT_DEGREE_NAME,
    ISNULL(sst.STUDENT_STUDY_TYPE_NAME, N'') AS STUDENT_STUDY_TYPE_NAME,
    COUNT(sd.STUDENT_ID) AS student_count
FROM MOHECORE.STUDENT_DATA sd
LEFT JOIN MOHECORE.STUDENT_COUNTRY     sc   ON sc.STUDENT_COUNTRY_ID     = sd.STUDENT_COUNTRY_ID
LEFT JOIN MOHECORE.STUDENT_MAJOR       sm   ON sm.STUDENT_MAJOR_ID       = sd.STUDENT_MAJOR_ID
LEFT JOIN MOHECORE.STUDENT_DEGREE      sdeg ON sdeg.STUDENT_DEGREE_ID    = sd.STUDENT_DEGREE_ID
LEFT JOIN MOHECORE.STUDENT_STUDY_TYPE  sst  ON sst.STUDENT_STUDY_TYPE_ID = sd.STUDENT_STUDY_TYPE_ID
WHERE ISNULL(sd.IS_DELETED, 0) = 0
GROUP BY sd.STUDENT_ADDED_QUARTER, sd.STUDY_COUNTRY_IN, ISNULL(sd.STUDENT_COUNTRY_ID, 0),
         ISNULL(sd.STUDENT_MAJOR_ID, 0), ISNULL(sd.STUDENT_DEGREE_ID, 0), ISNULL(sd.STUDENT_STUDY_TYPE_ID, 0),
         sc.STUDENT_COUNTRY_NAME, sm.STUDENT_MAJOR_NAME, sdeg.STUDENT_DEGREE_NAME,
         sst.STUDENT_STUDY_TYPE_NAME;
GO

/* ─────────────────────────────────────────────────────────────────────────────
 * 6. V_Major_Distribution — same treatment.
 * ──────────────────────────────────────────────────────────────────────────── */
CREATE OR ALTER VIEW MOHECORE.V_Major_Distribution AS
SELECT
    sd.STUDY_COUNTRY_IN AS study_country_in_id,                     -- tenant key
    -- Composite key components, coalesced for the same reason as above.
    ISNULL(sd.STUDENT_MAJOR_ID, 0)      AS STUDENT_MAJOR_ID,
    ISNULL(sd.STUDENT_DEGREE_ID, 0)     AS STUDENT_DEGREE_ID,
    ISNULL(sd.STUDENT_COUNTRY_ID, 0)    AS STUDENT_COUNTRY_ID,       -- nationality
    ISNULL(sd.STUDENT_STUDY_TYPE_ID, 0) AS STUDENT_STUDY_TYPE_ID,
    ISNULL(sm.STUDENT_MAJOR_NAME, N'') AS STUDENT_MAJOR_NAME,
    ISNULL(sdeg.STUDENT_DEGREE_NAME, N'') AS STUDENT_DEGREE_NAME,
    ISNULL(sc.STUDENT_COUNTRY_NAME, N'') AS STUDENT_COUNTRY_NAME,
    ISNULL(sst.STUDENT_STUDY_TYPE_NAME, N'') AS STUDENT_STUDY_TYPE_NAME,
    COUNT(sd.STUDENT_ID) AS student_count,
    SUM(CASE WHEN sg.STUDENT_GENDER_NAME_EN LIKE N'Male%'   THEN 1 ELSE 0 END) AS male_count,
    SUM(CASE WHEN sg.STUDENT_GENDER_NAME_EN LIKE N'Female%' THEN 1 ELSE 0 END) AS female_count
FROM MOHECORE.STUDENT_DATA sd
LEFT JOIN MOHECORE.STUDENT_MAJOR       sm   ON sm.STUDENT_MAJOR_ID       = sd.STUDENT_MAJOR_ID
LEFT JOIN MOHECORE.STUDENT_DEGREE      sdeg ON sdeg.STUDENT_DEGREE_ID    = sd.STUDENT_DEGREE_ID
LEFT JOIN MOHECORE.STUDENT_COUNTRY     sc   ON sc.STUDENT_COUNTRY_ID     = sd.STUDENT_COUNTRY_ID
LEFT JOIN MOHECORE.STUDENT_STUDY_TYPE  sst  ON sst.STUDENT_STUDY_TYPE_ID = sd.STUDENT_STUDY_TYPE_ID
LEFT JOIN MOHECORE.STUDENT_GENDER      sg   ON sg.STUDENT_GENDER_ID      = sd.STUDENT_GENDER
WHERE ISNULL(sd.IS_DELETED, 0) = 0
GROUP BY sd.STUDY_COUNTRY_IN, ISNULL(sd.STUDENT_MAJOR_ID, 0), ISNULL(sd.STUDENT_DEGREE_ID, 0),
         ISNULL(sd.STUDENT_COUNTRY_ID, 0), ISNULL(sd.STUDENT_STUDY_TYPE_ID, 0),
         sm.STUDENT_MAJOR_NAME, sdeg.STUDENT_DEGREE_NAME, sc.STUDENT_COUNTRY_NAME,
         sst.STUDENT_STUDY_TYPE_NAME;
GO

/* ─────────────────────────────────────────────────────────────────────────────
 * 7. V_Data_Visualization — was not tenant-filterable at all.
 *
 * DataVisualizationRepository applies no country predicate of any kind, and the
 * view had no column to apply one to: it aggregated every student in the
 * ministry into one global set of chart slices. Every country saw the same chart,
 * built from everyone's data.
 *
 * Each slice is now cut per study country, so a row belongs to exactly one
 * tenant. A user scoped to several countries gets one slice per country per
 * label, which DataVisualizationService sums back together before charting —
 * otherwise a multi-country user would see duplicate labels.
 * ──────────────────────────────────────────────────────────────────────────── */
CREATE OR ALTER VIEW MOHECORE.V_Data_Visualization AS
SELECT
    ROW_NUMBER() OVER (ORDER BY study_country_in_id, category, data_label) AS visualization_id,
    N'Student Distribution' AS chart_name,
    N'pie' AS chart_type,
    category,
    data_value,
    data_label,
    N'#4472C4' AS color_code,
    ROW_NUMBER() OVER (PARTITION BY study_country_in_id, category ORDER BY data_value DESC) AS sort_order,
    study_country_in_id
FROM (
    SELECT 'status' AS category,
           sd.STUDY_COUNTRY_IN AS study_country_in_id,
           COUNT(*) AS data_value,
           ISNULL(sss.STUDENT_STUDY_STATUS_NAME, N'Unknown') AS data_label
    FROM MOHECORE.STUDENT_DATA sd
    LEFT JOIN MOHECORE.STUDENT_STUDY_STATUS sss ON sss.STUDENT_STUDY_STATUS_ID = sd.STUDENT_STUDY_STATUS_ID
    WHERE ISNULL(sd.IS_DELETED, 0) = 0
    GROUP BY sd.STUDY_COUNTRY_IN, sss.STUDENT_STUDY_STATUS_NAME

    UNION ALL

    SELECT 'country' AS category,
           sd.STUDY_COUNTRY_IN AS study_country_in_id,
           COUNT(*) AS data_value,
           ISNULL(sc.STUDENT_COUNTRY_NAME, N'Unknown') AS data_label
    FROM MOHECORE.STUDENT_DATA sd
    LEFT JOIN MOHECORE.STUDENT_COUNTRY sc ON sc.STUDENT_COUNTRY_ID = sd.STUDENT_COUNTRY_ID
    WHERE ISNULL(sd.IS_DELETED, 0) = 0
    GROUP BY sd.STUDY_COUNTRY_IN, sc.STUDENT_COUNTRY_NAME
) v;
GO

/* ─────────────────────────────────────────────────────────────────────────────
 * 8. The three report-management views (V027) — add the tenant key.
 *
 * V029 added STUDY_COUNTRY_IN_ID to SAVED_QUERY, SCHEDULED_REPORT and
 * REPORT_TYPE_SETTINGS' access log. NULL keeps its established meaning of
 * "ministry-wide", so a saved query or schedule with no country is visible to
 * everyone, exactly as it is today, while a stamped one belongs to its country.
 * ──────────────────────────────────────────────────────────────────────────── */
CREATE OR ALTER VIEW MOHECORE.V_Export_Manager AS
WITH access_summary AS (
    SELECT
        l.REPORT_CODE,
        l.STUDY_COUNTRY_IN_ID,
        MAX(l.ACCESSED_AT) AS last_export_date,
        MAX(CASE WHEN l.EXPORT_FORMAT IN (N'PDF', N'XLSX', N'DOC')
                 THEN l.EXPORT_FORMAT END) AS last_export_format
    FROM MOHECORE.REPORT_ACCESS_LOG l
    GROUP BY l.REPORT_CODE, l.STUDY_COUNTRY_IN_ID
)
SELECT
    CAST(s.REPORT_TYPE_ID AS BIGINT) AS export_id,
    COALESCE(NULLIF(LTRIM(RTRIM(s.REPORT_TYPE_NAME_AR)), N''),
             CAST(s.REPORT_TYPE_NAME_EN AS NVARCHAR(510))) AS report_name,
    CAST(COALESCE(NULLIF(LTRIM(RTRIM(s.TITLE_01_AR)), N''),
                  CAST(s.TITLE_01_EN AS NVARCHAR(1000))) AS NVARCHAR(1000)) AS report_description,
    CAST(NULL AS INT) AS total_records,
    CASE WHEN s.IS_ACTIVE = 1 THEN N'available' ELSE N'unavailable' END AS export_status,
    CAST(NULL AS INT) AS estimated_size_kb,
    CAST(a.last_export_date AS DATETIME) AS last_export_date,
    COALESCE(a.last_export_format, N'PDF') AS default_format,
    CAST(s.IS_ACTIVE AS INT) AS is_available,
    CAST(NULL AS INT) AS filter_criteria_id,
    -- The report catalogue itself is ministry-wide; the country comes from who
    -- has exported it. NULL = never exported, so visible to everyone.
    a.STUDY_COUNTRY_IN_ID AS study_country_in_id
FROM MOHECORE.REPORT_TYPE_SETTINGS s
LEFT JOIN access_summary a ON a.REPORT_CODE = s.REPORT_TYPE_CODE;
GO

CREATE OR ALTER VIEW MOHECORE.V_Report_Template AS
SELECT
    q.SAVED_QUERY_ID AS template_id,
    q.QUERY_NAME AS template_name,
    q.DESCRIPTION AS template_description,
    q.SOURCE_NAME AS report_type,
    q.SPEC_JSON AS filter_configuration,
    CAST(JSON_QUERY(q.SPEC_JSON, '$.columns') AS NVARCHAR(MAX)) AS selected_columns,
    CAST(NULL AS INT) AS estimated_records,
    CAST(q.CREATED_AT AS DATETIME) AS created_date,
    CAST(q.UPDATED_AT AS DATETIME) AS last_used_date,
    CAST(NULL AS INT) AS usage_count,
    CAST(q.IS_SHARED AS INT) AS is_public,
    CAST(NULL AS NVARCHAR(60)) AS template_status,
    q.STUDY_COUNTRY_IN_ID AS study_country_in_id
FROM MOHECORE.SAVED_QUERY q;
GO

CREATE OR ALTER VIEW MOHECORE.V_Scheduled_Report AS
SELECT
    CAST(r.SCHEDULE_ID AS BIGINT) AS schedule_id,
    r.SCHEDULE_NAME AS schedule_name,
    r.SCHEDULE_DESCRIPTION AS schedule_description,
    r.FREQUENCY AS frequency,
    CAST(r.NEXT_RUN_DATE AS DATETIME) AS next_run_date,
    CAST(r.LAST_RUN_DATE AS DATETIME) AS last_run_date,
    r.SCHEDULE_STATUS AS schedule_status,
    r.RECORDS_IN_LAST_RUN AS records_in_last_run,
    r.RECIPIENT_EMAIL AS recipient_email,
    CAST(r.IS_ENABLED AS INT) AS is_enabled,
    r.STUDY_COUNTRY_IN_ID AS study_country_in_id
FROM MOHECORE.SCHEDULED_REPORT r;
GO

/* ── Verify ────────────────────────────────────────────────────────────────── */
PRINT '=== student coverage: every figure below must be 941 ===';
SELECT 'STUDENT_DATA (baseline)' v, COUNT(*) students FROM MOHECORE.STUDENT_DATA WHERE ISNULL(IS_DELETED,0)=0
UNION ALL SELECT 'V_Enrollment_Statistics', SUM(student_count) FROM MOHECORE.V_Enrollment_Statistics
UNION ALL SELECT 'V_Major_Distribution',    SUM(student_count) FROM MOHECORE.V_Major_Distribution
UNION ALL SELECT 'V_Student_Status_Report', SUM(student_count) FROM MOHECORE.V_Student_Status_Report
UNION ALL SELECT 'V_Student_Demographics',  COUNT(*) FROM MOHECORE.V_Student_Demographics
UNION ALL SELECT 'V_Student_List_Detailed', COUNT(*) FROM MOHECORE.V_Student_List_Detailed
UNION ALL SELECT 'V_Academic_Performance',  COUNT(*) FROM MOHECORE.V_Academic_Performance;
GO

PRINT '=== every view below must now expose study_country_in_id ===';
SELECT v.name AS view_name,
       CASE WHEN COL_LENGTH('MOHECORE.' + v.name, 'study_country_in_id') IS NULL
            THEN '*** MISSING ***' ELSE 'ok' END AS tenant_column
FROM sys.views v
WHERE v.name IN ('V_Student_Demographics','V_Student_List_Detailed','V_Academic_Performance',
                 'V_Student_Status_Report','V_Enrollment_Statistics','V_Major_Distribution',
                 'V_Data_Visualization','V_Export_Manager','V_Report_Template','V_Scheduled_Report')
ORDER BY v.name;
GO
