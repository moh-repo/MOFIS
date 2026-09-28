/*
    V104 — English reports print English data: the student report views
    ===================================================================

    THE DEFECT (finding 4 of docs/report-data-audit.md)

    Every report accepts ?lang=ar|en and ReportParamBuilder switches the titles,
    column headings, labels and footer correctly. The rows did not follow. The
    report views expose a single Arabic name column per lookup, so
    student-list.jrxml?lang=en printed English headings over Arabic country,
    major, degree and status names.

    THE FIX

    Each view gains a paired *_NAME_EN column beside its Arabic name. The Arabic
    column keeps its name and its meaning, so nothing that reads these views
    today changes behaviour; the English column is additive.

    Resolution happens once, centrally: BilingualRows (wired into
    JasperReportService.fill) copies each row for an English render with every
    property x replaced by its sibling xEn. Templates keep printing
    $F{studentCountryName} and never branch on $P{LANG}.

    The COALESCE(NULLIF(en,''), ar) shape matters. It is not decoration:
    STUDENT_AGENCY_TYPE has 4 of 11 rows with no English name, so without the
    fallback an English report would print blanks where it can print Arabic.
    Arabic in an English report is a translation gap; a blank cell is a missing
    fact, and the second is worse. The remaining eleven lookups these views touch
    are fully translated (STUDENT_COUNTRY 17/17, STUDENT_MAJOR 440/440,
    STUDENT_DEGREE 15/15, STUDENT_CURRENT_LEVEL 21/21, STUDENT_STUDY_STATUS 9/9,
    STUDENT_STUDY_TYPE 9/9, STUDENT_UNIVERSITY_TYPE 16/16, STUDENT_GENDER 3/3,
    STUDENT_MINISTRY_TYPE 7/7 — measured 7 September 2026), so the fallback is a
    guard rather than the routine path.

    Every lookup join in these four views was already a LEFT JOIN, so step 2 of
    the remediation recipe needed no change here.

    SCOPE

    The four student-facing views, in the order the audit recommended:
    V_Student_List_Detailed, V_Student_Status_Report, V_Student_Demographics,
    V_Academic_Performance. The university, financial and periodic groups are a
    later script; they follow the identical shape.

    AFTER APPLYING

        python scripts/audit_report_data.py
        mvn -o test -Dtest=BilingualRowsTest,BilingualViewMappingTest

    and render student-list with lang=en: country, major, degree, level, study
    type and status should read in English.
*/

SET QUOTED_IDENTIFIER ON;
GO

/* ── 1. V_Student_List_Detailed ─────────────────────────────────────────── */
CREATE OR ALTER VIEW MOHECORE.V_Student_List_Detailed AS
SELECT
    sd.STUDENT_ID,
    sd.STUDENT_NAME,
    sd.STUDENT_PASSPORT_NUMBER,
    CAST(sd.STUDENT_BIRTHDATE AS DATE)   AS STUDENT_BIRTHDATE,
    TRY_CAST(sd.STUDENT_AGE AS INT)      AS student_age,
    sd.STUDY_COUNTRY_IN                  AS study_country_in_id,
    sd.STUDENT_COUNTRY_ID,
    ISNULL(sc.STUDENT_COUNTRY_NAME, N'') AS STUDENT_COUNTRY_NAME,
    ISNULL(COALESCE(NULLIF(sc.STUDENT_COUNTRY_NAME_EN, N''), sc.STUDENT_COUNTRY_NAME), N'') AS STUDENT_COUNTRY_NAME_EN,
    ISNULL(sg.STUDENT_GENDER_NAME, N'')  AS STUDENT_GENDER,
    ISNULL(COALESCE(NULLIF(sg.STUDENT_GENDER_NAME_EN, N''), sg.STUDENT_GENDER_NAME), N'') AS STUDENT_GENDER_EN,
    sd.STUDENT_MAJOR_ID,
    ISNULL(sm.STUDENT_MAJOR_NAME, N'') AS STUDENT_MAJOR_NAME,
    ISNULL(COALESCE(NULLIF(sm.STUDENT_MAJOR_NAME_EN, N''), sm.STUDENT_MAJOR_NAME), N'') AS STUDENT_MAJOR_NAME_EN,
    sd.STUDENT_DEGREE_ID,
    ISNULL(sdeg.STUDENT_DEGREE_NAME, N'') AS STUDENT_DEGREE_NAME,
    ISNULL(COALESCE(NULLIF(sdeg.STUDENT_DEGREE_NAME_EN, N''), sdeg.STUDENT_DEGREE_NAME), N'') AS STUDENT_DEGREE_NAME_EN,
    sd.STUDENT_CURRENT_LEVEL_ID,
    ISNULL(scl.STUDENT_CURRENT_LEVEL_NAME, N'') AS STUDENT_CURRENT_LEVEL_NAME,
    ISNULL(COALESCE(NULLIF(scl.STUDENT_CURRENT_LEVEL_NAME_EN, N''), scl.STUDENT_CURRENT_LEVEL_NAME), N'') AS STUDENT_CURRENT_LEVEL_NAME_EN,
    sd.STUDENT_STUDY_TYPE_ID,
    ISNULL(sst.STUDENT_STUDY_TYPE_NAME, N'') AS STUDENT_STUDY_TYPE_NAME,
    ISNULL(COALESCE(NULLIF(sst.STUDENT_STUDY_TYPE_NAME_EN, N''), sst.STUDENT_STUDY_TYPE_NAME), N'') AS STUDENT_STUDY_TYPE_NAME_EN,
    sd.STUDENT_STUDY_STATUS_ID,
    ISNULL(sss.STUDENT_STUDY_STATUS_NAME, N'') AS STUDENT_STUDY_STATUS_NAME,
    ISNULL(COALESCE(NULLIF(sss.STUDENT_STUDY_STATUS_NAME_EN, N''), sss.STUDENT_STUDY_STATUS_NAME), N'') AS STUDENT_STUDY_STATUS_NAME_EN,
    sd.STUDENT_UNIVERSITY_TYPE_ID,
    ISNULL(sut.STUDENT_UNIVERSITY_TYPE_NAME, N'') AS STUDENT_UNIVERSITY_TYPE_NAME,
    ISNULL(COALESCE(NULLIF(sut.STUDENT_UNIVERSITY_TYPE_NAME_EN, N''), sut.STUDENT_UNIVERSITY_TYPE_NAME), N'') AS STUDENT_UNIVERSITY_TYPE_NAME_EN,
    sd.STUDENT_AGENCY_TYPE_ID,
    ISNULL(sat.STUDENT_AGENCY_TYPE_NAME, N'') AS STUDENT_AGENCY_TYPE_NAME,
    ISNULL(COALESCE(NULLIF(sat.STUDENT_AGENCY_TYPE_NAME_EN, N''), sat.STUDENT_AGENCY_TYPE_NAME), N'') AS STUDENT_AGENCY_TYPE_NAME_EN,
    sd.STUDENT_MINISTRY_TYPE_ID,
    ISNULL(smt.STUDENT_MINISTRY_TYPE_NAME, N'') AS STUDENT_MINISTRY_TYPE_NAME,
    ISNULL(COALESCE(NULLIF(smt.STUDENT_MINISTRY_TYPE_NAME_EN, N''), smt.STUDENT_MINISTRY_TYPE_NAME), N'') AS STUDENT_MINISTRY_TYPE_NAME_EN,
    sd.STUDENT_STUDY_NO_OF_YEARS,
    sd.STUDENT_DELEGATION_DECISION_YEAR,
    sd.STUDENT_ADDED_QUARTER,
    CAST(sd.STUDENT_PASSPORT_ISSUE_DATE AS DATE)  AS STUDENT_PASSPORT_ISSUE_DATE,
    CAST(sd.STUDENT_PASSPORT_EXPIRE_DATE AS DATE) AS STUDENT_PASSPORT_EXPIRE_DATE
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

/* ── 2. V_Student_Status_Report ─────────────────────────────────────────────
   The English names join the GROUP BY beside their Arabic counterparts. They are
   functionally dependent on the same lookup id, so this adds no rows.          */
CREATE OR ALTER VIEW MOHECORE.V_Student_Status_Report AS
SELECT
    sd.STUDY_COUNTRY_IN AS study_country_in_id,                     -- tenant key
    ISNULL(sd.STUDENT_STUDY_STATUS_ID, 0) AS STUDENT_STUDY_STATUS_ID,
    ISNULL(sd.STUDENT_COUNTRY_ID, 0)      AS STUDENT_COUNTRY_ID,     -- nationality
    ISNULL(sd.STUDENT_MAJOR_ID, 0)        AS STUDENT_MAJOR_ID,
    ISNULL(sss.STUDENT_STUDY_STATUS_NAME, N'') AS STUDENT_STUDY_STATUS_NAME,
    ISNULL(COALESCE(NULLIF(sss.STUDENT_STUDY_STATUS_NAME_EN, N''), sss.STUDENT_STUDY_STATUS_NAME), N'') AS STUDENT_STUDY_STATUS_NAME_EN,
    ISNULL(sc.STUDENT_COUNTRY_NAME, N'') AS STUDENT_COUNTRY_NAME,
    ISNULL(COALESCE(NULLIF(sc.STUDENT_COUNTRY_NAME_EN, N''), sc.STUDENT_COUNTRY_NAME), N'') AS STUDENT_COUNTRY_NAME_EN,
    ISNULL(sm.STUDENT_MAJOR_NAME, N'') AS STUDENT_MAJOR_NAME,
    ISNULL(COALESCE(NULLIF(sm.STUDENT_MAJOR_NAME_EN, N''), sm.STUDENT_MAJOR_NAME), N'') AS STUDENT_MAJOR_NAME_EN,
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
         sss.STUDENT_STUDY_STATUS_NAME, sss.STUDENT_STUDY_STATUS_NAME_EN,
         sc.STUDENT_COUNTRY_NAME,       sc.STUDENT_COUNTRY_NAME_EN,
         sm.STUDENT_MAJOR_NAME,         sm.STUDENT_MAJOR_NAME_EN,
         sss.CODE;
GO

/* ── 3. V_Student_Demographics ──────────────────────────────────────────── */
CREATE OR ALTER VIEW MOHECORE.V_Student_Demographics AS
SELECT
    sd.STUDENT_ID,
    sd.STUDENT_NAME,
    -- Tenant key. STUDENT_COUNTRY_ID below is the student's nationality and is
    -- deliberately NOT the column scope filters on.
    sd.STUDY_COUNTRY_IN                  AS study_country_in_id,
    sd.STUDENT_COUNTRY_ID,
    ISNULL(sc.STUDENT_COUNTRY_NAME, N'') AS STUDENT_COUNTRY_NAME,
    ISNULL(COALESCE(NULLIF(sc.STUDENT_COUNTRY_NAME_EN, N''), sc.STUDENT_COUNTRY_NAME), N'') AS STUDENT_COUNTRY_NAME_EN,
    ISNULL(sg.STUDENT_GENDER_NAME, N'')  AS STUDENT_GENDER,
    ISNULL(COALESCE(NULLIF(sg.STUDENT_GENDER_NAME_EN, N''), sg.STUDENT_GENDER_NAME), N'') AS STUDENT_GENDER_EN,
    TRY_CAST(sd.STUDENT_AGE AS INT)      AS student_age,
    sd.STUDENT_CURRENT_LEVEL_ID,
    ISNULL(scl.STUDENT_CURRENT_LEVEL_NAME, N'') AS STUDENT_CURRENT_LEVEL_NAME,
    ISNULL(COALESCE(NULLIF(scl.STUDENT_CURRENT_LEVEL_NAME_EN, N''), scl.STUDENT_CURRENT_LEVEL_NAME), N'') AS STUDENT_CURRENT_LEVEL_NAME_EN,
    sd.STUDENT_STUDY_TYPE_ID,
    ISNULL(sst.STUDENT_STUDY_TYPE_NAME, N'') AS STUDENT_STUDY_TYPE_NAME,
    ISNULL(COALESCE(NULLIF(sst.STUDENT_STUDY_TYPE_NAME_EN, N''), sst.STUDENT_STUDY_TYPE_NAME), N'') AS STUDENT_STUDY_TYPE_NAME_EN,
    sd.STUDENT_DEGREE_ID,
    ISNULL(sdeg.STUDENT_DEGREE_NAME, N'') AS STUDENT_DEGREE_NAME,
    ISNULL(COALESCE(NULLIF(sdeg.STUDENT_DEGREE_NAME_EN, N''), sdeg.STUDENT_DEGREE_NAME), N'') AS STUDENT_DEGREE_NAME_EN,
    sd.STUDENT_MAJOR_ID,
    ISNULL(sm.STUDENT_MAJOR_NAME, N'') AS STUDENT_MAJOR_NAME,
    ISNULL(COALESCE(NULLIF(sm.STUDENT_MAJOR_NAME_EN, N''), sm.STUDENT_MAJOR_NAME), N'') AS STUDENT_MAJOR_NAME_EN,
    sd.STUDENT_STUDY_STATUS_ID,
    ISNULL(sss.STUDENT_STUDY_STATUS_NAME, N'') AS STUDENT_STUDY_STATUS_NAME,
    ISNULL(COALESCE(NULLIF(sss.STUDENT_STUDY_STATUS_NAME_EN, N''), sss.STUDENT_STUDY_STATUS_NAME), N'') AS STUDENT_STUDY_STATUS_NAME_EN
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

/* ── 4. V_Academic_Performance ──────────────────────────────────────────── */
CREATE OR ALTER VIEW MOHECORE.V_Academic_Performance AS
SELECT
    sd.STUDENT_ID,
    sd.STUDENT_NAME,
    sd.STUDY_COUNTRY_IN                  AS study_country_in_id,
    sd.STUDENT_MAJOR_ID,
    ISNULL(sm.STUDENT_MAJOR_NAME, N'') AS STUDENT_MAJOR_NAME,
    ISNULL(COALESCE(NULLIF(sm.STUDENT_MAJOR_NAME_EN, N''), sm.STUDENT_MAJOR_NAME), N'') AS STUDENT_MAJOR_NAME_EN,
    sd.STUDENT_CURRENT_LEVEL_ID,
    ISNULL(scl.STUDENT_CURRENT_LEVEL_NAME, N'') AS STUDENT_CURRENT_LEVEL_NAME,
    ISNULL(COALESCE(NULLIF(scl.STUDENT_CURRENT_LEVEL_NAME_EN, N''), scl.STUDENT_CURRENT_LEVEL_NAME), N'') AS STUDENT_CURRENT_LEVEL_NAME_EN,
    sd.STUDENT_STUDY_NO_OF_YEARS,
    sd.STUDENT_STUDY_STATUS_ID,
    ISNULL(sss.STUDENT_STUDY_STATUS_NAME, N'') AS STUDENT_STUDY_STATUS_NAME,
    ISNULL(COALESCE(NULLIF(sss.STUDENT_STUDY_STATUS_NAME_EN, N''), sss.STUDENT_STUDY_STATUS_NAME), N'') AS STUDENT_STUDY_STATUS_NAME_EN,
    sd.STUDENT_DEGREE_ID,
    ISNULL(sdeg.STUDENT_DEGREE_NAME, N'') AS STUDENT_DEGREE_NAME,
    ISNULL(COALESCE(NULLIF(sdeg.STUDENT_DEGREE_NAME_EN, N''), sdeg.STUDENT_DEGREE_NAME), N'') AS STUDENT_DEGREE_NAME_EN,
    sd.STUDENT_COUNTRY_ID,
    ISNULL(sc.STUDENT_COUNTRY_NAME, N'') AS STUDENT_COUNTRY_NAME,
    ISNULL(COALESCE(NULLIF(sc.STUDENT_COUNTRY_NAME_EN, N''), sc.STUDENT_COUNTRY_NAME), N'') AS STUDENT_COUNTRY_NAME_EN
FROM MOHECORE.STUDENT_DATA sd
LEFT JOIN MOHECORE.STUDENT_MAJOR         sm   ON sm.STUDENT_MAJOR_ID          = sd.STUDENT_MAJOR_ID
LEFT JOIN MOHECORE.STUDENT_CURRENT_LEVEL scl  ON scl.STUDENT_CURRENT_LEVEL_ID = sd.STUDENT_CURRENT_LEVEL_ID
LEFT JOIN MOHECORE.STUDENT_STUDY_STATUS  sss  ON sss.STUDENT_STUDY_STATUS_ID  = sd.STUDENT_STUDY_STATUS_ID
LEFT JOIN MOHECORE.STUDENT_DEGREE        sdeg ON sdeg.STUDENT_DEGREE_ID       = sd.STUDENT_DEGREE_ID
LEFT JOIN MOHECORE.STUDENT_COUNTRY       sc   ON sc.STUDENT_COUNTRY_ID        = sd.STUDENT_COUNTRY_ID
WHERE ISNULL(sd.IS_DELETED, 0) = 0;
GO

/*  Verification — every Arabic name column should now have an English sibling,
    and the row counts must be unchanged (941 / 941 / 196 for the three
    student-level views on dev).  */
EXEC sp_set_session_context @key = N'TenantAll', @value = N'1', @read_only = 0;

/*  STUDENT_NAME is the person's own name, recorded once and in one script. It is
    not a lookup and has no translation, so it is excluded rather than reported as
    a gap that could never be closed. Expect zero rows from this query.  */
SELECT v.name AS vw, c.name AS arabic_only_name_col
FROM sys.views v
JOIN sys.columns c ON c.object_id = v.object_id
WHERE v.name IN ('V_Student_List_Detailed', 'V_Student_Status_Report',
                 'V_Student_Demographics', 'V_Academic_Performance')
  AND c.name LIKE '%NAME%' AND c.name NOT LIKE '%NAME[_]EN'
  AND c.name <> 'STUDENT_NAME'
  AND NOT EXISTS (SELECT 1 FROM sys.columns c2
                  WHERE c2.object_id = v.object_id AND c2.name = c.name + '_EN')
ORDER BY v.name, c.name;

SELECT 'V_Student_List_Detailed' AS vw, COUNT(*) AS rows_ FROM MOHECORE.V_Student_List_Detailed
UNION ALL SELECT 'V_Student_Demographics', COUNT(*) FROM MOHECORE.V_Student_Demographics
UNION ALL SELECT 'V_Academic_Performance', COUNT(*) FROM MOHECORE.V_Academic_Performance
UNION ALL SELECT 'V_Student_Status_Report', COUNT(*) FROM MOHECORE.V_Student_Status_Report;
GO
