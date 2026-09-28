/* ============================================================================
   V108 — The two catalogue rows the templates need to stop printing constants.

   Why
   ---
   1. COL_USAGE_COUNT. V106 removed `template_status` from V_Report_Template,
      from the ReportTemplate entity and from the screen, because a saved query
      has no lifecycle to report -- but report-templates.jrxml kept declaring
      `<field name="templateStatus">` and printing it in its third column.
      JRBeanCollectionDataSource resolves a field by bean property, so the fill
      throws `Unknown property 'templateStatus'` the moment there is a row to
      read it from. Nothing noticed because V_Report_Template reads SAVED_QUERY
      and no environment has a saved query yet: with zero rows no field is ever
      resolved. The first user to save one would have broken the export.

      The column becomes USAGE_COUNT, which V106 started recording and which the
      screen already shows in that position, and this seeds its heading.

   2. DATA_VISUALIZATION_CHART. V_Data_Visualization names every one of its rows
      with a hardcoded N'Student Distribution'. That is the mirror of finding 4
      of docs/report-data-audit.md -- a label written in English inside SQL, so
      the *Arabic* report prints English -- and it also means /chart-names, which
      feeds a filter, has exactly one value to offer and it is invented.

      The view cuts its slices by two dimensions, status and country, so there
      are two charts and they have real names. LU_REPORT_DIMENSION is where a
      report axis's bilingual name already lives (V004 seeded the group-by axes,
      V028 the age bands), so the names go there and V109 joins them.

   What
   ----
   Two MERGEs, both idempotent, both keyed the way their table is keyed.

   Rollback:
       DELETE FROM MOHECORE.LU_REPORT_LABEL     WHERE CODE = N'COL_USAGE_COUNT';
       DELETE FROM MOHECORE.LU_REPORT_DIMENSION WHERE GROUP_CODE = N'DATA_VISUALIZATION_CHART';
       -- and revert V109, which reads them.

   After applying
       mvn -o test -Dtest=ReportLabelsAreSeededTest,ReportTemplatesRenderTest,DataVisualizationRenderTest
   ============================================================================ */

SET QUOTED_IDENTIFIER ON;
GO

/* ── 1. The heading for the saved-query listing's third column ───────────────
   SORT_ORDER continues after V079, which used 269-307.                      */
DECLARE @done TABLE (act NVARCHAR(10));

MERGE MOHECORE.LU_REPORT_LABEL AS tgt
USING (VALUES
    (N'COL_USAGE_COUNT', N'عدد الاستخدام', N'Usage Count', 308)
) AS src (CODE, NAME_AR, NAME_EN, SORT_ORDER)
   ON tgt.CODE = src.CODE
WHEN MATCHED AND tgt.IS_SYSTEM = 0 THEN UPDATE SET IS_SYSTEM = 1
WHEN NOT MATCHED THEN
    INSERT (CODE, NAME_AR, NAME_EN, SORT_ORDER, IS_DELETED, IS_SYSTEM)
    VALUES (src.CODE, src.NAME_AR, src.NAME_EN, src.SORT_ORDER, 0, 1)
OUTPUT $action INTO @done;

DECLARE @inserted INT, @updated INT;
SELECT @inserted = COUNT(*) FROM @done WHERE act = N'INSERT';
SELECT @updated  = COUNT(*) FROM @done WHERE act = N'UPDATE';
PRINT 'V108: labels inserted = ' + CAST(@inserted AS VARCHAR(10))
    + ', flagged IS_SYSTEM = ' + CAST(@updated AS VARCHAR(10))
    + ' (first run 1 / 0, re-run 0 / 0)';
GO

/* ── 2. The names of the two charts the visualization view actually cuts ─────
   The CODE is the view's own `category` key, so V109 joins on it directly and
   a third dimension added later needs a row here and nothing else.          */
MERGE MOHECORE.LU_REPORT_DIMENSION AS tgt
USING (VALUES
    (N'DATA_VISUALIZATION_CHART', N'status',  N'توزيع الطلاب حسب الحالة', N'Students by Status',  1),
    (N'DATA_VISUALIZATION_CHART', N'country', N'توزيع الطلاب حسب الدولة', N'Students by Country', 2)
) AS src (GROUP_CODE, CODE, NAME_AR, NAME_EN, SORT_ORDER)
    ON tgt.GROUP_CODE = src.GROUP_CODE AND tgt.CODE = src.CODE
WHEN NOT MATCHED BY TARGET THEN
    INSERT (GROUP_CODE, CODE, NAME_AR, NAME_EN, SORT_ORDER, IS_DELETED)
    VALUES (src.GROUP_CODE, src.CODE, src.NAME_AR, src.NAME_EN, src.SORT_ORDER, 0);
GO

/* ── 3. Both rows must be there and active, or the reports that read them
       print blank rather than failing, which is the harder thing to notice. */
IF NOT EXISTS (SELECT 1 FROM MOHECORE.LU_REPORT_LABEL
                WHERE CODE = N'COL_USAGE_COUNT' AND IS_DELETED = 0 AND IS_SYSTEM = 1)
    THROW 50108, N'V108: COL_USAGE_COUNT is missing or inactive after the merge', 1;

DECLARE @charts INT;
SELECT @charts = COUNT(*) FROM MOHECORE.LU_REPORT_DIMENSION
 WHERE GROUP_CODE = N'DATA_VISUALIZATION_CHART' AND IS_DELETED = 0;
IF @charts <> 2
BEGIN
    DECLARE @msg NVARCHAR(200) = N'V108: expected 2 DATA_VISUALIZATION_CHART rows, found '
        + CAST(@charts AS NVARCHAR(10));
    THROW 50108, @msg, 1;
END
PRINT 'V108: DATA_VISUALIZATION_CHART rows = 2';
GO
