# MOHE backend

Spring Boot 3.5 / Java 21 API for the MOHE scholarship system. See the
[root README](../README.md) for the whole picture; this page is the backend-only
quick reference.

```powershell
.\mvnw spring-boot:run                                   # http://localhost:8082/mohe (defaults to the dev profile)
.\mvnw test                                             # unit tests only (no database)
.\mvnw test -DexcludedGroups=none                         # unit + integration (needs the MOHE database)
```

- No default profile in the packaged jar — `spring-boot:run` and `mvnw test` default to `dev`; `java -jar` needs `prod` (or `dev`) explicitly ([`docs/ENVIRONMENT.md`](docs/ENVIRONMENT.md)).
- Schema changes are numbered scripts in [`docs/scripts/`](docs/scripts/). **From V097 on, Flyway applies them at startup** and records each in `dbo.flyway_schema_history`; an existing database is baselined at V096 the first time the new build starts against it. V001–V096 are applied by hand, in order. Set `MOHE_FLYWAY_ENABLED=false` to go back to applying everything by hand. See [`docs/MIGRATION_RUNBOOK.md`](docs/MIGRATION_RUNBOOK.md). Never edit an applied script; add the next number.
- Reports are JasperReports templates in `src/main/resources/reports/`; `python scripts/audit_report_data.py` checks that every template field has a bean property behind it.
- Uploaded files live in `uploads/` (git-ignored), encrypted with `MOHE_FILE_ENCRYPTION_KEY`.
- Template generators, run only when a report's official layout changes: `scripts/apply_official_layout.py` (re-applies the header/footer bands and stamps each template), `scripts/make_table_report_template.py` (a new table report from the layout) and `scripts/make_query_result_template.py` (`query-result.jrxml` derived from `student-list.jrxml`).
