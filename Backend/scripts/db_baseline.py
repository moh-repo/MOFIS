"""Generate docs/scripts/baseline/V000__baseline_schema.sql from the live MOHE catalogue.

The baseline is a *reference* entry point for a fresh database: every user
object as it exists on the database the script is pointed at, in an order
that would create them — schemas, sequences, tables (columns, identity,
computed columns, defaults, primary/unique/check constraints), foreign keys,
indexes, functions, views in dependency order, procedures, triggers, the
tenant security policy (STATE = OFF), and the MS_Description extended
properties the Query Builder catalogue (V024) reads.

It is not applied anywhere and is not part of the V001+ run list; V001 and
later assume a database that looks like it. Regenerate after a migration the
same way db-inventory.md is regenerated (MIGRATION_RUNBOOK.md, checklist).

Usage (from Backend/):
    python scripts/db_baseline.py [--server localhost,1433] [--user sa] [--password ...]

Same connection approach as db_inventory.py: sqlcmd on the PATH or at the SQL
Server client-SDK location; the query is SELECT-only and nothing is written to
the database. Definitions come from sys.sql_modules verbatim; DDL for tables,
constraints and indexes is composed from sys.* in T-SQL (SQL Server 2016
compatible — no STRING_AGG).
"""
from __future__ import annotations

import argparse
import datetime as dt
import os
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

ROW_MARK = "--<<MOHE_BASELINE_ROW>>"

# One row per DDL statement, ordered. Every row ends with a newline and the
# marker so the Python side can split sqlcmd's output without relying on its
# column formatting (definitions span lines; -y 0 prints them whole).
QUERY = r"""
SET NOCOUNT ON;
SET TRANSACTION ISOLATION LEVEL READ UNCOMMITTED;

DECLARE @nl NVARCHAR(2) = NCHAR(10);
DECLARE @mark NVARCHAR(50) = N'--<<MOHE_BASELINE_ROW>>';
DECLARE @dbcoll NVARCHAR(128) = CAST(DATABASEPROPERTYEX(DB_NAME(), 'Collation') AS NVARCHAR(128));

DECLARE @out TABLE (section INT NOT NULL, sub INT NOT NULL, ord NVARCHAR(400) NOT NULL, ddl NVARCHAR(MAX) NOT NULL);

/* 1. schemas */
INSERT INTO @out SELECT 1, 0, s.name, N'IF SCHEMA_ID(N''' + s.name + N''') IS NULL EXEC (N''CREATE SCHEMA ' + QUOTENAME(s.name) + N' AUTHORIZATION ' + QUOTENAME(dp.name) + N';'');'
  FROM sys.schemas s JOIN sys.database_principals dp ON dp.principal_id = s.principal_id
 WHERE s.schema_id BETWEEN 5 AND 16383;

/* 2. sequences */
INSERT INTO @out SELECT 2, 0, SCHEMA_NAME(sq.schema_id) + '.' + sq.name,
       N'CREATE SEQUENCE ' + QUOTENAME(SCHEMA_NAME(sq.schema_id)) + N'.' + QUOTENAME(sq.name) + N' AS ' + TYPE_NAME(sq.system_type_id)
     + N' START WITH ' + CAST(sq.start_value AS NVARCHAR(40)) + N' INCREMENT BY ' + CAST(sq.increment AS NVARCHAR(40))
     + CASE WHEN sq.is_cached = 0 THEN N' NO CACHE' ELSE N'' END + N';'
  FROM sys.sequences sq;

/* 3. tables */
INSERT INTO @out
SELECT 3, 0, SCHEMA_NAME(t.schema_id) + '.' + t.name,
       N'CREATE TABLE ' + QUOTENAME(SCHEMA_NAME(t.schema_id)) + N'.' + QUOTENAME(t.name) + N' (' + @nl
     + STUFF((SELECT N',' + @nl + N'    ' + QUOTENAME(c.name) + N' '
                   + CASE WHEN cc.definition IS NOT NULL
                          THEN N'AS ' + cc.definition + CASE WHEN cc.is_persisted = 1 THEN N' PERSISTED' ELSE N'' END
                          ELSE
                            CASE WHEN ty.name IN ('nvarchar', 'nchar') THEN ty.name + N'(' + CASE WHEN c.max_length = -1 THEN N'MAX' ELSE CAST(c.max_length / 2 AS NVARCHAR(10)) END + N')'
                                 WHEN ty.name IN ('varchar', 'char', 'varbinary', 'binary') THEN ty.name + N'(' + CASE WHEN c.max_length = -1 THEN N'MAX' ELSE CAST(c.max_length AS NVARCHAR(10)) END + N')'
                                 WHEN ty.name IN ('decimal', 'numeric') THEN ty.name + N'(' + CAST(c.precision AS NVARCHAR(5)) + N',' + CAST(c.scale AS NVARCHAR(5)) + N')'
                                 WHEN ty.name IN ('datetime2', 'time', 'datetimeoffset') THEN ty.name + N'(' + CAST(c.scale AS NVARCHAR(5)) + N')'
                                 ELSE ty.name END
                          + CASE WHEN c.collation_name IS NOT NULL AND c.collation_name <> @dbcoll THEN N' COLLATE ' + c.collation_name ELSE N'' END
                          + CASE WHEN ic.column_id IS NOT NULL THEN N' IDENTITY(' + CAST(ic.seed_value AS NVARCHAR(40)) + N',' + CAST(ic.increment_value AS NVARCHAR(40)) + N')' ELSE N'' END
                          + CASE WHEN c.is_nullable = 1 THEN N' NULL' ELSE N' NOT NULL' END
                          + CASE WHEN dc.definition IS NOT NULL THEN N' CONSTRAINT ' + QUOTENAME(dc.name) + N' DEFAULT ' + dc.definition ELSE N'' END
                     END
                FROM sys.columns c
                JOIN sys.types ty ON ty.user_type_id = c.user_type_id
                LEFT JOIN sys.identity_columns ic ON ic.object_id = c.object_id AND ic.column_id = c.column_id
                LEFT JOIN sys.computed_columns cc ON cc.object_id = c.object_id AND cc.column_id = c.column_id
                LEFT JOIN sys.default_constraints dc ON dc.parent_object_id = c.object_id AND dc.parent_column_id = c.column_id
               WHERE c.object_id = t.object_id
               ORDER BY c.column_id
                 FOR XML PATH(''), TYPE).value('.', 'nvarchar(max)'), 1, 2, N'')
     + @nl + N');'
  FROM sys.tables t WHERE t.is_ms_shipped = 0;

/* 4. primary keys and unique constraints */
INSERT INTO @out
SELECT 4, CASE WHEN kc.type = 'PK' THEN 0 ELSE 1 END, SCHEMA_NAME(t.schema_id) + '.' + t.name + '.' + kc.name,
       N'ALTER TABLE ' + QUOTENAME(SCHEMA_NAME(t.schema_id)) + N'.' + QUOTENAME(t.name) + N' ADD CONSTRAINT ' + QUOTENAME(kc.name)
     + CASE WHEN kc.type = 'PK' THEN N' PRIMARY KEY ' ELSE N' UNIQUE ' END
     + CASE WHEN i.type = 1 THEN N'CLUSTERED' ELSE N'NONCLUSTERED' END + N' ('
     + STUFF((SELECT N', ' + QUOTENAME(COL_NAME(ic.object_id, ic.column_id)) + CASE WHEN ic.is_descending_key = 1 THEN N' DESC' ELSE N'' END
                FROM sys.index_columns ic WHERE ic.object_id = i.object_id AND ic.index_id = i.index_id AND ic.is_included_column = 0
               ORDER BY ic.key_ordinal FOR XML PATH(''), TYPE).value('.', 'nvarchar(max)'), 1, 2, N'')
     + N');'
  FROM sys.key_constraints kc
  JOIN sys.tables t ON t.object_id = kc.parent_object_id
  JOIN sys.indexes i ON i.object_id = kc.parent_object_id AND i.index_id = kc.unique_index_id
 WHERE t.is_ms_shipped = 0;

/* 5. check constraints */
INSERT INTO @out
SELECT 5, 0, SCHEMA_NAME(t.schema_id) + '.' + t.name + '.' + ck.name,
       N'ALTER TABLE ' + QUOTENAME(SCHEMA_NAME(t.schema_id)) + N'.' + QUOTENAME(t.name)
     + CASE WHEN ck.is_disabled = 1 OR ck.is_not_trusted = 1 THEN N' WITH NOCHECK' ELSE N' WITH CHECK' END
     + N' ADD CONSTRAINT ' + QUOTENAME(ck.name) + N' CHECK ' + ck.definition + N';'
     + CASE WHEN ck.is_disabled = 1 THEN @nl + N'ALTER TABLE ' + QUOTENAME(SCHEMA_NAME(t.schema_id)) + N'.' + QUOTENAME(t.name) + N' NOCHECK CONSTRAINT ' + QUOTENAME(ck.name) + N';' ELSE N'' END
  FROM sys.check_constraints ck JOIN sys.tables t ON t.object_id = ck.parent_object_id
 WHERE t.is_ms_shipped = 0;

/* 6. foreign keys */
INSERT INTO @out
SELECT 6, 0, SCHEMA_NAME(t.schema_id) + '.' + t.name + '.' + fk.name,
       N'ALTER TABLE ' + QUOTENAME(SCHEMA_NAME(t.schema_id)) + N'.' + QUOTENAME(t.name)
     + CASE WHEN fk.is_disabled = 1 OR fk.is_not_trusted = 1 THEN N' WITH NOCHECK' ELSE N' WITH CHECK' END
     + N' ADD CONSTRAINT ' + QUOTENAME(fk.name) + N' FOREIGN KEY ('
     + STUFF((SELECT N', ' + QUOTENAME(COL_NAME(fkc.parent_object_id, fkc.parent_column_id)) FROM sys.foreign_key_columns fkc
               WHERE fkc.constraint_object_id = fk.object_id ORDER BY fkc.constraint_column_id FOR XML PATH(''), TYPE).value('.', 'nvarchar(max)'), 1, 2, N'')
     + N') REFERENCES ' + QUOTENAME(OBJECT_SCHEMA_NAME(fk.referenced_object_id)) + N'.' + QUOTENAME(OBJECT_NAME(fk.referenced_object_id)) + N' ('
     + STUFF((SELECT N', ' + QUOTENAME(COL_NAME(fkc.referenced_object_id, fkc.referenced_column_id)) FROM sys.foreign_key_columns fkc
               WHERE fkc.constraint_object_id = fk.object_id ORDER BY fkc.constraint_column_id FOR XML PATH(''), TYPE).value('.', 'nvarchar(max)'), 1, 2, N'')
     + N')'
     + CASE fk.delete_referential_action WHEN 1 THEN N' ON DELETE CASCADE' WHEN 2 THEN N' ON DELETE SET NULL' WHEN 3 THEN N' ON DELETE SET DEFAULT' ELSE N'' END
     + CASE fk.update_referential_action WHEN 1 THEN N' ON UPDATE CASCADE' WHEN 2 THEN N' ON UPDATE SET NULL' WHEN 3 THEN N' ON UPDATE SET DEFAULT' ELSE N'' END
     + N';'
     + CASE WHEN fk.is_disabled = 1 THEN @nl + N'ALTER TABLE ' + QUOTENAME(SCHEMA_NAME(t.schema_id)) + N'.' + QUOTENAME(t.name) + N' NOCHECK CONSTRAINT ' + QUOTENAME(fk.name) + N';' ELSE N'' END
  FROM sys.foreign_keys fk JOIN sys.tables t ON t.object_id = fk.parent_object_id
 WHERE t.is_ms_shipped = 0;

/* 7. indexes that are not constraints */
INSERT INTO @out
SELECT 7, CASE WHEN i.type = 1 THEN 0 ELSE 1 END, SCHEMA_NAME(t.schema_id) + '.' + t.name + '.' + i.name,
       N'CREATE ' + CASE WHEN i.is_unique = 1 THEN N'UNIQUE ' ELSE N'' END
     + CASE WHEN i.type = 1 THEN N'CLUSTERED' ELSE N'NONCLUSTERED' END + N' INDEX ' + QUOTENAME(i.name)
     + N' ON ' + QUOTENAME(SCHEMA_NAME(t.schema_id)) + N'.' + QUOTENAME(t.name) + N' ('
     + STUFF((SELECT N', ' + QUOTENAME(COL_NAME(ic.object_id, ic.column_id)) + CASE WHEN ic.is_descending_key = 1 THEN N' DESC' ELSE N'' END
                FROM sys.index_columns ic WHERE ic.object_id = i.object_id AND ic.index_id = i.index_id AND ic.is_included_column = 0
               ORDER BY ic.key_ordinal FOR XML PATH(''), TYPE).value('.', 'nvarchar(max)'), 1, 2, N'')
     + N')'
     + ISNULL(N' INCLUDE (' + STUFF((SELECT N', ' + QUOTENAME(COL_NAME(ic.object_id, ic.column_id))
                FROM sys.index_columns ic WHERE ic.object_id = i.object_id AND ic.index_id = i.index_id AND ic.is_included_column = 1
               ORDER BY ic.column_id FOR XML PATH(''), TYPE).value('.', 'nvarchar(max)'), 1, 2, N'') + N')', N'')
     + ISNULL(N' WHERE ' + i.filter_definition, N'')
     + N';'
  FROM sys.indexes i JOIN sys.tables t ON t.object_id = i.object_id
 WHERE t.is_ms_shipped = 0 AND i.type IN (1, 2) AND i.is_primary_key = 0 AND i.is_unique_constraint = 0 AND i.is_hypothetical = 0;

/* 8. functions, 9. views (dependency order), 10. procedures, 11. triggers — verbatim from sys.sql_modules.
   Every module on this database was created with ANSI_NULLS and QUOTED_IDENTIFIER ON;
   the generated file sets both once at the top. */
INSERT INTO @out
SELECT 8, 0, SCHEMA_NAME(o.schema_id) + '.' + o.name, m.definition
  FROM sys.objects o JOIN sys.sql_modules m ON m.object_id = o.object_id
 WHERE o.type IN ('FN', 'IF', 'TF') AND o.is_ms_shipped = 0;

;WITH v AS (
    SELECT o.object_id, 0 AS lvl FROM sys.views o
     WHERE o.is_ms_shipped = 0
       AND NOT EXISTS (SELECT 1 FROM sys.sql_expression_dependencies d JOIN sys.views r ON r.object_id = d.referenced_id
                        WHERE d.referencing_id = o.object_id AND d.referenced_id <> o.object_id)
    UNION ALL
    SELECT o.object_id, v.lvl + 1
      FROM sys.views o
      JOIN sys.sql_expression_dependencies d ON d.referencing_id = o.object_id
      JOIN v ON v.object_id = d.referenced_id
     WHERE o.is_ms_shipped = 0 AND v.lvl < 20
)
INSERT INTO @out
SELECT 9, x.lvl, SCHEMA_NAME(o.schema_id) + '.' + o.name, m.definition
  FROM (SELECT object_id, MAX(lvl) AS lvl FROM v GROUP BY object_id) x
  JOIN sys.objects o ON o.object_id = x.object_id
  JOIN sys.sql_modules m ON m.object_id = o.object_id;

INSERT INTO @out
SELECT 10, 0, SCHEMA_NAME(o.schema_id) + '.' + o.name, m.definition
  FROM sys.objects o JOIN sys.sql_modules m ON m.object_id = o.object_id
 WHERE o.type = 'P' AND o.is_ms_shipped = 0;

INSERT INTO @out
SELECT 11, 0, OBJECT_SCHEMA_NAME(tr.parent_id) + '.' + OBJECT_NAME(tr.parent_id) + '.' + tr.name,
       m.definition + CASE WHEN tr.is_disabled = 1 THEN @nl + N'GO' + @nl + N'DISABLE TRIGGER ' + QUOTENAME(OBJECT_SCHEMA_NAME(tr.object_id)) + N'.' + QUOTENAME(tr.name)
                                                       + N' ON ' + QUOTENAME(OBJECT_SCHEMA_NAME(tr.parent_id)) + N'.' + QUOTENAME(OBJECT_NAME(tr.parent_id)) + N';' ELSE N'' END
  FROM sys.triggers tr JOIN sys.sql_modules m ON m.object_id = tr.object_id
 WHERE tr.parent_class = 1 AND tr.is_ms_shipped = 0;

/* 12. security policies, always STATE = OFF in the baseline (V062 turns it on) */
INSERT INTO @out
SELECT 12, 0, SCHEMA_NAME(p.schema_id) + '.' + p.name,
       N'CREATE SECURITY POLICY ' + QUOTENAME(SCHEMA_NAME(p.schema_id)) + N'.' + QUOTENAME(p.name) + @nl
     + STUFF((SELECT N',' + @nl + N'    ADD ' + sp.predicate_type_desc + N' PREDICATE '
                   -- the catalogue wraps the call in one pair of parentheses the grammar does not take
                   + CASE WHEN LEFT(sp.predicate_definition, 1) = N'(' AND RIGHT(sp.predicate_definition, 1) = N')'
                          THEN SUBSTRING(sp.predicate_definition, 2, LEN(sp.predicate_definition) - 2)
                          ELSE sp.predicate_definition END
                   + N' ON ' + QUOTENAME(OBJECT_SCHEMA_NAME(sp.target_object_id)) + N'.' + QUOTENAME(OBJECT_NAME(sp.target_object_id))
                   + CASE WHEN sp.predicate_type = 1 THEN N' ' + sp.operation_desc ELSE N'' END
                FROM sys.security_predicates sp WHERE sp.object_id = p.object_id
               ORDER BY OBJECT_SCHEMA_NAME(sp.target_object_id), OBJECT_NAME(sp.target_object_id), sp.predicate_type, sp.operation
                 FOR XML PATH(''), TYPE).value('.', 'nvarchar(max)'), 1, 2, N'')
     + @nl + N'WITH (STATE = OFF' + CASE WHEN p.is_schema_bound = 1 THEN N', SCHEMABINDING = ON' ELSE N', SCHEMABINDING = OFF' END + N');'
     + @nl + N'-- was STATE = ' + CASE WHEN p.is_enabled = 1 THEN N'ON' ELSE N'OFF' END + N' on the source database'
  FROM sys.security_policies p;

/* 13. extended properties on tables, views and their columns */
INSERT INTO @out
SELECT 13, CASE WHEN ep.minor_id = 0 THEN 0 ELSE 1 END, SCHEMA_NAME(o.schema_id) + '.' + o.name + '.' + CAST(ep.minor_id AS NVARCHAR(10)) + '.' + ep.name,
       N'EXEC sys.sp_addextendedproperty @name = N''' + REPLACE(ep.name, N'''', N'''''') + N''', @value = N''' + REPLACE(CAST(ep.value AS NVARCHAR(MAX)), N'''', N'''''') + N''',' + @nl
     + N'    @level0type = N''SCHEMA'', @level0name = N''' + SCHEMA_NAME(o.schema_id) + N''','
     + N' @level1type = N''' + CASE o.type WHEN 'V' THEN N'VIEW' ELSE N'TABLE' END + N''', @level1name = N''' + o.name + N''''
     + CASE WHEN ep.minor_id = 0 THEN N'' ELSE N',' + @nl + N'    @level2type = N''COLUMN'', @level2name = N''' + COL_NAME(ep.major_id, ep.minor_id) + N'''' END
     + N';'
  FROM sys.extended_properties ep JOIN sys.objects o ON o.object_id = ep.major_id
 WHERE ep.class = 1 AND o.type IN ('U', 'V') AND o.is_ms_shipped = 0;

SELECT ddl + @nl + @mark AS ddl FROM @out ORDER BY section, sub, ord;
"""

SECTION_TITLES = {
    1: "schemas", 2: "sequences", 3: "tables", 4: "primary keys and unique constraints",
    5: "check constraints", 6: "foreign keys", 7: "indexes", 8: "functions",
    9: "views (dependency order)", 10: "procedures", 11: "triggers",
    12: "security policies (STATE = OFF)", 13: "extended properties",
}

SQLCMD_CANDIDATES = [
    "sqlcmd",
    r"D:\Program Files\Microsoft SQL Server\Client SDK\ODBC\130\Tools\Binn\SQLCMD.EXE",
    r"C:\Program Files\Microsoft SQL Server\Client SDK\ODBC\170\Tools\Binn\SQLCMD.EXE",
    r"C:\Program Files\Microsoft SQL Server\Client SDK\ODBC\130\Tools\Binn\SQLCMD.EXE",
]


def find_sqlcmd() -> str:
    for c in SQLCMD_CANDIDATES:
        if shutil.which(c) or os.path.isfile(c):
            return c
    sys.exit("sqlcmd not found; install the SQL Server command-line tools")


def run_query(args) -> list[str]:
    """Run QUERY through sqlcmd and return one string per DDL statement."""
    with tempfile.NamedTemporaryFile("w", suffix=".sql", delete=False, encoding="utf-8") as f:
        f.write(QUERY)
        query_file = f.name
    try:
        out = subprocess.run(
            # -I: QUOTED_IDENTIFIER ON, which the XML .value() calls in QUERY need.
            [find_sqlcmd(), "-S", args.server, "-d", args.database, "-U", args.user, "-P", args.password,
             "-I", "-y", "0", "-w", "65535", "-f", "65001", "-i", query_file],
            capture_output=True, text=True, encoding="utf-8", errors="replace", check=False)
    finally:
        os.unlink(query_file)
    if out.returncode != 0 or out.stdout.lstrip().startswith("Msg "):
        sys.exit(out.stderr or out.stdout)
    text = out.stdout.replace("\r\n", "\n")
    pieces = text.split(ROW_MARK)
    stmts = []
    for i, piece in enumerate(pieces):
        if i == len(pieces) - 1:
            break  # whatever follows the last marker is sqlcmd's own trailer
        if i == 0:
            # sqlcmd may print the column header ("ddl" and a dashes line) first.
            lines = piece.split("\n")
            while lines and (lines[0].strip() == "" or lines[0].strip() == "ddl" or set(lines[0].strip()) == {"-"}):
                lines.pop(0)
            piece = "\n".join(lines)
        stmts.append(piece.strip("\n"))
    return stmts


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--server", default=os.environ.get("MOHE_DB_SERVER", "localhost,1433"))
    ap.add_argument("--database", default="MOHE")
    ap.add_argument("--user", default=os.environ.get("MOHE_DATASOURCE_USERNAME", "sa"))
    ap.add_argument("--password", default=os.environ.get("MOHE_DATASOURCE_PASSWORD", "P@ssw0rd"))
    args = ap.parse_args()

    stmts = run_query(args)
    if not stmts:
        sys.exit("the catalogue query returned nothing — wrong database?")

    target = Path(__file__).resolve().parents[1] / "docs" / "scripts" / "baseline" / "V000__baseline_schema.sql"
    target.parent.mkdir(parents=True, exist_ok=True)

    header = (
        "/*\n"
        "    V000 — baseline schema (GENERATED — do not edit by hand)\n"
        "\n"
        f"    Generated {dt.date.today().isoformat()} by scripts/db_baseline.py from SQL Server database\n"
        f"    `{args.database}` on {args.server}: {len(stmts)} statements.\n"
        "\n"
        "    Reference only. This file is not applied anywhere and is not part of\n"
        "    the V001+ run list; V001 and later assume a database that looks like\n"
        "    this. See baseline/README.md. Security policies are emitted with\n"
        "    STATE = OFF (V062 turns the tenant policy on). Logins, database users\n"
        "    and permissions (V025's mohe_query_reader) are server-level and not here.\n"
        "*/\n"
        "SET ANSI_NULLS ON;\n"
        "SET QUOTED_IDENTIFIER ON;\n"
        "GO\n\n"
    )
    body = []
    current = None
    for stmt in stmts:
        section = _section_of(stmt, current)
        if section != current:
            current = section
            body.append(f"/* ═══ {SECTION_TITLES.get(section, '')} ═══ */\n")
        body.append(stmt + "\nGO\n\n")
    target.write_text(header + "".join(body), encoding="utf-8")
    print(f"{target}: {len(stmts)} statements")
    return 0


_SECTION_PREFIXES = [
    (1, "IF SCHEMA_ID("), (2, "CREATE SEQUENCE "), (3, "CREATE TABLE "),
    (7, "CREATE UNIQUE CLUSTERED INDEX "), (7, "CREATE UNIQUE NONCLUSTERED INDEX "),
    (7, "CREATE CLUSTERED INDEX "), (7, "CREATE NONCLUSTERED INDEX "),
    (12, "CREATE SECURITY POLICY "), (13, "EXEC sys.sp_addextendedproperty"),
]


def _section_of(stmt: str, current: int | None) -> int:
    """Best-effort section label from the statement text (for the banners only)."""
    head = stmt.lstrip()
    for section, prefix in _SECTION_PREFIXES:
        if head.startswith(prefix):
            return section
    if head.startswith("ALTER TABLE "):
        if " PRIMARY KEY " in head or " UNIQUE " in head.split("\n", 1)[0]:
            return 4
        if " CHECK " in head.split("\n", 1)[0] and " FOREIGN KEY " not in head:
            return 5
        return 6
    upper = head.upper()
    for kw, section in (("CREATE FUNCTION", 8), ("CREATE VIEW", 9), ("CREATE PROCEDURE", 10),
                        ("CREATE PROC ", 10), ("CREATE TRIGGER", 11)):
        # module definitions may start with a comment block; look at the first CREATE
        idx = upper.find("CREATE ")
        if idx >= 0 and " ".join(upper[idx:idx + 40].split()).startswith(kw):
            return section
    return current if current is not None else 0


if __name__ == "__main__":
    raise SystemExit(main())
