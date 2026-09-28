# Deployment security checklist

What a production deployment has to set up beyond `SPRING_PROFILES_ACTIVE=prod`. Every variable
named here is listed with its defaults in [`ENVIRONMENT.md`](ENVIRONMENT.md).

## 1. Required configuration

The prod profile has no fallbacks. Startup fails until these are set:

- **Database:** `MOHE_DATASOURCE_URL`, `MOHE_DATASOURCE_USERNAME`, `MOHE_DATASOURCE_PASSWORD`. Use the
  least-privilege application login from V090, not `sa`. Flyway needs a login with DDL rights: set
  `SPRING_FLYWAY_USER` / `SPRING_FLYWAY_PASSWORD` (see [`MIGRATION_RUNBOOK.md`](MIGRATION_RUNBOOK.md)).
- **Secrets:** `JWT_SECRET` (at least 32 bytes), `MOHE_FILE_ENCRYPTION_KEY` (base64, 32 bytes) and
  `MOHE_ACTUATOR_PASSWORD` are required. Also set `MOHE_MFA_KEY`, which encrypts the stored TOTP
  secrets; it has an empty default, so startup does not enforce it. Keep all of them in a secrets
  manager, never in a file in the repository. To rotate, set the old value as `JWT_SECRET_PREVIOUS`, `MOHE_MFA_KEY_PREVIOUS` or
  `MOHE_FILE_ENCRYPTION_KEYS` while the new one takes over.
- **Browser origin:** set `CORS_ALLOWED_ORIGINS` when the UI is served from another origin. Leave
  `CORS_ALLOW_DEV_PATTERNS` at its prod value; the application refuses dev patterns outside dev.

## 2. Operating system

- **UTF-8 locale:** run the service with `LANG=C.UTF-8` (or another UTF-8 locale). The JVM encodes
  file names with the OS locale, and under POSIX/C every Arabic document, import or export name fails
  with `InvalidPathException` (ENV-01). On startup `FileNameEncodingCheck` logs an error when the
  locale is wrong.
- **Writable directories:** uploads, `archive/audit` and `archive/retention`
  (`MOHE_AUDIT_ARCHIVE_DIR`, `MOHE_RETENTION_REPORT_DIR`). Only the service account may read them.
  Audit archives are redacted and, when a file key is configured, encrypted (`.ndjson.gz.enc`).

## 3. Reverse proxy

- Terminate TLS at the proxy and forward to port 8082, context path `/mohe`.
- **`MOHE_TRUSTED_PROXIES`** (SEC-04) is a regex of proxy IPs whose `X-Forwarded-*` headers Tomcat
  honours, e.g. `10\.0\.0\.5`. It is empty by default, and empty trusts **no** peer. With it unset
  behind a TLS proxy, `X-Forwarded-Proto` is ignored and the `Strict-Transport-Security` header is not
  sent. Never set it to a whole private range: any client inside that range could then spoof its IP
  past the login throttle, the admin IP allowlist and the audit log.
- `MOHE_TRUST_FORWARDED_FOR` stays `false` unless the proxy is the only path to the service.
- Serve the frontend build (`Frontend/dist/mof-is/browser`) from the same origin under `/`, and
  forward `/mohe` to the backend.

## 4. Database security

- **Row-level security:** V031/V062 enable the student tenant policy. The staff-table policy created by
  V102 ships with `STATE = OFF`. Switch it on with `docs/scripts/adhoc/enable_staff_country_policy.sql`
  after reading V102 section 5, and test sign-in immediately afterwards.
- **Query builder:** in prod, `MOHE_QUERY_ENGINE_URL` / `_USERNAME` / `_PASSWORD` should point at the
  read-only `mohe_query_reader` login (V025).
- **Backups:** encrypt them and test restore quarterly (`scripts/dr_drill.ps1`).

## 5. Application hardening switches

Leave these at their defaults unless there is a documented reason: `MOHE_MFA_ENFORCE=true`,
`JWT_SESSION_VALIDATION=true`, `MOHE_SESSION_BINDING=true`, `UMS_FOUR_EYES_ENABLED=true`,
`MOHE_RETENTION_DRY_RUN=true`. Turn off dry-run only after a reviewed dry-run report:
anonymisation deletes files and cannot be undone. Also `MOHE_OUTBOUND_ALLOW_PRIVATE=false`,
`MOHE_PORTAL_OTP_LOG_TO_CONSOLE=false`, and the student-portal test account off
(`MOHE_PORTAL_INIT_TEST_ACCOUNT` unset).

## 6. Transparent Data Encryption

`docs/scripts/adhoc/enable_tde.sql` creates the master key, `MOHE_TDE_Certificate` and the database
encryption key, then turns TDE on. Before running it:

1. Replace the placeholder password in the script (never commit the value).
2. Run it once per server, as a DBA, in a maintenance window. The first encryption scan runs in the
   background.
3. **Immediately back up the certificate and its private key** with the command the script prints,
   and store them apart from the database backups. Without them no backup of an encrypted database
   can be restored anywhere else.
4. Check it: `SELECT DB_NAME(database_id), encryption_state FROM sys.dm_database_encryption_keys;`
   (3 = encrypted).

It needs Enterprise edition on SQL Server 2016/2017; Standard edition supports TDE from SQL Server
2019.

## 7. Security scanning (OWASP ZAP)

The `zap-baseline` job in `.github/workflows/ci.yml` is manual. Start it with **Run workflow** and a
`zap_target` URL of a **dev or staging** host, never production. To run it locally, use
`Backend/scripts/zap_baseline.ps1` (Docker) or `zap_local.ps1` (desktop ZAP).

- It runs once anonymously, then again with the `ZAP_BEARER_TOKEN` repository secret (a staff JWT for
  that host) sent on every request.
- `.zap/check_high.py` fails the run on any High alert. Reports are kept as build artefacts.
- To silence a false positive, add a line to `.zap/rules.tsv`: `<pluginId>\tIGNORE\t<name — reason>`.
  Every IGNORE needs a reason a reviewer can check. The list starts empty.
- Triage: fix Highs before release. Record Medium and below in the release notes with a decision.

## 8. Other scans in CI

SpotBugs with FindSecBugs (high-confidence findings fail), OWASP Dependency-Check (CVSS ≥ 7 fails;
assessed exceptions in `dependency-check-suppressions.xml`), CodeQL, `npm audit --audit-level=high`,
and CycloneDX SBOMs for both halves. Assessments of the dependency advisories:
[`DEPENDENCY_ADVISORIES.md`](DEPENDENCY_ADVISORIES.md).
