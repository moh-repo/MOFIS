/* ============================================================================
   V109 — The last two reports that printed constants instead of data.

   Finding 4 of docs/report-data-audit.md recorded two columns as deliberately
   left Arabic-only, each for its own reason. Looking at what the two reports
   actually print showed both reasons were understated.

   1. V_Data_Visualization

      chart_name was a hardcoded N'Student Distribution' on every row: the
      mirror of finding 4, an English label written inside SQL, so the *Arabic*
      report printed English. It also meant /chart-names, which feeds a filter,
      had exactly one value to offer and that value was invented rather than
      read from anything.

      The view cuts its slices by two dimensions, status and country, so there
      are two charts and they have real names. V108 put those names in
      LU_REPORT_DIMENSION, bilingual, under DATA_VISUALIZATION_CHART, keyed by
      the view's own `category`. This joins them.

      data_label -- the slice's own name, 'Active' or 'Yemen' -- gets an English
      sibling on the same COALESCE(NULLIF(en,''), ar) shape as V104-V107,
      because the template now prints it.

   2. V_Distribution_Statistics

      category_name was recorded as "a UNION whose branches draw from four
      different lookups; each needs its own join, so it is its own change".
      This is that change: country, major, degree and status each get their own
      English source.

      Note the view is legacy. DistributionStatisticsService reads the native
      four-way UNION in DistributionStatisticsRepository instead, and that query
      is edited in step with this one -- it is what reaches the page. The view
      still backs the entity's JPQL finders, and ddl-auto=none means the
      entity's new property needs the column here or those finders fail at query
      time. The two also disagree about which country a 'country' row counts:
      the view groups by nationality (STUDENT_COUNTRY), the repository by study
      country (STUDY_COUNTRY_IN). That drift predates this script and is left
      alone -- recorded in the audit rather than silently reconciled.

   The N'Unknown' fallbacks become a language pair, غير محدد / Unspecified --
   the wording V076 already seeded as LBL_UNSPECIFIED. Each GROUP BY keys on the
   underlying column, not on the ISNULL expression, so the displayed text
   changes and the grouping does not.

   Both views are rewritten whole rather than patched: both needed a join added,
   and one needed it in four branches.

   Idempotent (CREATE OR ALTER). View-only, but apply as the Flyway/admin login
   and verify the result afterwards: under mohe_app these statements emit a
   misleading Msg 3701 and may or may not have applied (see MIGRATION_RUNBOOK).

   Apply V108 first -- this reads the rows it seeds.

   After applying
       python scripts/audit_report_data.py --check
       mvn -o test -Dtest=BilingualViewMappingTest,DataVisualizationRenderTest
   ============================================================================ */

SET QUOTED_IDENTIFIER ON;
GO

/* -- 1. V_Data_Visualization -------------------------------------------------
   The inner query is unchanged apart from the English sibling of data_label
   and the bilingual fallback. How the slices are cut, and the per-country
   partitioning V030 introduced, are left exactly as they were.              */
CREATE OR ALTER VIEW MOHECORE.V_Data_Visualization AS
SELECT
    ROW_NUMBER() OVER (ORDER BY v.study_country_in_id, v.category, v.data_label) AS visualization_id,
    -- The chart's name, read from the catalogue rather than asserted here.
    -- ISNULL falls back to the key so a dimension added to the view without a
    -- row in LU_REPORT_DIMENSION prints something identifiable, not NULL.
    ISNULL(dim.NAME_AR, v.category) AS chart_name,
    ISNULL(COALESCE(NULLIF(dim.NAME_EN, N''), dim.NAME_AR), v.category) AS chart_name_en,
    N'pie' AS chart_type,
    v.category,
    v.data_value,
    v.data_label,
    v.data_label_en,
    N'#4472C4' AS color_code,
    ROW_NUMBER() OVER (PARTITION BY v.study_country_in_id, v.category ORDER BY v.data_value DESC) AS sort_order,
    v.study_country_in_id
FROM (
    SELECT 'status' AS category,
           sd.STUDY_COUNTRY_IN AS study_country_in_id,
           COUNT(*) AS data_value,
           ISNULL(sss.STUDENT_STUDY_STATUS_NAME, N'غير محدد') AS data_label,
           ISNULL(COALESCE(NULLIF(sss.STUDENT_STUDY_STATUS_NAME_EN, N''), sss.STUDENT_STUDY_STATUS_NAME),
                  N'Unspecified') AS data_label_en
    FROM MOHECORE.STUDENT_DATA sd
    LEFT JOIN MOHECORE.STUDENT_STUDY_STATUS sss ON sss.STUDENT_STUDY_STATUS_ID = sd.STUDENT_STUDY_STATUS_ID
    WHERE ISNULL(sd.IS_DELETED, 0) = 0
    GROUP BY sd.STUDY_COUNTRY_IN, sss.STUDENT_STUDY_STATUS_NAME, sss.STUDENT_STUDY_STATUS_NAME_EN

    UNION ALL

    SELECT 'country' AS category,
           sd.STUDY_COUNTRY_IN AS study_country_in_id,
           COUNT(*) AS data_value,
           ISNULL(sc.STUDENT_COUNTRY_NAME, N'غير محدد') AS data_label,
           ISNULL(COALESCE(NULLIF(sc.STUDENT_COUNTRY_NAME_EN, N''), sc.STUDENT_COUNTRY_NAME),
                  N'Unspecified') AS data_label_en
    FROM MOHECORE.STUDENT_DATA sd
    LEFT JOIN MOHECORE.STUDENT_COUNTRY sc ON sc.STUDENT_COUNTRY_ID = sd.STUDENT_COUNTRY_ID
    WHERE ISNULL(sd.IS_DELETED, 0) = 0
    GROUP BY sd.STUDY_COUNTRY_IN, sc.STUDENT_COUNTRY_NAME, sc.STUDENT_COUNTRY_NAME_EN
) v
LEFT JOIN MOHECORE.LU_REPORT_DIMENSION dim
       ON dim.GROUP_CODE = N'DATA_VISUALIZATION_CHART'
      AND dim.CODE       = v.category
      AND dim.IS_DELETED = 0;
GO

/* -- 2. V_Distribution_Statistics --------------------------------------------
   Four branches, four lookups, four English sources.                        */
CREATE OR ALTER VIEW MOHECORE.V_Distribution_Statistics AS
SELECT distribution_type, category_name, category_name_en, student_count,
       CAST(student_count * 100.0 / SUM(student_count) OVER (PARTITION BY distribution_type) AS DECIMAL(5,2)) AS percentage_of_total,
       RANK() OVER (PARTITION BY distribution_type ORDER BY student_count DESC) AS rank_position,
       CASE WHEN student_count >= 20 THEN 'major' WHEN student_count >= 5 THEN 'minor' ELSE 'marginal' END AS category_significance
FROM (
    -- by country (nationality; the repository counts study country instead)
    SELECT 'country' AS distribution_type,
           ISNULL(sc.STUDENT_COUNTRY_NAME, N'غير محدد') AS category_name,
           ISNULL(COALESCE(NULLIF(sc.STUDENT_COUNTRY_NAME_EN, N''), sc.STUDENT_COUNTRY_NAME),
                  N'Unspecified') AS category_name_en,
           COUNT(*) AS student_count
    FROM MOHECORE.STUDENT_DATA sd
    LEFT JOIN MOHECORE.STUDENT_COUNTRY sc ON sc.STUDENT_COUNTRY_ID = sd.STUDENT_COUNTRY_ID
    WHERE ISNULL(sd.IS_DELETED, 0) = 0
    GROUP BY sc.STUDENT_COUNTRY_NAME, sc.STUDENT_COUNTRY_NAME_EN

    UNION ALL

    -- by major
    SELECT 'major',
           ISNULL(sm.STUDENT_MAJOR_NAME, N'غير محدد'),
           ISNULL(COALESCE(NULLIF(sm.STUDENT_MAJOR_NAME_EN, N''), sm.STUDENT_MAJOR_NAME), N'Unspecified'),
           COUNT(*)
    FROM MOHECORE.STUDENT_DATA sd
    LEFT JOIN MOHECORE.STUDENT_MAJOR sm ON sm.STUDENT_MAJOR_ID = sd.STUDENT_MAJOR_ID
    WHERE ISNULL(sd.IS_DELETED, 0) = 0
    GROUP BY sm.STUDENT_MAJOR_NAME, sm.STUDENT_MAJOR_NAME_EN

    UNION ALL

    -- by degree
    SELECT 'degree',
           ISNULL(sdeg.STUDENT_DEGREE_NAME, N'غير محدد'),
           ISNULL(COALESCE(NULLIF(sdeg.STUDENT_DEGREE_NAME_EN, N''), sdeg.STUDENT_DEGREE_NAME), N'Unspecified'),
           COUNT(*)
    FROM MOHECORE.STUDENT_DATA sd
    LEFT JOIN MOHECORE.STUDENT_DEGREE sdeg ON sdeg.STUDENT_DEGREE_ID = sd.STUDENT_DEGREE_ID
    WHERE ISNULL(sd.IS_DELETED, 0) = 0
    GROUP BY sdeg.STUDENT_DEGREE_NAME, sdeg.STUDENT_DEGREE_NAME_EN

    UNION ALL

    -- by status
    SELECT 'status',
           ISNULL(sss.STUDENT_STUDY_STATUS_NAME, N'غير محدد'),
           ISNULL(COALESCE(NULLIF(sss.STUDENT_STUDY_STATUS_NAME_EN, N''), sss.STUDENT_STUDY_STATUS_NAME), N'Unspecified'),
           COUNT(*)
    FROM MOHECORE.STUDENT_DATA sd
    LEFT JOIN MOHECORE.STUDENT_STUDY_STATUS sss ON sss.STUDENT_STUDY_STATUS_ID = sd.STUDENT_STUDY_STATUS_ID
    WHERE ISNULL(sd.IS_DELETED, 0) = 0
    GROUP BY sss.STUDENT_STUDY_STATUS_NAME, sss.STUDENT_STUDY_STATUS_NAME_EN
) base;
GO

/* -- 3. Both views must expose their new columns, or the entities that map them
      fail at query time rather than at startup.                             */
DECLARE @missing NVARCHAR(MAX);
SELECT @missing = STUFF((
    SELECT N', ' + c.view_name + N'.' + c.col
    FROM (VALUES
        (N'V_Data_Visualization',      N'chart_name_en'),
        (N'V_Data_Visualization',      N'data_label_en'),
        (N'V_Distribution_Statistics', N'category_name_en')
    ) AS c(view_name, col)
    JOIN sys.views v ON v.name = c.view_name AND v.schema_id = SCHEMA_ID('MOHECORE')
    WHERE NOT EXISTS (
        SELECT 1 FROM sys.columns sc
         WHERE sc.object_id = v.object_id AND sc.name = c.col)
    FOR XML PATH(''), TYPE).value('.', 'NVARCHAR(MAX)'), 1, 2, N'');

IF @missing IS NOT NULL
BEGIN
    DECLARE @msg NVARCHAR(MAX) = N'V109: columns missing after the rewrite: ' + @missing;
    THROW 50109, @msg, 1;
END
PRINT 'V109: both views expose their English columns';
GO
