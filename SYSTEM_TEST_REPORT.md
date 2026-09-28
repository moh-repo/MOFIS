# MOHE — Whole-System Review & Test Report

| | |
|---|---|
| **Date** | 2026-09-28 |
| **Commit reviewed** | `f3290a8` (Initial version – MOHE V08 System) |
| **Scope** | Backend (Spring Boot 3.5 / Java 21, 1,241 source files, 160 REST controllers), Frontend (Angular 21, 632 TS files), DB migrations V001–V147, 53 Jasper report templates, CI configuration, documentation |
| **Method** | (1) ran every automated check that works without SQL Server, (2) code review of the whole system split into five areas (security & tenancy · finance & approvals · student portal, notifications & audit · reports & migrations · frontend), (3) wrote a full test plan for everything that needs a database or a human tester |

How to use this document:

1. **Section 2** lists every issue found, ranked by severity, with a checkbox. Work from the top: tick an issue once it is fixed **and** its regression test (the "Test" column) passes.
2. **Section 3** is the full test plan: how to run the automated suites, then the manual/functional test cases per module. Fill in the *Result* column during a test round.
3. **Section 4** lists automated tests that are missing today; each is written as a ready-to-implement test case.

Severity scale: **Critical** = privilege escalation / money or data loss now · **High** = wrong money or data leak under realistic use · **Medium** = incorrect behaviour with a workaround, or a defence-in-depth gap · **Low** = minor / cosmetic / hardening.
"Confirmed" = the code path was traced end to end. "Plausible" = the code supports it, but it depends on runtime data or configuration and must be reproduced.

---

## 1. Automated test results (this run)

Environment: Linux, JDK 21.0.10, Node 22.22, Python 3.11, Chromium 141 headless. **No SQL Server was available**, so the `@Tag("integration")` backend tests (170 classes) were **not run**.

| # | Check | Command | Result |
|---|---|---|---|
| A1 | Backend compile (main + test) | `./mvnw -B -ntp test` | ✅ compiles |
| A2 | Backend unit tests (no DB) | `./mvnw -B -ntp test` | ⚠️ **1,187 tests, 6 errors**, all from the OS locale (see ENV-01). With `LC_ALL=C.UTF-8` all 6 pass → effectively **1,187 / 1,187 pass** |
| A3 | Backend integration tests (170 classes) | `./mvnw test -DexcludedGroups=none` | ⏸ **not run**: needs the populated `MOHE` SQL Server database |
| A4 | Report template audit | `python scripts/audit_report_data.py` | ✅ exit 0. It reports 2 "missing property" warnings for `growth-analysis`, which the review showed to be a **false positive** (RPT-00) |
| A5 | Frontend install exactly as CI does it | `npm ci` | ❌ **fails**: `package-lock.json` out of sync (BLD-01) |
| A6 | Frontend install (workaround) | `CYPRESS_INSTALL_BINARY=0 npm install` | ✅ (the Cypress binary download is blocked by this sandbox; not a project defect) |
| A7 | i18n keys | `npm run validate-i18n` | ✅ ar and en both have 6,672 keys; every key used exists |
| A8 | Menu ↔ routes ↔ permissions | `npm run validate-menu` | ✅ 76 links resolve; 223 guard codes map to backend authorities |
| A9 | Reload streams | `npm run validate-reload-streams` | ✅ |
| A10 | Lint | `npx ng lint --quiet` | ✅ all files pass |
| A11 | Production build | `npx ng build --configuration production` | ✅ builds, with 25 warnings (component SCSS budgets exceeded, e.g. `student-finance-wafar` 25.9 kB against an 8 kB budget) |
| A12 | Dist hygiene | `npm run check-dist` | ✅ 218 files, no source maps, no dev values |
| A13 | Frontend unit tests | `ng test --watch=false --browsers=ChromeHeadlessWide` | ✅ **1,963 passed**, 5 skipped, 0 failed |
| A14 | npm audit (prod deps) | `npm audit --omit=dev` | ⚠️ 3 advisories (1 low, 2 moderate: `uuid` < 11.1.1 via `exceljs`). No high or critical, so the CI `--audit-level=high` gate passes |
| A15 | E2E (Cypress) | `npm run e2e` | ⏸ not run (the binary could not be downloaded here). There is only one spec (`layout-smoke.cy.ts`) |

---

## 2. Issues found

### 2.1 Critical

| ✔ | ID | Area | Location | Defect | How it fails | Fix | Regression test |
|---|---|---|---|---|---|---|---|
| [ ] | SEC-01 | UMS | `ums/controler/UmsUserController.java:137`, `UmsUserService.createUser:476-540`; same pattern in `UmsRoleService.createRole`, `UmsGroupService.createGroup`, `UmsPermissionService.createPermission` | The "create" endpoints bind the JPA entity with a writable `id` and call `save()`. A non-null id makes Spring Data **merge**, overwriting an existing row. *(Confirmed in code; reproduce at runtime.)* | A country admin POSTs `/ums/users/` with `{"id": <victim id>, "userName": "x", "password": "…"}`. The victim's username and password hash are replaced: **account takeover**, including ministry accounts. The same trick on `/ums/roles/` can rename a role to `MINISTRY_ADMIN`, raising the caller's tier. | Use create DTOs, or `setId(null)` before save. Mark `id` `@JsonProperty(access = READ_ONLY)` on all UMS entities. | POST with an existing `id` creates a new row (or returns 400); the victim row is unchanged. |
| [ ] | SEC-02 | UMS | `UmsPermissionController.java:94-110` → `UmsPermissionService.updatePermission` | Permission create, update and delete are gated only by `UMS_ADMIN.ADD/EDIT/DELETE`, which country admins hold. `PermissionCode` can be rewritten, with no audit and no cache eviction. | A country admin renames a permission they already hold to `SYSTEM.RESTORE` (or `SETTINGS.EDIT`, …). About 60 s later (the cache TTL) they hold that power. | Gate on `ADMIN_TOOLS_*` / `UMS_PERMISSIONS.*`. Make `PermissionCode` immutable. Audit the change and evict the authority cache. | A country admin calling PUT/POST/DELETE `/ums/permissions/**` gets 403. |
| [ ] | FIN-01 | Wafar | `wafar/service/WafarBalanceService.java:90`, `WafarSpendingService.create:137`, `applyCorrection:514`, `WafarSpendingCreateDto` | Nothing rejects a negative Wafar spend: `requireCovers` returns early for amounts ≤ 0, the DTO has no constraints, and there is no DB CHECK. The UI's `[min]=0` is the only guard. | A spend of −5000 is recorded and approved. The attaché's Wafar balance **increases** by 5000. | Add `@NotNull @Positive` on the amount, reject ≤ 0 in the service, and add `CHECK (AMOUNT > 0)`. | Create, update or correct with amount 0 or −1 returns 400. |

### 2.2 High

| ✔ | ID | Area | Location | Defect | How it fails | Fix | Regression test |
|---|---|---|---|---|---|---|---|
| [ ] | SEC-03 | UMS | `UmsRoleController.java:76-99`, `UmsGroupController.java:76-99` | Role and group create/update/delete are gated on `UMS_ADMIN.*` rather than the ministry-only codes, and update/delete never call `privilegeGuard.assertMayEditRole`. | A country admin deactivates or renames a global role, and staff in every country lose access. | Gate on `ADMIN_TOOLS_*`, call `assertMayEditRole`, and evict the cache. | A country admin calling POST/PUT/DELETE `/ums/roles/**` or `/ums/groups/**` gets 403. |
| [ ] | FIN-02 | Travel tickets / Wafar | `service/TravelTicketWafarService.java:93-121` | The auto-created APPROVED Wafar spend has no `studyCountryInId` and records the requester as the approver. | A Wafar-funded ticket is approved, but the attaché's available Wafar does not fall, so the money can be spent twice. | Set the country from the student and record the real approver. | After ticket approval, `availableFor(country)` falls by the ticket total. |
| [ ] | FIN-03 | Payment batches | `accounting/service/PaymentBatchService.java:241-263`, `AdvanceBalanceService.java:162-170` | `markPaid` is a read-modify-write with no lock and no `@Version` on `PaymentBatch` or `AttacheAdvanceBalance`. | Two concurrent "mark paid" calls both pass the APPROVED check, and the advance is **debited twice**. | Add `@Version`, or use a conditional `UPDATE … WHERE STATUS='APPROVED'` and an atomic increment. | Two parallel `markPaid` calls: one returns 409, and DISBURSED rises once. |
| [ ] | FIN-04 | Payment batches | `PaymentBatchService.validateLines:305-336`, `requireStudents:345` | The server does not refuse (a) a student already fully paid in another open batch of the same quarter, or (b) a student from a different attaché than the batch. | The same student is paid twice, or a Riyadh student is paid from Cairo's advance. | Validate both inside the transaction. | Each case is refused with 409 / 400. |
| [ ] | FIN-05 | Allocations | `accounting/controller/FundChainController.java:100` → `AllocationService.create:70-107` | Same entity-with-id merge bug as SEC-01. | A POST with the id of a TRANSFERRED allocation resets it to DRAFT. Transferring it again books the advance twice. | Clear the id / use a DTO (as `FxRateService.create` already does). | POST with an existing id leaves the original untouched. |
| [ ] | FIN-06 | Approvals | `approval/service/ApprovalConfigService.java:106`, `ApprovalWorkflowService.java:406` | The final level is read from the **live** config's `totalLevels`, which the request body sets freely. | Setting `totalLevels=1` makes in-flight 3-level requests final after one signature (**four-eyes bypass**). Deactivating a middle level leaves requests stuck ("Current step not found"). | Derive the final level from the request's own steps; ignore `totalLevels` on update. | Changing the config does not change the number of signatures an in-flight request needs. |
| [ ] | FIN-07 | Quarters | `service/MoheQuarterCreateService.java:119-160`, `MoheQuarterCloseService.java:209` | `MOHE_QUARTER` is global, but roll-forward and close touch only the caller's countries. | An attaché user creates 20263. Every other country stays on 20262 permanently. | Restrict create/close to ministry users, or make quarters per-country. | An attaché-scoped user creating or closing a quarter gets 403. |
| [ ] | PRT-01 | Data retention | `retention/DataRetentionJob.java:191-201` | "Inactive for N years" is measured from `STUDENT_MIDATE` (the **entry** date), not from when the terminal status was set. | A student who joined 8 years ago and withdrew yesterday is **anonymised and their files deleted** on the next apply. This is irreversible. | Use the status-change date. | A student withdrawn yesterday is not a candidate. |

### 2.3 Medium

| ✔ | ID | Area | Location | Defect / effect | Fix |
|---|---|---|---|---|---|
| [ ] | SEC-04 | Network | `application.properties:64` (`forward-headers-strategy=native`) | Tomcat trusts `X-Forwarded-For` from any private-range peer. A spoofed header evades the login throttle, passes the admin IP allowlist, and fakes the audit IP. | Set `server.tomcat.remoteip.internal-proxies` to the real proxy only. |
| [ ] | SEC-05 | Tenancy | `TenantContextFilter.java:167`, `TenantContext.Scope.of:244` | A ministry user who sends `X-Country-Id` gets `wafarWrite=true`, which disables the V095 DB backstop against ministry Wafar writes. | Derive `wafarWrite` from the base tier. |
| [ ] | SEC-06 | Rate limit | `LoginRateLimitFilter.contextRelativePath:122`, `ApiRateLimitFilter` | They match on the raw, undecoded URI, so `…/auth/logi%6E` bypasses the login throttle. *(Plausible, high confidence.)* | Match on the decoded, normalised path. |
| [ ] | FIN-08 | Quarter close | `MoheQuarterCloseService.java:150-156`, `AllocationService.rollUpToQuarter:182` | The budget-cap check sums amounts across currencies and across all quarters. | Group by currency and quarter. |
| [ ] | FIN-09 | Quarter close | `MoheQuarterCloseService.java:183-196` | A non-current quarter can be closed, which archives the next quarter's data under the old key. | Refuse unless the quarter is the current one. |
| [ ] | FIN-10 | Budget | `BudgetAppropriationService.java:116-122,159` | The last editor of a draft can approve it (maker-checker only checks `createdBy`). | Check the approver against the last editor as well. |
| [ ] | FIN-11 | Approvals | `ApprovalIntegrationService.java:1313-1320` | The inner `rejectRequest` joins the outer transaction. A caught exception still marks it rollback-only, so the approved DELETE is lost (`UnexpectedRollbackException`). | Use `REQUIRES_NEW`, or pre-check. |
| [ ] | FIN-12 | Fiscal periods | `FiscalPeriodService.java:100` (`assertPeriodOpen` is never called), `FundChainController.java:73-88` | Money can move in a closed fiscal year. The closer's `userId` comes from a request parameter (spoofable). | Call `assertPeriodOpen` on the write paths; take the user from the security context. |
| [ ] | FIN-13 | Allocations | `AllocationService.create:92` | A client-sent `fiscalYearId` that contradicts the quarter is kept. | Always derive it from the quarter. |
| [ ] | FIN-14 | Wafar | `WafarSpendingService.approve:257` | Balance check plus approve is not serialised, so two concurrent approvals can overdraw the balance. *(Plausible.)* | Lock per attaché. |
| [ ] | PRT-02 | Retention | `DataRetentionJob.java:219-265` | Anonymisation misses document-history files, the photo file, change-request old/new values, info-request staging JSON and inquiry text. | Scrub them all. |
| [ ] | PRT-03 | Portal | `StudentChangeRequestService.java:59,406-438` | An approved `studyCountryIn` change does not relocate the student's files or end their sessions. | Call `relocateForCountryChange` and end sessions, or disallow the field. |
| [ ] | PRT-04 | Portal | `StudentUserAuthService.java:144-146` | There is no per-account lockout for portal passwords (`failed_login_attempts` is never incremented). | Mirror the staff `LoginAttemptService`. |
| [ ] | PRT-05 | Portal | `StudentPortalMeController.java:646`, `StudentInfoRequestService.java:288-296,404-418` | An info-request response may propose **any** StudentDatum or StudentFinance field (e.g. `studentAmount`). *(Plausible.)* | Whitelist fields per request type. |
| [ ] | RPT-01 | Masking | `model/CustomFilterReport.java:35,94-96` | The passport number is masked under the national-ID permission (and labelled `national_number`), so it leaks in custom-filter exports and scheduled emails. | Mask it with `studentPassportNumber`. |
| [ ] | RPT-02 | Query builder | `ReportFieldMask.java:81,162-187`, `QueryExecutionService.maskHiddenColumns` | Masking rules are keyed by table name, so passport and ID columns read through a view (e.g. `V_STUDENT_PROFILE_DOC`) come back unmasked. | Match by column name across all sources. |
| [ ] | RPT-03 | Query builder | `QueryExecutionService.buildFrom:326-335`, `appendCountryScope:585-640` | Arbitrary join pairs plus scope on one source only means a LEFT JOIN to a person table with no country column may return other countries' rows. *(Plausible.)* | Allow key/FK joins only. |
| [ ] | DB-01 | Migrations | `docs/scripts/baseline/V000__baseline_schema.sql:210,3462` | The baseline creates an empty `flyway_schema_history`. On a DB built from it, Flyway re-runs V001–V147. *(Plausible.)* | Exclude that table or seed the baseline row. |
| [ ] | FE-01 | Frontend auth | `error.interceptor.ts:86-92` + `MfaService.java:52-55` | A wrong MFA code on /profile (disable MFA, regenerate codes) returns 401, and the interceptor **logs the user out**. | Exclude `/ums/auth/mfa/` from auto-logout, or return 400/422. |
| [ ] | FE-02 | Frontend auth | `app.module.ts:142-145` | `ErrorInterceptor` sees a 401 before `AuthInterceptor` and clears the token, so silent refresh on 401 can never run. | Reorder the interceptors; add a chain test. |
| [ ] | FE-03 | Frontend auth | `auth.interceptor.ts` (handleRefresh) | A proactive refresh that fails with a network error or 5xx logs the user out even though the token is still valid. | Log out only on 401/403. |
| [ ] | BLD-01 | Build | `Frontend/package-lock.json` | The lock file is out of sync with package.json (missing `chokidar@5.0.0` and `readdirp@5.1.1`), so **`npm ci` fails**, and the CI frontend job with it. | Run `npm install` and commit the updated lock file. |
| [ ] | BLD-02 | CI | `.github/workflows/ci.yml` (zap-baseline job) | The job reads `.zap/rules.tsv` and `.zap/check_high.py`, but `.zap/` is in `.gitignore` and absent. | Commit `.zap/` (remove it from `.gitignore`). |
| [x] | BLD-03 | CI | `Backend/mvnw` | Committed without the execute bit, so CI's `./mvnw` fails with "Permission denied". | **Fixed in this branch** (`chmod +x`). |
| [ ] | ENV-01 | Deployment | JVM file-name encoding | With a non-UTF-8 OS locale (POSIX/C), Arabic file names throw `InvalidPathException`. 6 unit tests fail (`ScholarshipDeleteModeTest`, `StudentFileStorageServiceTest`), and in production, uploads or imports with Arabic names fail. | Run the server with `LANG=C.UTF-8` (or `-Dsun.jnu.encoding=UTF-8 -Dfile.encoding=UTF-8`). Put it in the surefire `argLine` and the deployment docs. |

### 2.4 Low

| ✔ | ID | Location | Defect |
|---|---|---|---|
| [ ] | SEC-07 | `MustChangePasswordInterceptor.java:31-40` | The exemptions are too broad: a user with a temporary password can call `POST /ums/auth/reset-password` and `PUT /sys-settings`. |
| [ ] | SEC-08 | `StudentPortalTenantScope.of:57` | Portal connections carry `TenantWafarWrite=1`. |
| [ ] | SEC-09 | `application-dev.properties` | Dev JWT secret, file key, `sa`/`P@ssw0rd` and a portal test account are committed. Move them to an untracked env file. |
| [ ] | PRT-06 | `StudentUserAuthService.java:133-146` | Login timing reveals which e-mails are active portal accounts (bcrypt is skipped for unknown users). |
| [ ] | PRT-07 | `StudentPortalMfaService.java:685-707` | There is no TOTP replay guard on the portal (staff MFA has one). |
| [ ] | PRT-08 | `audit/AuditRetentionJob.java:120-176` | The audit archive is written unredacted and in plaintext (it includes bcrypt hashes and PII). |
| [ ] | PRT-09 | `DataRetentionJob.java:112,126-131` | Transaction and elevation handling in `apply()` is ineffective; a failure mid-student leaves that student half-anonymised. |
| [ ] | PRT-10 | `StudentPortalTokenInterceptor.java:74-83` | Legacy MOHECORE portal tokens survive logout and deactivation. Remove the fallback. |
| [ ] | PRT-11 | `StudentPortalMeController.java:434,479` | Decrypt and read failures on download are swallowed without a log. |
| [ ] | PRT-12 | `StudentChangeRequestController.java:74-81` | A numeric `newValue` causes a ClassCastException, so the client gets 500 instead of 400. |
| [ ] | PRT-13 | `util/FileUploadValidator.java:116` | The SVG active-content regex is bypassable (`javascript&#58;`). Impact is limited to the super-admin logo. |
| [ ] | PRT-14 | `StudentPortalMeController.java:506-520,622-651` | The file is written before the DB insert, with no cleanup, leaving orphan files on failure. |
| [ ] | FIN-15 | `WafarReconciliationService.java:154` | The earmark is rounded to an integer before comparing, so 100.40 against 100 shows MATCHED. |
| [ ] | FIN-16 | `PaymentBatchService.cancel:275` | Cancel writes `APPROVED_BY`/`APPROVED_AT`. |
| [ ] | FIN-17 | `security/policy/ApprovalRequestPolicy:100-109` | A super admin can approve their own money request through every level. This is by design, but it is worth a decision. |
| [ ] | RPT-00 | `scripts/audit_report_data.py` | False positive on `growth-analysis` (the audit checks the wrong bean). Fix the audit rule. |
| [ ] | RPT-04 | `QueryExecutionService.java:466-510,659-678` | Masked columns can still be filtered and sorted on, so values can be recovered by `LIKE` probing. |
| [ ] | RPT-05 | `DataVisualizationController.java:36-67` | No application-level country filter; it relies entirely on RLS. |
| [ ] | RPT-06 | `StudentExtensionStatisticsReportController.java:66` | The export ignores the `year` parameter. |
| [ ] | RPT-07 | `GrowthAnalysisRepository.java:29-58` | Growth is computed on a cumulative count ("declining" can never appear); years with no students are dropped. |
| [ ] | RPT-08 | `QueryReportTicketService` | Tickets are held in memory and the whole map is cleared at 500 entries; PDF links 404 on multi-node or under load. |
| [ ] | DB-02 | `V031__row_level_security.sql:67-136` | Re-running it silently turns RLS **off** and drops the predicates later scripts added. |
| [ ] | FE-04 | `inquiries.component.ts:50`, `chart-of-accounts.component.ts:85`, `allocations.component.ts:83` | `currentLanguage$` subscriptions are never unsubscribed (memory leak). |
| [ ] | FE-05 | `payment-auth-report.component.ts:204` | Hard-coded English toast text. |
| [ ] | FE-06 | `student-finance-wafar.component.scss:280,399,412,456` | `text-align:right` is wrong in LTR (English); use `end`. |
| [ ] | FE-07 | `index.html:15` vs `environment.prod.ts` | The CSP `connect-src 'self'` blocks the documented `splitOriginApi` option. |
| [ ] | FE-08 | `ums-permission.guard.ts:196-199` | A legacy `role.roleName==='ADMIN'` fallback from storage passes UI guards (the backend still refuses). |
| [ ] | FE-09 | `angular.json` budgets | 25 build warnings (component SCSS over budget). |
| [ ] | FE-10 | `package.json` | `uuid` < 11.1.1 (moderate) via `exceljs`; track upstream. |
| [ ] | DOC-01 | `README.md` and others | The docs link to files that **do not exist**: `verify.ps1`, `Backend/docs/ENVIRONMENT.md`, `MIGRATION_RUNBOOK.md`, `JWT_AUTH.md`, `db-inventory.md`, `financial-data-form-v2.md`, `report-data-audit.md`, `chart-of-accounts-revenue-load.md`, `DEPENDENCY_ADVISORIES.md`, `DEPLOYMENT_SECURITY.md`, `MASTER-STATUS-AND-PLAN-2026-09-22.md`. The missing `verify.ps1` and `MIGRATION_RUNBOOK.md` are the ones that matter most: they are the documented way to run the DB tests and apply V001–V096. |
| [ ] | DOC-02 | `.github/workflows/ci.yml` header | It says integration tests are "97 of 162 classes"; today it is 170 of ~370 test files. |

**Areas checked and found clean:** JWT algorithm, expiry, jti and MFA-pending handling; refresh rotation; CORS; portal IDOR on documents, tickets, inquiries and info requests; captcha single-use; OTP attempt cap; token hashing; path traversal (`SafePathUtils`); AES-GCM nonce handling; sort whitelists; e-mail HTML sanitising; WhatsApp JSON escaping; query-builder identifier whitelisting and value binding; migration numbering (V001–V147 contiguous, no duplicates; only top-level scripts are packaged); all 242 `@Table` entities resolve to schema objects; every frontend API base path matches a backend controller; every frontend route has a guard; ar/en i18n parity.

---

## 3. Test plan

### 3.1 Test environments

| Env | Needs | Purpose |
|---|---|---|
| **Local / CI (no DB)** | JDK 21, Node 22, Python 3.12, Chromium, **UTF-8 locale** | Unit tests, lint, build, static audits (Section 1) |
| **Integration** | The above plus SQL Server with the restored `MOHE` dev backup, migrations applied through V147 | 170 `@Tag("integration")` backend classes |
| **System / UAT** | Backend (`dev` or staging profile) plus frontend, SMTP/WhatsApp test gateway (`docs/scripts/adhoc/notification-test-gateway.mjs`) | Manual functional cases below, E2E |

**Test accounts needed** (create once in UMS and record them here):

| Role | Username | Country | MFA |
|---|---|---|---|
| Super admin | | – | on |
| Ministry admin | | all | on |
| Ministry staff (view-only) | | all | off |
| Country admin – country A | | A | off |
| Country staff (finance officer) – A | | A | off |
| Country staff (checker / approver) – A | | A | off |
| Country staff – country B | | B | off |
| Student portal user – country A | | A | on |
| Student portal user – country B | | B | off |

### 3.2 Automated suites, run order

```bash
# Backend (always with a UTF-8 locale)
cd Backend
export LC_ALL=C.UTF-8 LANG=C.UTF-8
./mvnw -B -ntp test                           # unit tests only (≈1,187 tests)
./mvnw -B -ntp test -DexcludedGroups=none     # + integration (needs SQL Server)
./mvnw -B -ntp compile spotbugs:check         # static security analysis
./mvnw -B -ntp dependency-check:check         # CVE scan (set NVD_API_KEY)
python scripts/audit_report_data.py           # report template wiring

# Frontend
cd ../Frontend
npm ci                                        # must pass once BLD-01 is fixed
npm run validate-i18n && npm run validate-menu && npm run validate-reload-streams
npx ng lint --quiet
npx ng test --watch=false --browsers=ChromeHeadlessWide
npm run build:prod                            # build + check-dist
npm audit --omit=dev --audit-level=high
npm run e2e                                   # Cypress, against a running app
```

Exit criteria: every command exits 0, and every integration class passes against the dev database.

### 3.3 Functional test cases (manual / E2E)

Format: **ID · Steps · Expected**. Record Pass/Fail and the defect ID in *Result*.

#### 3.3.1 Staff authentication & session

| ID | Steps | Expected | Result |
|---|---|---|---|
| AUTH-01 | Log in with a valid username and password (no MFA). | Dashboard opens; the menu shows only permitted pages. | |
| AUTH-02 | Wrong password 1× | Generic error; no hint whether the user exists. | |
| AUTH-03 | Wrong password until the lockout threshold | Account locked for the configured window; a correct password is also refused. | |
| AUTH-04 | More than 10 login POSTs/min from one IP | 429 Too Many Requests. | |
| AUTH-05 | Repeat AUTH-04 with a percent-encoded path (`…/logi%6E`) | Still throttled (SEC-06). | |
| AUTH-06 | Log in to an MFA-enabled account; enter a correct TOTP | Signed in. | |
| AUTH-07 | MFA with a wrong code | Refused; stay on the MFA screen. | |
| AUTH-08 | MFA with a recovery code, then reuse the same code | First use succeeds; reuse is refused. | |
| AUTH-09 | Enrol MFA from /profile (scan QR, confirm) | MFA enabled; recovery codes shown once. | |
| AUTH-10 | /profile → disable MFA with a **wrong** code | Error message; user **stays signed in** (FE-01). | |
| AUTH-11 | Account with must-change-password logs in | Redirected to /profile?changePassword=required; every other route redirects back. | |
| AUTH-12 | Must-change-password account calls `PUT /sys-settings` via API | 403 PASSWORD_CHANGE_REQUIRED (SEC-07). | |
| AUTH-13 | Change password: weak / common / reused | Each is refused with a specific message. | |
| AUTH-14 | Idle until the token passes 75% of its life, then act | Token silently refreshed; no logout. | |
| AUTH-15 | Stop the backend while the token is at 80% of its life, then restart it | User is **not** logged out (FE-03). | |
| AUTH-16 | Log out | Tokens removed from local and session storage; Back button does not reopen pages; old token refused by the API. | |
| AUTH-17 | Log in on N+1 devices (concurrent-session limit) | Oldest session ended, or the new login refused, per policy. | |
| AUTH-18 | Admin ends a user's session in UMS → Sessions | That user's next request gets 401. | |
| AUTH-19 | Open `#/finance` directly as a user without FINANCE | Redirected to /auth/access. | |
| AUTH-20 | Mistyped URL | /notfound page. | |

#### 3.3.2 UMS / administration

| ID | Steps | Expected | Result |
|---|---|---|---|
| UMS-01 | Super admin creates a user with roles, groups and countries | User can log in and sees exactly the granted pages. | |
| UMS-02 | Country admin A creates a user for country A | Allowed. | |
| UMS-03 | Country admin A creates a user for country B | 403. | |
| UMS-04 | Country admin POSTs `/ums/users/` with `"id"` of an existing user | New row or 400; **existing user unchanged** (SEC-01). | |
| UMS-05 | Same as UMS-04 for `/ums/roles/`, `/ums/groups/`, `/ums/permissions` | Existing rows unchanged. | |
| UMS-06 | Country admin PUT/POST/DELETE `/ums/permissions/**` | 403 (SEC-02). | |
| UMS-07 | Country admin create/rename/deactivate a role or group | 403 (SEC-03). | |
| UMS-08 | Rename any role to `MINISTRY_ADMIN` / `COUNTRY_ADMIN` | Refused. | |
| UMS-09 | Remove a permission from a role | The holder loses it on the next request (note the cache TTL). | |
| UMS-10 | Effective permissions page for a user | Matches the union of roles, groups and direct grants. | |
| UMS-11 | Access review: run, then revoke | Revoked access disappears; the action is audited. | |
| UMS-12 | Pages / reports / API-links admin (super admin only) | Other tiers get 403. | |
| UMS-13 | Lockout policy change | New thresholds apply to the next failures. | |
| UMS-14 | System settings: change the logo (PNG, SVG) | Accepted and shown; SVG with script or `javascript&#58;` rejected (PRT-13). | |
| UMS-15 | Non-super-admin opens /settings/system-settings | 403 / access page. | |

#### 3.3.3 Country scoping (tenancy); run each case as Country-A staff

| ID | Steps | Expected | Result |
|---|---|---|---|
| SCP-01 | Student list | Only country-A students. | |
| SCP-02 | Open a country-B student by URL/ID | 404/403. | |
| SCP-03 | Send `X-Country-Id: <B>` | Ignored or 403; never B's data. | |
| SCP-04 | Dashboard, statistics, charts | Only A's figures. | |
| SCP-05 | Every report export (PDF/Excel) | Only A's rows. | |
| SCP-06 | Query builder (as system admin with country scope): join STUDENT_DATA to a table without a country column | No country-B rows (RPT-03). | |
| SCP-07 | Data visualisation screen and export | A's aggregates only (RPT-05). | |
| SCP-08 | Audit log | Only A's entries. | |
| SCP-09 | Ministry user with the country selector set to A | Only A's data; Wafar writes still refused for ministry (SEC-05). | |
| SCP-10 | Approvals pending list | Only A's requests. | |

#### 3.3.4 Student records

| ID | Steps | Expected | Result |
|---|---|---|---|
| STU-01 | Add a student with all mandatory fields | Saved; appears in the list; audited. | |
| STU-02 | Add with a missing mandatory field / bad passport / bad phone | Field errors; nothing saved. | |
| STU-03 | Duplicate passport or national ID | Refused. | |
| STU-04 | Edit a student (full form) | Changes saved; history row created. | |
| STU-05 | Quick edit (grid) | Saved; the same validation as the full form. | |
| STU-06 | Upload a photo (crop) | Stored encrypted; displayed. | |
| STU-07 | Upload documents: PDF, JPG, and a renamed EXE (`x.pdf`) | The first two are accepted; the fake PDF is refused. | |
| STU-08 | Upload a file with an **Arabic name** | Accepted (needs a UTF-8 locale on the server; ENV-01). | |
| STU-09 | Document history: replace a document | Old version kept in history; downloadable. | |
| STU-10 | Change a student's study country | Files moved to the new country folder; the student visible only to the new country. | |
| STU-11 | Student history / tracer screens | Show the correct timeline. | |
| STU-12 | Delete a student (with approval if configured) | Removed from lists and reports; finance rows handled. | |
| STU-13 | Extension request → approve | Study end date extended. | |
| STU-14 | Data import (Excel): valid file, then a file with bad rows | Valid rows imported; bad rows reported per line. | |
| STU-15 | Scholarship delete modes (none / country / all) | Only the chosen scope is deleted. | |
| STU-16 | Year results entry | Saved and shown in the portal. | |

#### 3.3.5 Finance, fees, allowances, loans, tickets

| ID | Steps | Expected | Result |
|---|---|---|---|
| FIN-TC-01 | Enter student finance for the current quarter | Saved; net amount calculated correctly (check by hand). | |
| FIN-TC-02 | Finance archive | Previous quarters are read-only. | |
| FIN-TC-03 | University fee: add, edit, archive | Correct totals per currency. | |
| FIN-TC-04 | Book allowance request → approve | Amount applied once. | |
| FIN-TC-05 | Loan request → approve | Loan recorded; repayment schedule correct. | |
| FIN-TC-06 | Travel ticket (normal) → approve | Ticket recorded. | |
| FIN-TC-07 | Travel ticket **funded from Wafar** → approve | Attaché Wafar balance falls by the ticket amount (FIN-02). | |
| FIN-TC-08 | Finance received-whom → approve | Applied. | |
| FIN-TC-09 | Payment authorisation form → print | PDF amounts and amount-in-words (Arabic) correct. | |
| FIN-TC-10 | Amounts with decimals (e.g. 1234.565) | Rounding is consistent between screen, DB and PDF. | |

#### 3.3.6 Wafar

| ID | Steps | Expected | Result |
|---|---|---|---|
| WAF-01 | Record a Wafar spend within the balance → approve | Balance falls. | |
| WAF-02 | Record a spend above the available balance | Refused. | |
| WAF-03 | Record a spend with amount **0** or **negative** via the API | 400 (FIN-01). | |
| WAF-04 | Two spends that together exceed the balance, approved at the same time | Only one succeeds (FIN-14). | |
| WAF-05 | Reject / cancel a spend | Balance unchanged. | |
| WAF-06 | Ministry correction | Applied with a correct audit trail; negative correction refused. | |
| WAF-07 | Ministry user tries to create a Wafar spend | Refused. | |
| WAF-08 | Wafar reconciliation with 100.40 against 100 | Shows MISMATCH (FIN-15). | |
| WAF-09 | Student finance Wafar screen | Per-student figures agree with the attaché total. | |

#### 3.3.7 Accounting / fund chain

| ID | Steps | Expected | Result |
|---|---|---|---|
| ACC-01 | Chart of accounts: browse, add a revenue item | Shown in ar and en. | |
| ACC-02 | FX rates: add a rate; convert | Correct conversion; duplicate date/currency refused. | |
| ACC-03 | Budget appropriation: A creates, A approves | Refused (maker-checker). | |
| ACC-04 | Appropriation: A creates, B edits the amount, B approves | Refused (FIN-10). | |
| ACC-05 | Appropriation approve → revise | Revision opens a new draft; the original is kept. | |
| ACC-06 | Allocation: create → approve (other user) → transfer | Advance balance rises once. | |
| ACC-07 | POST an allocation with the id of an existing one | Original untouched (FIN-05). | |
| ACC-08 | Allocation with a fiscal year that contradicts the quarter | Corrected or refused (FIN-13). | |
| ACC-09 | Payment batch: build from candidates → approve → mark paid | Advance falls once; batch DISBURSED. | |
| ACC-10 | Mark the same batch paid from two browsers at once | One succeeds; the other gets 409 (FIN-03). | |
| ACC-11 | Add a student already fully paid this quarter to a second batch | Refused (FIN-04). | |
| ACC-12 | Add a country-B student to a country-A batch | Refused (FIN-04). | |
| ACC-13 | Cancel an open batch | Status CANCELLED; not shown as approved (FIN-16). | |
| ACC-14 | Any allocation or batch action in a closed fiscal period | Refused (FIN-12). | |
| ACC-15 | Overdraw the advance | DB constraint refuses; the UI shows a clear message. | |

#### 3.3.8 Quarters (operations)

| ID | Steps | Expected | Result |
|---|---|---|---|
| QTR-01 | Ministry creates the next quarter | All countries' finance rows roll forward; previous archived. | |
| QTR-02 | Country user creates or closes a quarter | 403 (FIN-07). | |
| QTR-03 | Close the current quarter | Archive and ledger written with the right key. | |
| QTR-04 | Close a **non-current** quarter | Refused (FIN-09). | |
| QTR-05 | Close with the budget cap exceeded in one currency only | Check evaluated per currency (FIN-08). | |
| QTR-06 | Quarter rollover with students added mid-quarter | "Added quarter" is correct. | |

#### 3.3.9 Approvals

| ID | Steps | Expected | Result |
|---|---|---|---|
| APR-01 | Configure a 3-level chain; submit a request | It needs 3 different approvers in order. | |
| APR-02 | Requester tries to approve their own request | Refused (separation of duties). | |
| APR-03 | Same user tries to approve two levels | Refused. | |
| APR-04 | Reject at level 2 | Request REJECTED; the change is not applied. | |
| APR-05 | Change config `totalLevels` 3→1 while a request is in flight | In-flight request still needs 3 (FIN-06). | |
| APR-06 | Deactivate the middle level while a request is in flight | Request still completes or is re-routed; never stuck. | |
| APR-07 | Approve a recipient DELETE whose linked request can't be withdrawn | Delete still commits (FIN-11). | |
| APR-08 | My requests / history pages | Show correct statuses and approver names. | |
| APR-09 | Approval-changes diff view | Old and new values are correct; masked fields stay masked. | |

#### 3.3.10 Reports (repeat for ar and en, PDF and Excel)

| ID | Steps | Expected | Result |
|---|---|---|---|
| RPT-TC-01 | Open each report from /reports/list (all 53 templates) | Renders with no blank columns and correct headers and footers. | |
| RPT-TC-02 | Arabic PDF | Right-to-left layout, Arabic font, correct numbers and amount-in-words. | |
| RPT-TC-03 | Excel export of each report | Opens in Excel; the language matches. | |
| RPT-TC-04 | Export above the row cap (country staff > 5,000 rows) | 422 REPORT_EXPORT_ROW_CAP; audited. | |
| RPT-TC-05 | Watermark on exports | Present with the user name and time. | |
| RPT-TC-06 | User **without** passport permission exports custom filter / student list | Passport column empty (RPT-01). | |
| RPT-TC-07 | Query builder: select a passport column from `V_STUDENT_PROFILE_DOC` without permission | Value masked (RPT-02). | |
| RPT-TC-08 | Query builder: filter or sort on a masked column | Refused (RPT-04). | |
| RPT-TC-09 | Query builder: identifiers with `]`, `;` or `--` | 400 "Unknown column". | |
| RPT-TC-10 | Saved query shared by a ministry user, run by a country user | Result limited to the runner's country. | |
| RPT-TC-11 | Scheduled report (e-mail) | Delivered; masked per the recipient's rights. | |
| RPT-TC-12 | Student-extension statistics export with a year filter | Matches the screen (RPT-06). | |
| RPT-TC-13 | Growth analysis | Year-to-year change is correct; empty years shown (RPT-07). | |
| RPT-TC-14 | Financial data form v1 / v2, quarterly financial report | Totals match the finance screens. | |
| RPT-TC-15 | Geographic maps and graphs | Load; export as an image works. | |
| RPT-TC-16 | PDF preview via `/mohe/api/pdf/**` | Loads (the ad-blocker alias). | |

#### 3.3.11 Notifications, alerts, calendar

| ID | Steps | Expected | Result |
|---|---|---|---|
| NOT-01 | Send an e-mail notification to one student / a group | Delivered (test gateway); HTML sanitised. | |
| NOT-02 | Send WhatsApp (each configured mode) | Delivered; special characters escaped. | |
| NOT-03 | Notification settings: secrets masked on read | Masked. | |
| NOT-04 | Test-send from settings | Success / clear failure message. | |
| NOT-05 | History filters | Correct results; scoped by country. | |
| NOT-06 | Alert events: a rule triggers | Alert raised once; the case can be worked and closed. | |
| NOT-07 | Real-time notifications (SSE) | Arrive without a reload; the ticket is single-use. | |
| NOT-08 | Calendar agenda | Items shown for the right country. | |

#### 3.3.12 Student portal

| ID | Steps | Expected | Result |
|---|---|---|---|
| POR-01 | Log in: captcha, password, country, e-mail OTP (first time), TOTP | Dashboard. | |
| POR-02 | Reuse a solved captcha | Refused. | |
| POR-03 | Wrong OTP 5× | Code invalidated. | |
| POR-04 | Wrong password many times (rotating IPs) | Account locked (PRT-04). | |
| POR-05 | Reuse the same TOTP code within 90 s | Refused (PRT-07). | |
| POR-06 | Forgot / reset password | Works; all sessions ended. | |
| POR-07 | My data | Only own data; the field list is correct. | |
| POR-08 | Change request: submit, cancel; staff approves | Applied; history kept. | |
| POR-09 | Change request with a numeric `newValue` via the API | 400 (PRT-12). | |
| POR-10 | Documents: upload / download own; try another student's document ID | Own only; others 404. | |
| POR-11 | Info request: respond with extra fields (e.g. `studentAmount`) | Refused (PRT-05). | |
| POR-12 | Inquiries / messages | Two-way; staff sees them in the right country. | |
| POR-13 | Travel tickets, extensions, year results, finance status | Correct and read-only where expected. | |
| POR-14 | Staff and portal sessions in the same browser | Independent; a portal 401 does not log out the staff user. | |
| POR-15 | Log out, then reuse the token | 401. | |
| POR-16 | Arabic / English switch | Full translation; RTL layout. | |

#### 3.3.13 Audit, retention, backup

| ID | Steps | Expected | Result |
|---|---|---|---|
| AUD-01 | Edit a student, then open the audit log | Entry with before/after; sensitive values redacted. | |
| AUD-02 | Audit log sort and filter | Works; scoped. | |
| AUD-03 | Audit retention job run | Old rows archived and purged; the archive has no password hashes (PRT-08). | |
| RET-01 | Data retention **dry run** | Candidate list only; nothing changed. | |
| RET-02 | Candidate check: joined 8 years ago, withdrew yesterday | **Not** a candidate (PRT-01). | |
| RET-03 | Apply retention on a test copy | Student anonymised everywhere, including history files, photo, change requests and inquiries (PRT-02). | |
| BAK-01 | Backup and restore screen (super admin) | Backup created; restore works on a test DB; other roles 403. | |
| BAK-02 | DR drill (`scripts/dr_drill.ps1`) | Completes within the target RTO. | |

#### 3.3.14 Lookups & settings

| ID | Steps | Expected | Result |
|---|---|---|---|
| LKP-01 | Add / edit / deactivate each lookup table | Saved; every field kept on update; names required in ar and en. | |
| LKP-02 | Duplicate lookup name | Refused. | |
| LKP-03 | Delete a lookup in use (e.g. document type) | Refused or soft-deleted. | |
| LKP-04 | Study years / study amount lookups | Used correctly in finance calculations. | |
| LKP-05 | Code lists and system parameters | Changes take effect; audited. | |
| LKP-06 | Report settings (header, footer, signatures) per country | Printed on that country's reports only. | |

### 3.4 Non-functional tests

| ID | Area | Test | Expected |
|---|---|---|---|
| NFR-01 | Performance | Student list, 941+ students, sort and filter | < 2 s |
| NFR-02 | Performance | Heaviest report PDF (all countries) | < 30 s; no out-of-memory |
| NFR-03 | Load | 50 concurrent staff users for 15 min (JMeter/k6) | No 5xx; p95 < 3 s; API rate limit not hit by normal use |
| NFR-04 | Security | OWASP ZAP baseline, anonymous and authenticated (needs BLD-02 fixed) | No High alerts |
| NFR-05 | Security | SpotBugs/FindSecBugs, OWASP Dependency-Check, CodeQL | No high-confidence or CVSS ≥ 7 findings |
| NFR-06 | Security | Response headers (CSP, HSTS, X-Frame-Options, nosniff, Cache-Control on /api) | Present |
| NFR-07 | Deployment | Start with the `prod` profile and a required env var missing | Fails fast with a clear message |
| NFR-08 | Deployment | Server locale not UTF-8 | Documented or forced to UTF-8 (ENV-01) |
| NFR-09 | Deployment | Flyway on the existing DB (baselined at 96) → V097–V147 | Applied once; restart is a no-op |
| NFR-10 | Browser | Chrome, Edge, Firefox; 1366×768 and mobile width | Layout usable; RTL correct |
| NFR-11 | Accessibility | Keyboard navigation and screen-reader labels on the main forms | Operable |

---

## 4. Missing automated tests (to add)

Coverage today: backend unit tests are strong (≈1,187). Frontend: **144 of 179 components and 59 of 76 services have no spec**, including the largest (data-import, student-add, student-quick-edit, pending-approvals, finance form, student-finance-wafar, approval-configs, `lookup.service`, `student-portal-api.service`), plus the accept-language and country-scope interceptors. There is a single Cypress spec. There are no tests for `TravelTicketWafarService`, `ApprovalConfigService` level counts, or any allocation/batch concurrency.

Backend (add one test per item, each linked to the issue it guards):

1. `UmsUser/Role/Group/Permission` create with an existing `id` → the victim row is unchanged (SEC-01).
2. Country admin → `/ums/permissions`, `/ums/roles`, `/ums/groups` writes → 403 (SEC-02, SEC-03).
3. Ministry user sending `X-Country-Id`, and portal requests → the connection has `TenantWafarWrite=0` (SEC-05, SEC-08).
4. Percent-encoded login path is counted by the throttle (SEC-06).
5. Spoofed `X-Forwarded-For` from 10.x is not trusted (SEC-04).
6. Wafar amount ≤ 0 → 400 on create, update and correction (FIN-01).
7. Wafar-funded ticket reduces `availableFor(country)` (FIN-02).
8. Concurrent `markPaid` → a single debit (FIN-03).
9. Batch lines: already-paid student or wrong country → refused (FIN-04).
10. Allocation create with an existing id (FIN-05); fiscal year mismatch (FIN-13).
11. Approval config change does not shorten in-flight chains (FIN-06); deactivated middle level (FIN-06).
12. Quarter create/close by a scoped user → 403; closing a non-current quarter → 409 (FIN-07, FIN-09).
13. Appropriation edited then approved by the same user → refused (FIN-10).
14. Money writes in a closed fiscal period → 409 (FIN-12).
15. Retention candidate date and full anonymisation coverage (PRT-01, PRT-02).
16. Portal password lockout, TOTP replay, info-request field whitelist (PRT-04, PRT-05, PRT-07).
17. Custom-filter passport masking; query-builder view masking and filter-on-masked (RPT-01, RPT-02, RPT-04).
18. Reflection test: every JRXML field exists on the bean its controller passes (replaces the RPT-00 false positive).
19. Flyway against a DB built from `baseline/V000` does not re-run V001–V096 (DB-01).
20. Jar contains exactly `db/migration/V001..V147` (packaging guard).

Frontend:

1. Full interceptor chain: 401 → refresh 200 → retry succeeds, no logout (FE-02).
2. Proactive refresh fails with 503 → request sent with the old token, no logout (FE-03).
3. Wrong MFA code on /profile → error shown, still signed in (FE-01).
4. Logout clears local and session storage.
5. Country-scope interceptor adds `X-Country-Id` only on list GETs.
6. Accept-Language interceptor follows the language switch.
7. Specs for the big forms: student-add, quick-edit, data-import, wafar, payment batches, approval configs.
8. Language switch after destroying inquiries or chart-of-accounts → no errors (FE-04).
9. Cypress flows: login → add student → finance → approval → report PDF; portal login → change request.

---

## 5. Recommended fix order

1. **Now (security & money):** SEC-01, SEC-02, SEC-03, FIN-01, FIN-02, FIN-03, FIN-05, FIN-06, PRT-01.
2. **Before the next release:** FIN-04, FIN-07, FIN-09 to FIN-12, SEC-04 to SEC-06, RPT-01, RPT-02, PRT-02 to PRT-05, FE-01 to FE-03, ENV-01.
3. **Build hygiene (quick):** BLD-01 (regenerate the lock file), BLD-02 (commit `.zap/`), DOC-01 (restore `verify.ps1` and `MIGRATION_RUNBOOK.md` at minimum).
4. **Backlog:** all remaining Low items and the Section 4 test gaps.

## 6. Sign-off

| Test round | Date | Tester | Automated (Sec. 1 / 3.2) | Functional (3.3) pass / fail | Open Critical / High | Decision |
|---|---|---|---|---|---|---|
| Round 1 (this report) | 2026-09-28 | Claude Code | Unit: pass (with UTF-8 locale); integration: not run | not run | 3 / 8 | Fix Critical and High first |
| Round 2 | | | | | | |
