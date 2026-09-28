/*
    V106 — the report-management screens report what is recorded
    ============================================================

    THE DEFECT

    Five columns on the two report-management screens have always been empty,
    because the views select a literal NULL for them and no column anywhere backs
    them. The screens render them regardless: Export Manager prints a blank cell
    and a bare "KB", and Report Templates prints an empty count and an empty
    status tag.

        V_Export_Manager.total_records        CAST(NULL AS INT)
        V_Export_Manager.estimated_size_kb    CAST(NULL AS INT)
        V_Export_Manager.filter_criteria_id   CAST(NULL AS INT)
        V_Report_Template.estimated_records   CAST(NULL AS INT)
        V_Report_Template.usage_count         CAST(NULL AS INT)
        V_Report_Template.template_status     CAST(NULL AS NVARCHAR(60))

    THE FIX, AND THE TWO DIFFERENT ANSWERS IT NEEDS

    Four of the six name facts the system genuinely knows at a moment nobody was
    writing down. Those get a column and a writer:

      * How many times a saved query has been run, and how many rows it returned
        last time. `SavedQueryService.run` is a real execution path -- the Query
        Builder's "run saved query" endpoint -- and it knew both and discarded
        them. SAVED_QUERY gains USAGE_COUNT, LAST_ROW_COUNT and LAST_RUN_AT.

      * How many rows an export contained and how large the file was.
        ReportExports already logs every export to REPORT_ACCESS_LOG and already
        counts the rows for its filter summary; the byte count is the length of
        the array it is about to return. REPORT_ACCESS_LOG gains ROW_COUNT and
        SIZE_BYTES.

    The other two name nothing the system records, and inventing a column for
    them would move the emptiness rather than remove it:

      * `template_status` — a saved query has no lifecycle. There is no draft, no
        archive, no expiry. The screen's tag compares the value to 'Active', a
        state nothing can ever leave. Shared-versus-private is the one real
        distinction and the view already exposes it as `is_public`.

      * `filter_criteria_id` — an internal key from an earlier design. Nothing
        sets it, nothing joins on it, and no template prints it.

    Both are dropped end to end, following V075, which removed nineteen such
    columns rather than back-filling them.

    ON last_used_date

    V_Report_Template mapped it to SAVED_QUERY.UPDATED_AT — when the query was
    last *edited*, not last *used*. It now reads LAST_RUN_AT and falls back to
    UPDATED_AT for queries saved before this script, which have no run recorded.

    DEPLOYMENT

    Apply before the matching build. The new SAVED_QUERY columns are written by
    SavedQueryService.run and the REPORT_ACCESS_LOG ones by ReportExports; both
    are additive, so an older build against this schema simply leaves them at
    their defaults. Idempotent.

    Existing rows start at USAGE_COUNT 0 with NULL LAST_ROW_COUNT and NULL
    LAST_RUN_AT: nothing was recorded before now, and 0 runs is the honest
    reading of "we did not count". They fill as queries are run and reports
    exported.
*/

SET QUOTED_IDENTIFIER ON;
GO

/* ── 1. Columns to record what the writers already know ─────────────────── */

IF COL_LENGTH('MOHECORE.SAVED_QUERY', 'USAGE_COUNT') IS NULL
    ALTER TABLE MOHECORE.SAVED_QUERY ADD USAGE_COUNT INT NOT NULL CONSTRAINT DF_SAVED_QUERY_USAGE_COUNT DEFAULT 0;
GO
IF COL_LENGTH('MOHECORE.SAVED_QUERY', 'LAST_ROW_COUNT') IS NULL
    ALTER TABLE MOHECORE.SAVED_QUERY ADD LAST_ROW_COUNT INT NULL;
GO
IF COL_LENGTH('MOHECORE.SAVED_QUERY', 'LAST_RUN_AT') IS NULL
    ALTER TABLE MOHECORE.SAVED_QUERY ADD LAST_RUN_AT DATETIME2 NULL;
GO

IF COL_LENGTH('MOHECORE.REPORT_ACCESS_LOG', 'ROW_COUNT') IS NULL
    ALTER TABLE MOHECORE.REPORT_ACCESS_LOG ADD ROW_COUNT INT NULL;
GO
IF COL_LENGTH('MOHECORE.REPORT_ACCESS_LOG', 'SIZE_BYTES') IS NULL
    ALTER TABLE MOHECORE.REPORT_ACCESS_LOG ADD SIZE_BYTES BIGINT NULL;
GO

/* ── 2. V_Export_Manager: the last export's real size and row count ──────── */
CREATE OR ALTER VIEW MOHECORE.V_Export_Manager AS
WITH access_summary AS (
    SELECT
        l.REPORT_CODE,
        l.STUDY_COUNTRY_IN_ID,
        MAX(l.ACCESSED_AT) AS last_export_date,
        MAX(CASE WHEN l.EXPORT_FORMAT IN (N'PDF', N'XLSX', N'DOC')
                 THEN l.EXPORT_FORMAT END) AS last_export_format,
        -- The most recent export's figures, not the largest: these describe
        -- "what you last got", which is what the screen's column headings claim.
        MAX(CASE WHEN l.ACCESSED_AT = m.latest THEN l.ROW_COUNT END)  AS last_row_count,
        MAX(CASE WHEN l.ACCESSED_AT = m.latest THEN l.SIZE_BYTES END) AS last_size_bytes
    FROM MOHECORE.REPORT_ACCESS_LOG l
    CROSS APPLY (
        SELECT MAX(l2.ACCESSED_AT) AS latest
        FROM MOHECORE.REPORT_ACCESS_LOG l2
        WHERE l2.REPORT_CODE = l.REPORT_CODE
          AND (l2.STUDY_COUNTRY_IN_ID = l.STUDY_COUNTRY_IN_ID
               OR (l2.STUDY_COUNTRY_IN_ID IS NULL AND l.STUDY_COUNTRY_IN_ID IS NULL))
    ) m
    GROUP BY l.REPORT_CODE, l.STUDY_COUNTRY_IN_ID
)
SELECT
    CAST(s.REPORT_TYPE_ID AS BIGINT) AS export_id,
    COALESCE(NULLIF(LTRIM(RTRIM(s.REPORT_TYPE_NAME_AR)), N''),
             CAST(s.REPORT_TYPE_NAME_EN AS NVARCHAR(510))) AS report_name,
    CAST(COALESCE(NULLIF(LTRIM(RTRIM(s.TITLE_01_AR)), N''),
                  CAST(s.TITLE_01_EN AS NVARCHAR(1000))) AS NVARCHAR(1000)) AS report_description,
    a.last_row_count AS total_records,
    CASE WHEN s.IS_ACTIVE = 1 THEN N'available' ELSE N'unavailable' END AS export_status,
    -- Rounded up: a 400-byte export is "1 KB", not "0 KB". NULL stays NULL --
    -- a report nobody has exported has no size, which is not the same as zero.
    CAST(CASE WHEN a.last_size_bytes IS NULL THEN NULL
              ELSE (a.last_size_bytes + 1023) / 1024 END AS INT) AS estimated_size_kb,
    CAST(a.last_export_date AS DATETIME) AS last_export_date,
    COALESCE(a.last_export_format, N'PDF') AS default_format,
    CAST(s.IS_ACTIVE AS INT) AS is_available,
    -- The report catalogue itself is ministry-wide; the country comes from who
    -- has exported it. NULL = never exported, so visible to everyone.
    a.STUDY_COUNTRY_IN_ID AS study_country_in_id
FROM MOHECORE.REPORT_TYPE_SETTINGS s
LEFT JOIN access_summary a ON a.REPORT_CODE = s.REPORT_TYPE_CODE;
GO

/* ── 3. V_Report_Template: real usage, and no invented status ───────────── */
CREATE OR ALTER VIEW MOHECORE.V_Report_Template AS
SELECT
    q.SAVED_QUERY_ID AS template_id,
    q.QUERY_NAME AS template_name,
    q.DESCRIPTION AS template_description,
    q.SOURCE_NAME AS report_type,
    q.SPEC_JSON AS filter_configuration,
    CAST(JSON_QUERY(q.SPEC_JSON, '$.columns') AS NVARCHAR(MAX)) AS selected_columns,
    q.LAST_ROW_COUNT AS estimated_records,
    CAST(q.CREATED_AT AS DATETIME) AS created_date,
    -- Last *used*, not last edited. UPDATED_AT is the fallback for queries saved
    -- before V106, which have no run recorded.
    CAST(COALESCE(q.LAST_RUN_AT, q.UPDATED_AT) AS DATETIME) AS last_used_date,
    q.USAGE_COUNT AS usage_count,
    CAST(q.IS_SHARED AS INT) AS is_public,
    q.STUDY_COUNTRY_IN_ID AS study_country_in_id
FROM MOHECORE.SAVED_QUERY q;
GO

/*  Verification — the six columns should now be four real ones and two gone.  */
EXEC sp_set_session_context @key = N'TenantAll', @value = N'1', @read_only = 0;

SELECT 'dropped columns still present' AS check_name, v.name AS vw, c.name AS col
FROM sys.views v JOIN sys.columns c ON c.object_id = v.object_id
WHERE c.name IN ('template_status', 'filter_criteria_id')
  AND v.name IN ('V_Report_Template', 'V_Export_Manager');

SELECT TOP 10 report_name, total_records, estimated_size_kb, last_export_date
FROM MOHECORE.V_Export_Manager
WHERE last_export_date IS NOT NULL
ORDER BY last_export_date DESC;

SELECT template_name, usage_count, estimated_records, last_used_date
FROM MOHECORE.V_Report_Template;
GO
