#!/usr/bin/env bash
# Runs every check CI runs, plus the database-backed backend tests when a database
# is reachable. The Linux/macOS twin of verify.ps1; same switches.
#
#   ./verify.sh                  # everything
#   ./verify.sh --skip-database  # no SQL Server: backend unit tests only
#   ./verify.sh --skip-frontend  # backend only
#   ./verify.sh --skip-backend   # frontend only
set -uo pipefail

SKIP_DB=0; SKIP_FE=0; SKIP_BE=0
for arg in "$@"; do
  case "$arg" in
    --skip-database) SKIP_DB=1 ;;
    --skip-frontend) SKIP_FE=1 ;;
    --skip-backend)  SKIP_BE=1 ;;
    -h|--help) sed -n '2,8p' "$0"; exit 0 ;;
    *) echo "unknown option: $arg" >&2; exit 2 ;;
  esac
done

# ENV-01: Arabic file names need a UTF-8 locale in every JVM and node process.
export LANG=C.UTF-8 LC_ALL=C.UTF-8
ROOT="$(cd "$(dirname "$0")" && pwd)"
FAILED=()

step() {  # step <name> <dir> <command...>
  local name="$1" dir="$2"; shift 2
  echo; echo "=== $name"
  if (cd "$ROOT/$dir" && "$@"); then echo "--- $name: OK"; else echo "--- $name: FAILED"; FAILED+=("$name"); fi
}

if [ "$SKIP_BE" -eq 0 ]; then
  if [ "$SKIP_DB" -eq 1 ]; then
    step "Backend unit tests" Backend ./mvnw -B -ntp test
  else
    step "Backend unit + integration tests" Backend ./mvnw -B -ntp test -DexcludedGroups=none
  fi
  step "Report template audit" Backend python3 scripts/audit_report_data.py
fi

if [ "$SKIP_FE" -eq 0 ]; then
  step "Frontend install (npm ci)" Frontend npm ci
  step "Translation keys" Frontend npm run validate-i18n
  step "Menu routes" Frontend npm run validate-menu
  step "Reload streams" Frontend npm run validate-reload-streams
  step "Lint" Frontend npx ng lint --quiet
  step "Unit tests" Frontend npx ng test --watch=false --browsers=ChromeHeadlessWide
  step "Production build + hygiene" Frontend npm run build:prod
  step "Dependency advisories (high+)" Frontend npm audit --omit=dev --audit-level=high
fi

echo
if [ "${#FAILED[@]}" -eq 0 ]; then echo "verify: all checks passed"; exit 0; fi
echo "verify: FAILED: ${FAILED[*]}"; exit 1
