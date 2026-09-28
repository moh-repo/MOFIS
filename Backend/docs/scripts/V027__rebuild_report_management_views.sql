/*
    V027 — make the three report-management views return real rows.

    Problem
    -------
    V_Export_Manager, V_Report_Template and V_Scheduled_Report were type-only
    stubs. Every column was a literal and every body ended in WHERE 1 = 0:

        CREATE VIEW MOHECORE.V_Export_Manager AS
        SELECT CAST(0 AS BIGINT) AS export_id, N'' AS report_name, ...
        WHERE 1 = 0;

    They exist to give the @Immutable entities (model/ExportManager.java,
    model/ReportTemplate.java, model/ScheduledReport.java) a shape to bind to,
    but they can never produce a row. The Export Manager, Report Templates and
    Scheduled Reports screens under /reports/custom have therefore always
    rendered an empty table — the pages and controllers are correct, their
    source views were placeholders that were never filled in.

    Meanwhile the real tables were already there and already being written:

        REPORT_TYPE_SETTINGS   49 rows   the catalogue of exportable reports
        REPORT_ACCESS_LOG     310 rows   who exported what, when, in which format
        SAVED_QUERY             0 rows   written by SavedQueryService
        SCHEDULED_REPORT        0 rows   written by ScheduledReportExecutionService

    Fix
    ---
    Point each view at its real table. Column names and types are unchanged, so
    no Java, no entity and no repository change is needed.

    Where the source genuinely has no column for something the entity declares,
    the view now returns NULL rather than a made-up constant. NULL renders as an
    empty cell, which is the honest answer to "we do not record this"; a 0 or an
    N'active' would read as a measurement. The columns affected are listed under
    "Still outstanding" below.

    Expected output immediately after applying:

        SELECT COUNT(*) FROM MOHECORE.V_Export_Manager;    -- 49
        SELECT COUNT(*) FROM MOHECORE.V_Report_Template;   --  0  (SAVED_QUERY is empty)
        SELECT COUNT(*) FROM MOHECORE.V_Scheduled_Report;  --  0  (SCHEDULED_REPORT is empty)

    The two zeros are correct and will fill in as users save queries and create
    schedules. Before this script they were zero no matter what the user did.

    Still outstanding after this script
    -----------------------------------
    - V_Export_Manager.total_records / estimated_size_kb are NULL. Row counts are
      per-report and per-filter; nothing in the schema stores them, and computing
      them here would mean running all 49 reports on every page load.
    - V_Report_Template.estimated_records / usage_count are NULL, and
      template_status is NULL because SAVED_QUERY has no lifecycle column. Adding
      a USAGE_COUNT and a STATUS column to SAVED_QUERY is the follow-up; until
      then /by-status/{status} correctly returns nothing.

    Apply in SSMS.
*/

/* ─────────────────────────────────────────────────────────────────────────────
 * V_Export_Manager  ←  REPORT_TYPE_SETTINGS (+ REPORT_ACCESS_LOG)
 *
 * One row per configured report type. Availability comes from IS_ACTIVE; the
 * last export date and the default format are read back from the access log,
 * which records one row per export with its REPORT_CODE and EXPORT_FORMAT.
 * ──────────────────────────────────────────────────────────────────────────── */
CREATE OR ALTER VIEW MOHECORE.V_Export_Manager AS
WITH access_summary AS (
    SELECT
        l.REPORT_CODE,
        MAX(l.ACCESSED_AT) AS last_export_date,
        -- Most recently used export format for this report, ignoring the
        -- QUERY_BUILDER "EXECUTE" pseudo-format which is not a file format.
        MAX(CASE WHEN l.EXPORT_FORMAT IN (N'PDF', N'XLSX', N'DOC')
                 THEN l.EXPORT_FORMAT END) AS last_export_format
    FROM MOHECORE.REPORT_ACCESS_LOG l
    GROUP BY l.REPORT_CODE
)
SELECT
    CAST(s.REPORT_TYPE_ID AS BIGINT) AS export_id,
    COALESCE(NULLIF(LTRIM(RTRIM(s.REPORT_TYPE_NAME_AR)), N''),
             CAST(s.REPORT_TYPE_NAME_EN AS NVARCHAR(510))) AS report_name,
    CAST(COALESCE(NULLIF(LTRIM(RTRIM(s.TITLE_01_AR)), N''),
                  CAST(s.TITLE_01_EN AS NVARCHAR(1000))) AS NVARCHAR(1000)) AS report_description,
    -- No stored row count per report; see header.
    CAST(NULL AS INT) AS total_records,
    CASE WHEN s.IS_ACTIVE = 1 THEN N'available' ELSE N'unavailable' END AS export_status,
    CAST(NULL AS INT) AS estimated_size_kb,
    CAST(a.last_export_date AS DATETIME) AS last_export_date,
    COALESCE(a.last_export_format, N'PDF') AS default_format,
    CAST(s.IS_ACTIVE AS INT) AS is_available,
    CAST(NULL AS INT) AS filter_criteria_id
FROM MOHECORE.REPORT_TYPE_SETTINGS s
LEFT JOIN access_summary a
       ON a.REPORT_CODE = s.REPORT_TYPE_CODE;
GO

/* ─────────────────────────────────────────────────────────────────────────────
 * V_Report_Template  ←  SAVED_QUERY
 *
 * A saved Query Builder query is what this application actually means by a
 * report template: a named source plus a stored filter/column specification.
 * selected_columns is lifted out of the spec JSON (SQL Server 2016+).
 * ──────────────────────────────────────────────────────────────────────────── */
CREATE OR ALTER VIEW MOHECORE.V_Report_Template AS
SELECT
    q.SAVED_QUERY_ID AS template_id,
    q.QUERY_NAME AS template_name,
    q.DESCRIPTION AS template_description,
    q.SOURCE_NAME AS report_type,
    q.SPEC_JSON AS filter_configuration,
    CAST(JSON_QUERY(q.SPEC_JSON, '$.columns') AS NVARCHAR(MAX)) AS selected_columns,
    -- Row count depends on the filters at run time; not stored.
    CAST(NULL AS INT) AS estimated_records,
    CAST(q.CREATED_AT AS DATETIME) AS created_date,
    CAST(q.UPDATED_AT AS DATETIME) AS last_used_date,
    -- SAVED_QUERY records neither a run count nor a lifecycle state; see header.
    CAST(NULL AS INT) AS usage_count,
    CAST(q.IS_SHARED AS INT) AS is_public,
    CAST(NULL AS NVARCHAR(60)) AS template_status
FROM MOHECORE.SAVED_QUERY q;
GO

/* ─────────────────────────────────────────────────────────────────────────────
 * V_Scheduled_Report  ←  SCHEDULED_REPORT
 *
 * Straight projection — the table carries every column the entity declares.
 * ──────────────────────────────────────────────────────────────────────────── */
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
    CAST(r.IS_ENABLED AS INT) AS is_enabled
FROM MOHECORE.SCHEDULED_REPORT r;
GO
