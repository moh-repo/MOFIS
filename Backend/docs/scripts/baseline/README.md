# Baseline schema (generated, reference only)

`V000__baseline_schema.sql` is **generated** by `Backend/scripts/db_baseline.py`
from the live catalogue of the database it is pointed at (the dev `MOHE`
database unless told otherwise). It lists every user object — schemas,
sequences, tables with their columns, identity, computed columns, defaults,
primary/unique/check constraints, foreign keys, indexes, functions, views in
dependency order, procedures, triggers, the tenant security policy (always
written with `STATE = OFF`) and the `MS_Description` extended properties the
Query Builder reads — in an order that would create them on an empty database.

It is **not applied anywhere** and is **not part of the migration run list**
in `MIGRATION_RUNBOOK.md`. It exists so that someone standing up a fresh
database has one file that says what `V001__…` and everything after it assume
is already there, and so that two environments can be diffed by regenerating
it against each and comparing the output.

Do not edit it by hand: regenerate it.

```
cd Backend
python scripts/db_baseline.py            # defaults to the dev profile's connection
python scripts/db_baseline.py --server host,1433 --user sa --password ...
```

The generator is SELECT-only — it reads `sys.*` and `sys.sql_modules` and
writes nothing to the database. Module definitions (functions, views,
procedures, triggers) are copied verbatim from `sys.sql_modules`; table,
constraint and index DDL is composed from the catalogue.

What it does not carry: server logins, database users and object permissions
(V025's `mohe_query_reader`), and any data — including the lookup and
reference rows that scripts such as V003/V004/V028/V050 seed. Those come from
the numbered scripts.

Because it is a snapshot, it reflects whatever had been applied when it was
generated (the header says the date and the statement count). After applying a
migration, regenerate it together with `docs/db-inventory.md`.
