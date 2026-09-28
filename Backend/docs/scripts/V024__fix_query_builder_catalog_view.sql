/*
    V024 — make V_Query_Builder describe the schema it claims to describe.

    Problem
    -------
    Three of the view's six columns were literals:

        N''            AS column_description,
        CAST(1 AS INT) AS is_filterable,
        CAST(1 AS INT) AS is_sortable

    - column_description was empty on all 1,351 rows, even though 318 columns in
      MOHECORE carry an MS_Description extended property. The documentation was
      in the database and the view discarded it.
    - is_filterable and is_sortable were the constant 1, so "filterable columns"
      returned every column in the schema. The 24 ntext columns cannot appear in
      a WHERE or ORDER BY without an explicit cast, so the flags were not merely
      uninformative, they were wrong.

    It also filtered

        AND c.TABLE_NAME NOT LIKE 'V_%'

    which hid all 39 reporting views — the objects most worth querying — while
    including credential columns (StudentPortalAccount.PasswordHash,
    StudentPortalSession.Token, NOTIFICATION_SETTINGS.SMTP_PASSWORD,
    Users___old.Password) and superseded copies (STUDENT_DATA_BKUP, Users___old).

    Fix
    ---
    Read from sys.objects / sys.columns so views are included alongside tables,
    pick up MS_Description where it exists, derive the two flags from the column
    type, and drop credential columns and superseded objects.

    Credentials are excluded by column name rather than by table: this catalogue
    is what will decide which columns a composed query may select, so a column
    that must never be returned should not be listed as available.

    Passport numbers are deliberately kept. They are part of the model and the
    student documents already print them; restricting who may read them belongs
    with the query engine's permission rules, not with the data dictionary.

    Expected shape after applying:

        columns   objects   views included   non-filterable   described
        2,055     180       39               24 (ntext)       318

    (previously: 1,351 columns, 134 objects, 0 views, 0 non-filterable, 0 described)

    Scope
    -----
    The query builder page no longer reads this view — QueryBuilderRepository
    builds the same catalogue directly, so the page did not have to wait for this
    script. Applying it brings the view into line for any other consumer and
    stops the two definitions drifting apart.

    Apply in SSMS.
*/

CREATE OR ALTER VIEW MOHECORE.V_Query_Builder AS
SELECT
    o.name AS table_name,
    c.name AS column_name,
    TYPE_NAME(c.user_type_id) AS data_type,
    CAST(ISNULL(CAST(ep.value AS NVARCHAR(400)), N'') AS NVARCHAR(400)) AS column_description,
    CASE WHEN TYPE_NAME(c.user_type_id) IN
              ('text','ntext','image','xml','varbinary','binary','geography','geometry','hierarchyid','sql_variant')
         THEN 0 ELSE 1 END AS is_filterable,
    CASE WHEN TYPE_NAME(c.user_type_id) IN
              ('text','ntext','image','xml','varbinary','binary','geography','geometry','hierarchyid','sql_variant')
         THEN 0 ELSE 1 END AS is_sortable
FROM sys.objects o
INNER JOIN sys.columns c
        ON c.object_id = o.object_id
LEFT JOIN sys.extended_properties ep
       ON ep.major_id = c.object_id
      AND ep.minor_id = c.column_id
      AND ep.class = 1
      AND ep.name = 'MS_Description'
WHERE o.schema_id = SCHEMA_ID('MOHECORE')
  AND o.type IN ('U', 'V')
  AND o.is_ms_shipped = 0
  AND o.name NOT LIKE '%[_][_][_]old'
  AND o.name NOT LIKE '%[_]BKUP'
  AND o.name NOT LIKE '%[_]BAK'
  AND o.name <> 'sysdiagrams'
  AND c.name NOT LIKE '%PASSWORD%'
  AND c.name NOT LIKE '%TOKEN%'
  AND c.name NOT LIKE '%SECRET%'
  AND c.name NOT LIKE '%[_]SALT%'
  AND c.name NOT LIKE '%APIKEY%'
  AND c.name NOT LIKE '%API[_]KEY%';
