/*
    V124 — English names for the export-manager report
    (plan Wave 3: W3-06; QA B6 / N4, live QA #12).

    THE DEFECT

    V_Export_Manager exposed the report's Arabic name and title only, so
    export-manager.jrxml?lang=en printed English headings over Arabic report
    names -- one of the seven reports N4 found still Arabic in English output.
    The other six carry their SQL in the Java code (a subselect or a repository
    query) and are fixed there; this is the only one that reads a view.

    THE FIX

    The view gains report_name_en and report_description_en beside the Arabic
    columns, in the V104 shape: the English text, falling back to the Arabic where
    none was recorded, so an English report prints a name rather than a blank.
    BilingualRows (JasperReportService.fill) swaps them in for an English render.
    Every other column is V106's, unchanged, so nothing that reads the view today
    changes behaviour.

    REPORT_TYPE_SETTINGS has an English name on all 57 rows and an English title
    on 53 (measured 22 September 2026), so the title fallback is the routine path
    for four reports and the name fallback is a guard.

    Idempotent (CREATE OR ALTER). THROWs if a column is missing afterwards or if
    the view's row count changed. Run with sqlcmd -I -f 65001 -b.
*/
SET NOCOUNT ON;
SET XACT_ABORT ON;
SET QUOTED_IDENTIFIER ON;

EXEC sp_set_session_context @key = N'TenantAll', @value = N'1';

IF OBJECT_ID(N'tempdb..#v124_before') IS NOT NULL DROP TABLE #v124_before;
SELECT COUNT(*) AS rows_ INTO #v124_before FROM MOHECORE.V_Export_Manager;
GO

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
    CAST(COALESCE(NULLIF(LTRIM(RTRIM(s.REPORT_TYPE_NAME_EN)), ''), s.REPORT_TYPE_NAME_AR) AS NVARCHAR(510)) AS report_name_en,
    CAST(COALESCE(NULLIF(LTRIM(RTRIM(s.TITLE_01_AR)), N''),
                  CAST(s.TITLE_01_EN AS NVARCHAR(1000))) AS NVARCHAR(1000)) AS report_description,
    CAST(COALESCE(NULLIF(LTRIM(RTRIM(s.TITLE_01_EN)), ''), s.TITLE_01_AR) AS NVARCHAR(1000)) AS report_description_en,
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

/* ---- checks ---------------------------------------------------------------------- */
SET NOCOUNT ON;
SET XACT_ABORT ON;
EXEC sp_set_session_context @key = N'TenantAll', @value = N'1';

IF COL_LENGTH(N'MOHECORE.V_Export_Manager', N'report_name_en') IS NULL
   OR COL_LENGTH(N'MOHECORE.V_Export_Manager', N'report_description_en') IS NULL
    THROW 51240, 'V124: V_Export_Manager has no English name columns', 1;

IF (SELECT COUNT(*) FROM MOHECORE.V_Export_Manager) <> (SELECT rows_ FROM #v124_before)
    THROW 51241, 'V124: V_Export_Manager changed its row count', 1;

IF EXISTS (SELECT 1 FROM MOHECORE.V_Export_Manager
            WHERE report_name IS NOT NULL AND NULLIF(LTRIM(RTRIM(report_name_en)), N'') IS NULL)
    THROW 51242, 'V124: a report has an Arabic name but no English one, not even the fallback', 1;

DROP TABLE #v124_before;
GO
