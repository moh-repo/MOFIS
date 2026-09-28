"""Regenerate docs/db-inventory.md from the live MOHE catalogue.

The inventory is a plain list of `SCHEMA OBJECT_TYPE NAME` lines, one per
table, view, function, procedure and trigger, so a reviewer can diff two
environments or check that a migration's objects exist. It went stale between
V038 and V057 because it was maintained by hand; this script is what "update
db-inventory.md after schema changes" (MIGRATION_RUNBOOK.md) now means.

Usage (from Backend/):
    python scripts/db_inventory.py [--server localhost,1433] [--user sa] [--password ...]

Credentials default to the dev profile's. Needs sqlcmd on the PATH or at the
SQL Server 2016 client-SDK location; nothing is written to the database.
"""
from __future__ import annotations

import argparse
import datetime as dt
import os
import shutil
import subprocess
import sys
from pathlib import Path

QUERY = (
    "SET NOCOUNT ON; "
    # COLLATE: sys.objects.name and SCHEMA_NAME() carry different collations on
    # this server, and concatenating them without one is a collation-conflict error.
    "SELECT SCHEMA_NAME(o.schema_id) COLLATE DATABASE_DEFAULT + ' ' + o.type_desc COLLATE DATABASE_DEFAULT "
    "+ ' ' + o.name COLLATE DATABASE_DEFAULT "
    "FROM sys.objects o "
    "WHERE o.type IN ('U','V','FN','IF','TF','P','TR') AND o.is_ms_shipped = 0 "
    "ORDER BY SCHEMA_NAME(o.schema_id) COLLATE DATABASE_DEFAULT, o.type_desc, o.name COLLATE DATABASE_DEFAULT;"
)

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


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--server", default=os.environ.get("MOHE_DB_SERVER", "localhost,1433"))
    ap.add_argument("--database", default="MOHE")
    ap.add_argument("--user", default=os.environ.get("MOHE_DATASOURCE_USERNAME", "sa"))
    ap.add_argument("--password", default=os.environ.get("MOHE_DATASOURCE_PASSWORD", "P@ssw0rd"))
    args = ap.parse_args()

    out = subprocess.run(
        [find_sqlcmd(), "-S", args.server, "-d", args.database, "-U", args.user, "-P", args.password,
         "-h", "-1", "-W", "-Q", QUERY],
        capture_output=True, text=True, encoding="utf-8", errors="replace", check=False)
    if out.returncode != 0 or out.stdout.lstrip().startswith("Msg "):
        sys.exit(out.stderr or out.stdout)
    lines = [ln.strip() for ln in out.stdout.splitlines() if ln.strip()]
    schemas = {ln.split(" ", 1)[0] for ln in lines}

    target = Path(__file__).resolve().parents[1] / "docs" / "db-inventory.md"
    header = (
        "# MOHE Database Object Inventory\n\n"
        f"Live snapshot from SQL Server database `{args.database}` "
        f"({len(schemas)} schemas, {len(lines)} objects), taken {dt.date.today().isoformat()} "
        "with `python scripts/db_inventory.py`.\n"
        "Scripts for schema fixes live in `Backend/docs/scripts/`; re-run the script after applying one.\n\n"
    )
    target.write_text(header + "```\n" + "\n".join(lines) + "\n```\n", encoding="utf-8")
    print(f"{target}: {len(lines)} objects in {len(schemas)} schemas")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
