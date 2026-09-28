# Dependency advisories

Assessed advisories that remain on the tree, and why each is accepted. Backend (Maven) assessments
live next to the scanner that reports them, in [`../dependency-check-suppressions.xml`](../dependency-check-suppressions.xml).
One `<suppress>` per advisory, each with its reason and a review date. This file holds the frontend
(npm) ones.

CI fails on anything rated **high or critical** (`npm audit --omit=dev --audit-level=high`) and on
backend CVSS ≥ 7 (Dependency-Check). The CI step "Dependency advisories (report all)" prints
everything below that threshold without failing.

## Frontend (`npm audit --omit=dev`, 28 Sep 2026)

| Package | Advisory | Severity | Status | Assessment |
|---|---|---|---|---|
| `quill` 2.0.3 | [GHSA-v3m3-f69x-jf25](https://github.com/advisories/GHSA-v3m3-f69x-jf25): XSS through the HTML export | low | **accepted** | The only fix npm offers is a *downgrade* to 2.0.2. Quill is the notification editor, and its HTML goes only to the backend. `EmailService.sanitize` (jsoup, relaxed safelist) strips scripts, event handlers and `javascript:` URLs before anything is sent, so the exported HTML is never trusted (security plan O-13). Re-check when quill releases a fix. |
| `uuid` < 11.1.1 (via `exceljs`, `vis-network`, `sockjs`) | [GHSA-w5hq-g745-h8pq](https://github.com/advisories/GHSA-w5hq-g745-h8pq): missing buffer bounds check in v3/v5/v6 when `buf` is given | moderate | **resolved in the dependency tree (FE-10)** | `package.json` `overrides` pins `uuid` to `^11.1.1`, and `npm audit` no longer reports it. The browser bundle still loads exceljs's prebuilt `dist/exceljs.min.js`, which carries its own copy of uuid 8. That copy is only called as `v4()` with no buffer, and the advisory concerns v3/v5/v6 with a buffer, so the shipped code is not exposed. |

## Backend

See `dependency-check-suppressions.xml`: JasperReports CVE-2025-10492 (no client-supplied serialised
data; upgrading to 7.x is tracked separately), a false-positive Jakarta Mail CPE match, and Spring
Framework 6.2.19 advisories with no released fix (review date 2026-12-01).

## Review

Re-run `npm audit --omit=dev` and `mvnw dependency-check:check` monthly and before every release.
Update this table and the suppression file together, and give every accepted item a reason a
reviewer can check.
