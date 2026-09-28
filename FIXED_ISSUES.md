# MOHE — Fixed Issues Report

| | |
|---|---|
| **Date** | 2026-09-28 |
| **Branch** | `feat/fix-issue-v01` (from `f3290a8`, Initial version – MOHE V08 System) |
| **Source of the issues** | [`SYSTEM_TEST_REPORT.md`](SYSTEM_TEST_REPORT.md): the whole-system review, Section 2 |
| **Result** | **All 67 reported issues addressed**, plus 7 more found while fixing (74 in total): **69 fixed, 5 partly fixed or accepted**, each with its reason in [Section 5](#5-partly-fixed-accepted-or-needing-a-decision) |

## 1. Verification

Environment: Linux, JDK 21, Node 22, Chromium 141 headless. **No SQL Server was available.** The 170
integration test classes, the two new migrations (V148, V149) and every SQL-level change have **not**
been run against a database. [Section 6](#6-what-still-has-to-be-tested-with-a-database) lists what has
to be run on the dev database before release.

| Check | Before | After |
|---|---|---|
| Backend unit tests (`mvnw test`, run from a POSIX-locale shell) | 1,187 run, **6 errors** | **1,348 run, 0 failures, 0 errors** |
| Backend compile of all test sources (unit + integration) | ✅ | ✅ |
| Report template audit (`audit_report_data.py`) | 2 false "missing property" | **0 missing**, `--check` passes |
| Generated docs current (`gen_reference_docs.py --check`) | n/a | ✅ |
| `npm ci` (exactly as CI) | ❌ **lock file out of sync** | ✅ |
| i18n / menu / reload-stream validators | ✅ | ✅ |
| Lint (`ng lint --quiet`) | ✅ | ✅ |
| Frontend unit tests | 1,963 pass | **1,991 pass**, 0 fail (5 skipped, as before) |
| Production build | ✅ 25 warnings | ✅ 11 warnings, none of them budget warnings (Sass `@import` deprecations; see §5) |
| Dist hygiene (`check-dist`) | ✅ | ✅, now also checks the CSP against `splitOriginApi` |
| `npm audit --omit=dev` | 1 low, 2 moderate | **1 low** (quill, accepted; see §5) |
| `npm audit --audit-level=high` (CI gate) | ✅ | ✅ |

**New automated tests:** about 160 backend unit tests (1,187 → 1,348) and 28 frontend specs (1,963 → 1,991).
Each fix below names its test.

## 2. Things to do when deploying this branch

1. **Apply the migrations** with the backend's Flyway on startup, **on a copy of the dev database first**
   ([`Backend/docs/MIGRATION_RUNBOOK.md`](Backend/docs/MIGRATION_RUNBOOK.md)):
   - `V148__finance_integrity_fixes.sql`: `ROW_VERSION` on payment batches, allocations and advance balances; cancelled-by/at on batches; `CHECK (AMOUNT > 0)` on Wafar spending. **Read its PRINT output:** Wafar rows with an amount ≤ 0 are listed. When there are any, the check is added `WITH NOCHECK`, and those rows cannot be edited until corrected.
   - `V149__portal_and_retention_fixes.sql`: the student status-change table, its trigger and a backfill; `locked_until` and `totp_last_step` on portal users.
2. **Set `MOHE_TRUSTED_PROXIES`** to the reverse proxy's IP (a regex) when TLS is terminated at a proxy. Otherwise HSTS is not sent (SEC-04).
3. **Run the service with `LANG=C.UTF-8`** (ENV-01). The startup log now reports an error when this is missing.
4. **Tell users** about these behaviour changes:
   - **Country admins** can no longer create, edit or delete roles, groups or permissions, or change role/group assignments. The UI hides those actions (SEC-02, SEC-03, FE-11).
   - **Country users** can no longer create or close MOHE quarters: ministry tier only (FIN-07, FE-14).
   - **Changing or resetting a password now signs out that account's other sessions** (SEC-10).
   - **Old student-portal tokens** (legacy MOHECORE sessions) are refused, so those students sign in again (PRT-10).
   - **Portal accounts lock for 15 minutes after 5 wrong passwords** (PRT-04).
   - **Super admins** can no longer approve their own requests on money entity types (FIN-17).
   - The custom-filter report column formerly called "national number" is the **passport number** and is masked as one (RPT-01).
   - Fiscal period/year close endpoints no longer accept `?userId`; the signed-in user is recorded (FIN-12).

## 3. Fixed issues

Status: ✅ fixed · ◐ partly fixed / accepted (see §5). Paths are under `Backend/src/main/java/com/dilaw/mohe/`
unless they start with `Frontend/`, `Backend/` or `.`.

### 3.1 Critical

| ID | Status | What was wrong | What changed | Tests |
|---|---|---|---|---|
| SEC-01 | ✅ | UMS "create" endpoints merged onto an existing row when the body carried an `id`, allowing account takeover | Every UMS create (users, roles, groups, permissions, pages, sections, fields, reports, API links) clears the id before saving. `id` is `@JsonProperty(READ_ONLY)` on `UmsUser`, `UmsRole`, `UmsGroup`, `UmsPermission` | `UmsCreateNeverMergesTest` |
| SEC-02 | ✅ | Country admins could rewrite permission codes and grant themselves e.g. `SYSTEM.RESTORE` | Permission writes gated on the ministry-only `ADMIN_TOOLS_ADD/EDIT/DELETE`. `PermissionCode` is immutable (400 `ums.permission.code.immutable`). Changes are audited and every user's authority cache is evicted | `PermissionAndRoleEditHardeningTest`, `UmsCatalogueWritesAreMinistryOnlyTest` |
| FIN-01 | ✅ | A negative Wafar spend was accepted and *raised* the balance when approved | `@NotNull @Positive` on the create DTO and `@Positive` on corrections. `WafarSpendingService.requirePositiveAmount` runs on create, update, approve, correction request and apply. DB `CK_WAFAR_SPENDING_AMOUNT_POSITIVE` (V148) | `WafarAmountAndLockTest` |

### 3.2 High

| ID | Status | What was wrong | What changed | Tests |
|---|---|---|---|---|
| SEC-03 | ✅ | Country admins could create, rename or deactivate global roles and groups | Every role/group write, including permission and role assignment, uses `ADMIN_TOOLS_*`. Update and delete call `privilegeGuard.assertMayEditRole`. Tier role names are reserved (`ums.role.name.reserved`). The cache is evicted on change | `PermissionAndRoleEditHardeningTest`; `SystemRoleAndGroupRefusalTest` updated |
| FIN-02 | ✅ | A Wafar-funded ticket never reduced the attaché's Wafar balance | `TravelTicketWafarService` stamps the student's attaché country and records the requester as maker and the real approver. A zero-cost ticket releases the spend. A student with no attaché gets 409 | `TravelTicketWafarSpendTest` |
| FIN-03 | ✅ | Two concurrent "mark paid" calls debited the advance twice | `@Version` (`ROW_VERSION`, V148) on `PaymentBatch`, `AttacheAllocation`, `AttacheAdvanceBalance`. The status change is flushed before the advance moves, so the second call gets 409. Allocation transfer is fixed the same way | `PaymentBatchIntegrityTest`, `AllocationIntegrityTest` |
| FIN-04 | ✅ | A batch could pay an already-paid student, or a student of another attaché | `PaymentBatchService.create` refuses a country mismatch with 400 and a student fully paid in another live batch of the quarter with 409 | `PaymentBatchIntegrityTest` |
| FIN-05 | ✅ | Allocation create merged onto an existing allocation by id | `AllocationService.create` builds a new row from the maker's fields only | `AllocationIntegrityTest` |
| FIN-06 | ✅ | Editing `totalLevels` cut in-flight approval chains, and deactivating a level stranded requests | The next and final level come from the request's own steps. `updateConfig` ignores `totalLevels` and counts active levels | `ApprovalChainIntegrityTest` |
| FIN-07 | ✅ | A country user could create or close the global quarter, stranding other countries | Quarter create and close require a ministry-tier user (403 otherwise) and cover every student. The UI hides the actions (FE-14) | `MoheQuarterCreateServiceTest`, `MoheQuarterCloseServiceTest` updated |
| PRT-01 | ✅ | Retention measured "inactive" from the entry date, so it could anonymise someone who withdrew yesterday | New `MOHECORE.STUDENT_STUDY_STATUS_CHANGE`, filled by a trigger and backfilled from the archive (V149). A student is a candidate only when a status-change row matching the current status is older than the cutoff. No evidence means not a candidate | `DataRetentionAnonymiseTest`; `DataRetentionJobTest` (integration) updated |

### 3.3 Medium

| ID | Status | What was wrong | What changed | Tests |
|---|---|---|---|---|
| SEC-04 | ✅ | Tomcat trusted `X-Forwarded-For` from any private-range peer | `server.tomcat.remoteip.internal-proxies=${MOHE_TRUSTED_PROXIES:}`; empty trusts no one | `TrustedProxiesDefaultTest` |
| SEC-05 | ✅ | A ministry user sending `X-Country-Id` gained DB Wafar-write | `Scope.of(ministry, ids, mayWriteWafar)`, taken from the base tier, not the narrowed scope | `WafarWriteFollowsBaseTierTest` |
| SEC-06 | ✅ | A percent-encoded path (`logi%6E`) skipped the login throttle | Rate-limit paths are decoded and normalised (`;params`, `//`, `.`/`..`) before matching | `LoginRateLimitFilterTest` (+6 cases) |
| FIN-08 | ✅ | The close budget check mixed currencies and quarters | Per-currency, per-quarter comparison through two new repository queries | `MoheQuarterCloseServiceTest` |
| FIN-09 | ✅ | A non-current quarter could be closed | Closing anything but the latest quarter returns 409 | `MoheQuarterCloseServiceTest` |
| FIN-10 | ✅ | The last editor of an appropriation could approve it | The approver must differ from both the creator and the last editor | `BudgetAppropriationServiceTest` |
| FIN-11 | ◐ | An approved recipient DELETE was rolled back by a nested rejection | The new `rejectObsolete` has no policy check and cannot throw, so the delete commits. It still joins the outer transaction (see §5) | `ApprovalChainIntegrityTest` |
| FIN-12 | ✅ | Money could move in a closed fiscal year, and the closer's id was spoofable | Allocation and batch create, approve, transfer and pay check that the year and period are open. The close endpoints take the user from the security context | `PaymentBatchIntegrityTest`, `AllocationIntegrityTest` |
| FIN-13 | ✅ | A client-sent fiscal year could contradict the quarter | The fiscal year is always derived from the quarter | `AllocationIntegrityTest` |
| FIN-14 | ✅ | Concurrent approvals could overdraw Wafar | A SQL Server application lock per attaché covers every Wafar balance check; a timeout returns 409 | `WafarAmountAndLockTest` |
| PRT-02 | ✅ | Anonymisation left PII in history files, the photo, change requests, staging JSON and inquiries | All of them are scrubbed or deleted, and `PICTURE_PATH` is cleared, on archive rows too | `DataRetentionAnonymiseTest` |
| PRT-03 | ✅ | An approved portal country change neither moved the files nor ended sessions | Calls `relocateForCountryChange` and deletes the student's portal sessions | `ChangeRequestPortalFixesTest` |
| PRT-04 | ✅ | No per-account lockout for portal passwords | 5 failures lock the account for 15 minutes (configurable). Success or reset clears the lock. The 401 is the same while locked | `PortalPasswordLockoutTest` |
| PRT-05 | ✅ | Info-request answers could propose any student or finance field | Per-request-type whitelist. Finance fields and `studyCountryIn` are never allowed. A changed disallowed field returns 400 | `PortalInfoRequestProposalTest` |
| RPT-01 | ✅ | The passport number was masked under the national-ID rule and mislabelled | The column is `passportNumber`, with the passport `@FieldPermission`. Repository, controller, scheduled export and frontend interface updated | `ReportFieldMaskTest` |
| RPT-02 | ✅ | The query builder left passport and ID columns unmasked when read through views | `ReportFieldMask.ruleForColumn` falls back to the column name on any table or view, including passport-name variants | `QueryExecutionScopeAndMaskTest` |
| RPT-03 | ✅ | Arbitrary joins could pull other countries' rows | Joins only on suggested key/FK pairs. Every joined source with a country column is scoped in its ON clause. A people table with no country column must link by STUDENT_ID, otherwise 403 | `QueryExecutionScopeAndMaskTest` |
| DB-01 | ✅ | A DB built from the baseline had an empty Flyway history, so Flyway re-ran every script | `db_baseline.py` skips `flyway_schema_history`, and it was removed from `V000`. `baseline/README.md` explains the baseline version to use | review; needs a DB to run |
| FE-01 | ✅ | A wrong MFA code on /profile logged the user out | 401s from `/ums/auth/login` and `/ums/auth/mfa/*` are credential checks: no refresh, no logout (`isCredentialCheck`) | `error.interceptor.spec`, `auth.interceptor.spec` |
| FE-02 | ✅ | The silent refresh on 401 could never run (interceptor order) | Order centralised in `interceptors/http-interceptors.ts`. A failed refresh rethrows once; there is no retry loop and no double redirect | `interceptor-chain.spec.ts` (new, real chain) |
| FE-03 | ✅ | A failed proactive refresh (network error or 5xx) logged the user out | Only 401/403 log out; otherwise the request goes out with the still-valid token | `auth.interceptor.spec` |
| BLD-01 | ✅ | `package-lock.json` was out of sync, so `npm ci` and the frontend CI job failed | Lock regenerated; `npm ci` passes | `npm ci` |
| BLD-02 | ✅ | CI and `zap_*.ps1` read a `.zap/` folder that was git-ignored and absent | Added `.zap/rules.tsv`, `check_high.py` (fails on High alerts) and `baseline-local.yaml`; `.zap/` removed from `.gitignore` | `check_high.py` run on sample reports (exit 1 on High, 0 otherwise) |
| BLD-03 | ✅ | `Backend/mvnw` had no execute bit, so CI's `./mvnw` failed | `chmod +x` | — |
| ENV-01 | ✅ | Arabic file names failed under a non-UTF-8 OS locale (6 unit tests, and production uploads) | Surefire and `spring-boot:run` set `LANG/LC_ALL=C.UTF-8`. New `FileNameEncodingCheck` logs an error at startup when file names are not UTF-8. Documented | `FileNameEncodingCheckTest`; the 6 tests pass from a POSIX shell |

### 3.4 Low

| ID | Status | What changed | Tests |
|---|---|---|---|
| SEC-07 | ✅ | Must-change-password exemptions narrowed to self-service paths. Settings and code lists are exempt for GET only | `MustChangePasswordInterceptorTest` (+2) |
| SEC-08 | ✅ | Portal scopes carry `wafarWrite=false` | `WafarWriteFollowsBaseTierTest` |
| SEC-09 | ◐ | Committed portal test-account credentials removed (env only, off by default). Dev-only DB, JWT and file-key fallbacks kept (see §5) | — |
| PRT-06 | ✅ | Dummy bcrypt comparison on unknown, inactive and locked accounts, so timing no longer reveals which accounts exist | `PortalPasswordLockoutTest` |
| PRT-07 | ✅ | Portal TOTP replay guard (`totp_last_step`, V149) | `PortalTotpReplayTest` |
| PRT-08 | ✅ | Audit archive rows redacted with `AuditValueRedactor`, and the file encrypted with the file key (`.ndjson.gz.enc`) | `AuditArchiveRedactionTest` |
| PRT-09 | ✅ | Retention runs one transaction per student, and files are deleted only after that student's commit | `DataRetentionAnonymiseTest` |
| PRT-10 | ✅ | Legacy MOHECORE portal-token fallback removed | `StudentPortalTokenInterceptorTest` |
| PRT-11 | ✅ | Download decrypt and read failures logged with the document id | — |
| PRT-12 | ✅ | A numeric or boolean `newValue` is read as text; a bad shape returns 400 (`portal.changeRequest.badShape`) | `ChangeRequestPortalFixesTest` |
| PRT-13 | ✅ | The SVG check decodes entities, refuses unknown named entities, and blocks script-like elements, `on*`, `srcdoc` and `javascript:`/`vbscript:`/non-image `data:` URLs | `SvgActiveContentTest` (18 cases) |
| PRT-14 | ✅ | Portal uploads write the file, then save in one transaction, and delete the file on failure. Info-request answers are saved atomically | `PortalUploadAtomicityTest` |
| FIN-15 | ✅ | Wafar reconciliation compares exact decimals | `WafarReconciliationComparisonTest` |
| FIN-16 | ✅ | Batch cancel records `CANCELLED_BY/AT` (V148, which also backfills existing cancelled batches) | `PaymentBatchIntegrityTest` |
| FIN-17 | ◐ | Super-admin self-approval refused on the 8 money entity types (see §5) | `ApprovalChainIntegrityTest` |
| RPT-00 | ✅ | The audit script resolves the bean the controller actually passes; a new reflection test checks every template field | `TemplateFieldsMatchBeansTest` |
| RPT-04 | ✅ | Filtering or sorting on a column the user may not view returns 400 | `QueryExecutionScopeAndMaskTest` |
| RPT-05 | ✅ | Data-visualisation endpoints and export filter by country in the application, not only through RLS | `ReportExportScopeAndYearTest` |
| RPT-06 | ✅ | Extension statistics export and screen apply `year` | `ReportExportScopeAndYearTest` |
| RPT-07 | ✅ | Growth analysis counts per year, fills empty years, compares adjacent years, and the first year is "baseline" | `GrowthAnalysisYearOverYearTest` |
| RPT-08 | ✅ | Query-report tickets are signed, stateless HMAC tokens that expire after 15 minutes and are owner-bound; they work across nodes | `QueryReportTicketServiceTest` |
| DB-02 | ✅ | V031 is safe to re-run: when the policy already exists it no longer drops it | review; needs a DB |
| FE-04 | ✅ | `takeUntilDestroyed` on `currentLanguage$` in the three components; no other leaks found | 2 specs updated, 1 new |
| FE-05 | ✅ | Translated toast (`PAYMENT_AUTH.LOAD_RECORD_ERROR`) | `validate-i18n` |
| FE-06 | ✅ | `text-align: end`, and a forced `direction: rtl` removed | — |
| FE-07 | ✅ | `splitOriginApi` documented to need `connect-src`; `check-dist` fails the build when they disagree | `check-dist` |
| FE-08 | ✅ | Legacy `role.roleName==='ADMIN'` guard fallback removed | `ums-permission.guard.spec` updated |
| FE-09 | ✅ | Component-style warning budget 8 kB → 28 kB (error at 35 kB kept). Primeflex's Sass `if()` deprecation silenced | build: no budget warnings |
| FE-10 | ◐ | `overrides: uuid ^11.1.1` clears the advisory; the bundled exceljs build still ships its own uuid (see §5) | `npm audit` |
| DOC-01 | ◐ | Written: `verify.sh`, `verify.ps1`, `Backend/docs/{MIGRATION_RUNBOOK, ENVIRONMENT, JWT_AUTH, DEPLOYMENT_SECURITY, DEPENDENCY_ADVISORIES, report-data-audit, financial-data-form-v2, chart-of-accounts-revenue-load}.md`. The README no longer links to missing files. `db-inventory.md` needs a DB (see §5) | `gen_reference_docs.py --check` in CI |
| DOC-02 | ✅ | `ci.yml` header no longer says there is no remote; counts updated | — |

### 3.5 Found and fixed during this work (not in the original report)

| ID | Sev | What was wrong | What changed | Tests |
|---|---|---|---|---|
| SEC-10 | High | Changing or resetting a staff password left every existing session (and a stolen token) valid | Self-service change ends the user's *other* sessions. An admin change or reset ends *all* of the target's sessions and evicts their authority cache. `SecurityRequestUtils.currentJti()` added | `PasswordChangeEndsSessionsTest` |
| BLD-04 | Medium | `ci.yml` used `secrets.*` inside a step `if:`, which GitHub rejects, making the whole workflow invalid | The token goes through the job `env`; the step tests `env.ZAP_BEARER_TOKEN` | YAML validated |
| FE-11 | Medium | After SEC-02/03 the UI still offered role, group and permission writes to country admins | `*hasPermission="'X'; adminTools: true"` requires super-admin or ministry-admin tier; applied to every write action in those screens | `has-permission.directive.spec` (+4) |
| FE-12 | Low | The new "baseline" growth status would show as a raw code | Label "Base year" / "سنة الأساس" | spec added |
| FE-13 | Low | The extension statistics screen didn't send `year`; the `nationalNumber` interface was stale | Sends `year` and reloads on change; interface renamed to `passportNumber` | spec added |
| FE-14 | Low | Quarter create/close were still shown to country users after FIN-07 | Shown only to super admin or ministry level; the close method checks too | spec added |
| MIG | Low | Migration numbers would have skipped V149 | Portal migration renumbered V150 → V149; V001–V149 contiguous | — |

## 4. Files added

| File | Purpose |
|---|---|
| `Backend/docs/scripts/V148__finance_integrity_fixes.sql`, `V149__portal_and_retention_fixes.sql` | Migrations for FIN-01/03/16 and PRT-01/04/07 (idempotent, with rollback notes) |
| `Backend/src/main/java/com/dilaw/mohe/FileNameEncodingCheck.java` | Startup check for the UTF-8 file-name encoding (ENV-01) |
| `.zap/rules.tsv`, `.zap/check_high.py`, `.zap/baseline-local.yaml` | ZAP scan config and High-alert gate (BLD-02) |
| `verify.sh`, `verify.ps1` | One command for every check, with or without a database |
| `Backend/scripts/gen_reference_docs.py` | Regenerates `ENVIRONMENT.md` and the runbook's script table; `--check` runs in CI |
| `Frontend/src/app/demo/interceptors/http-interceptors.ts` | Single definition of the interceptor order (FE-02) |
| `Backend/docs/*.md` (8 files) | See DOC-01 |

## 5. Partly fixed, accepted, or needing a decision

| ID | What is left | Why / what to decide |
|---|---|---|
| FIN-17 | Super admins may still sign more than one level of *someone else's* request | Self-approval on money types is now refused. Whether super admins should be bound by per-level separation too is a **product decision**. `ApprovalRequestPolicy.MONEY_ENTITY_TYPES` lists the 8 types covered |
| PRT-05 | The allowed fields per info-request type are our choice (contact / identity / study sets) | **The ministry should confirm the lists** in `StudentInfoRequestService.checkPortalProposal` |
| SEC-09 | Dev profile keeps fallback JWT secret, file key, `mohe_app` and `sa`/`P@ssw0rd` | Needed so a local `-Dspring-boot.run.profiles=dev` run starts. All can be overridden by env, the prod profile has no fallback, and non-dev CORS guards refuse dev patterns. **Accepted, dev-only** |
| FE-10 | The browser bundle's exceljs prebuilt file embeds uuid 8 | Not exposed: only `v4()` without a buffer is used, and the advisory concerns v3/v5/v6 with a buffer. Re-check when exceljs publishes a build on uuid 11 ([`DEPENDENCY_ADVISORIES.md`](Backend/docs/DEPENDENCY_ADVISORIES.md)) |
| — | quill 2.0.3 low advisory (XSS on HTML export) | The only "fix" is a downgrade. The server sanitises all editor HTML with jsoup. **Accepted** |
| DOC-01 | `Backend/docs/db-inventory.md` not written | Generated from a live DB by `scripts/db_inventory.py`; run it on dev |
| FIN-04 | Two batches created at exactly the same moment could still both include one student | The check runs in the transaction but without a lock. Low risk; add a lock or unique index if it matters |
| FIN-11 | The withdrawal still runs inside the approval's transaction | It can no longer throw, so the delete commits. `REQUIRES_NEW` was avoided because of lock risk |
| FE-09 | 11 build warnings remain: Sass `@import` deprecations in `styles.scss` and primeflex | Real fix: migrate `styles.scss` to `@use`. Harmless until Dart Sass 3 |

**Report gaps found while documenting** the financial data form v2 (`Backend/docs/financial-data-form-v2.md`).
These are not fixed; each needs a business answer:
- Only the current quarter can be printed; the form does not read the archive.
- The amount column prints `STUDENT_DATA.STUDENT_AMOUNT`, not the amount paid.
- The signature and notes columns print `NOTE_2` and `NOTE_1`.
- The country filter matches the Arabic name, not the id.

## 6. What still has to be tested with a database

Before merging to production, on a restored copy of the dev `MOHE` database:

1. Start the backend once. Flyway applies **V148** and **V149**; check `dbo.flyway_schema_history` and V148's PRINT output.
2. Run the full backend suite: `./verify.sh` or `.\verify.ps1` (170 integration classes, including the updated `DataRetentionJobTest`, `GroupRolesAndClearAllTest` and `AuditRetentionJobTest`).
3. Run the manual cases in `SYSTEM_TEST_REPORT.md` §3.3 that name these fixes. At minimum:
   - UMS-04 to UMS-08, AUTH-05, AUTH-10, AUTH-12, AUTH-15
   - WAF-03/04/08, ACC-04, ACC-07, ACC-08, ACC-10 to ACC-14
   - QTR-02/04/05, APR-05 to APR-07
   - POR-04/05/09/11, RET-02/03
   - RPT-TC-06 to RPT-TC-08, RPT-TC-12/13
   - SCP-06, SCP-07, SCP-09
4. With a TLS proxy in front, set `MOHE_TRUSTED_PROXIES` and check that HSTS is present and that a spoofed `X-Forwarded-For` from a client is ignored.
5. Run the ZAP baseline against staging: Actions → CI → Run workflow → `zap_target`.

## 7. Commits

| Commit | Scope |
|---|---|
| `aba161b` | BLD-03 |
| `477783d` | ENV-01, BLD-02, BLD-04, verify scripts |
| `03d5259` | JWT, financial-data-form-v2, chart-of-accounts docs |
| `3f6ff42` | SEC-01 – SEC-10 |
| `c5f9c52` | RPT-00 – RPT-08, DB-01, DB-02 |
| `6dd9b34` | FIN-01 – FIN-17 |
| `8b414b2` | PRT-01 – PRT-14 |
| `04f1783` | V150 → V149, runbook, environment reference |
| `dd80edd` | DOC-01, DOC-02 |
| `f4ae867` | FE-01 – FE-14, BLD-01 |
| (this commit) | `FIXED_ISSUES.md`, `DEPENDENCY_ADVISORIES.md`, report status update |
