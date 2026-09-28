# MOHE — scholarship management system

The Ministry of Finance's system for students studying abroad on
government scholarships: the student record, finance and payments per
quarter, Wafar (savings) spending, university fees, travel tickets, book
allowances, loans, approvals, reports, and a student self-service portal.
Data is scoped per attaché country; ministry accounts see everything.

| Part | Stack | Where |
|---|---|---|
| Backend API | Spring Boot 3.5 · Java 21 · JPA/Hibernate · JasperReports · SQL Server | [`Backend/`](Backend/) |
| Frontend | Angular 21 · PrimeNG 21 · ng-zorro · ag-grid · Chart.js/ECharts | [`Frontend/`](Frontend/) |
| Database | SQL Server, schema `MOHECORE` (+ `UMSCORE`, `STUDENTCORE`, `FINCORE`, `AUDITLOG`), migrated by numbered SQL scripts | [`Backend/docs/scripts/`](Backend/docs/scripts/) |

## Prerequisites

- **JDK 21** (the build targets `release 21`; a newer JDK works)
- **Node.js ≥ 22** and npm (Angular CLI 21 also accepts 20.19+, but 20 is end-of-life)
- **SQL Server** with a populated `MOHE` database — the migration scripts *alter* an existing schema; they do not create one from nothing. Ask for a backup of the development database.
- **Python 3.12** for `Backend/scripts/audit_report_data.py` (report template audit)
- Chrome, for the frontend unit tests (`ChromeHeadlessWide`)

## Run it locally

**1. Database** — restore the `MOHE` backup on `localhost:1433`, then apply by hand any numbered script up to **V096** it does not yet have, in order. **From V097 on the backend applies them itself**: Flyway runs at startup, baselines an existing database at V096 the first time, and records each script in `dbo.flyway_schema_history`. So starting the backend is usually step 1 and a half. `Backend/docs/MIGRATION_RUNBOOK.md` lists every script and how to tell which have been applied.

**2. Backend** (port 8082, context path `/mohe`)

```powershell
cd Backend
.\mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

There is **no default profile**: `dev` supplies `localhost` SQL Server (`sa` / `P@ssw0rd`), a local JWT secret and a local file-encryption key. Override any of them with `MOHE_DATASOURCE_URL`, `MOHE_DATASOURCE_USERNAME`, `MOHE_DATASOURCE_PASSWORD`, `JWT_SECRET`, `MOHE_FILE_ENCRYPTION_KEY`. Swagger UI: <http://localhost:8082/mohe/swagger-ui.html>.

**3. Frontend** (port 4200; `proxy.conf.json` forwards `/mohe` to the backend)

```powershell
cd Frontend
npm install
npm start
```

Sign in at <http://localhost:4200> with a UMS account from the development database.

## Verify

```powershell
.\verify.ps1                 # everything: backend unit + integration tests, frontend validators, unit tests, build, report audit
.\verify.ps1 -SkipDatabase   # no SQL Server available
.\verify.ps1 -SkipFrontend   # backend only
```

Backend tests are in two groups: every `@SpringBootTest` class is `@Tag("integration")` and needs the database; plain `mvnw test` runs only the unit tests, `mvnw test -DexcludedGroups=none` runs both.

## Production

Set `SPRING_PROFILES_ACTIVE=prod`. The prod profile has **no fallbacks** — startup fails unless these are set: `MOHE_DATASOURCE_URL`, `MOHE_DATASOURCE_USERNAME`, `MOHE_DATASOURCE_PASSWORD`, `JWT_SECRET`, `MOHE_FILE_ENCRYPTION_KEY`, `MOHE_ACTUATOR_PASSWORD`, and `CORS_ALLOWED_ORIGINS` when the UI is served from another origin. Full list and meaning: [`Backend/docs/ENVIRONMENT.md`](Backend/docs/ENVIRONMENT.md).

The frontend is a static build (`npm run build:prod` writes it to `Frontend/dist/mof-is/browser/` — deploy that `browser` folder, not `dist/mof-is/` itself); serve it from the same origin as the API under `/mohe`, or set `splitOriginApi` in `src/environments/environment.prod.ts` (see `Frontend/env.template`). The backend also serves report PDFs under the alias `/mohe/api/pdf/**` (an ad-blocker workaround the frontend relies on).

## Where to read next

- [`Backend/docs/ENVIRONMENT.md`](Backend/docs/ENVIRONMENT.md) — every environment variable
- [`Backend/docs/MIGRATION_RUNBOOK.md`](Backend/docs/MIGRATION_RUNBOOK.md) — the database scripts, in order
- [`Backend/docs/JWT_AUTH.md`](Backend/docs/JWT_AUTH.md) — login flow and token claims
- [`Backend/docs/chart-of-accounts-revenue-load.md`](Backend/docs/chart-of-accounts-revenue-load.md) — how the ministry's revenue chart of accounts (مفردات الموارد) was read and loaded (V064), and what is still missing
- [`Backend/docs/db-inventory.md`](Backend/docs/db-inventory.md) — tables and views by schema
- [`Backend/docs/financial-data-form-v2.md`](Backend/docs/financial-data-form-v2.md) — the second financial data form, field by field
- [`Backend/docs/report-data-audit.md`](Backend/docs/report-data-audit.md) — what each report template reads, and what is still outstanding

## Repository notes

- There is no git remote, so `.github/workflows/ci.yml` has never run; `verify.ps1` is the working equivalent.
- `Backend/docs/scripts/adhoc/` holds one-off scripts that are **not** migrations — read its README before running any.
- Superseded planning and test reports are not kept in the tree; `MASTER-STATUS-AND-PLAN-2026-09-22.md` is the live work list and names the removed files, which git history keeps.
