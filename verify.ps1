<#
.SYNOPSIS
  Runs every check CI runs, plus the database-backed backend tests when a database
  is reachable. The Windows twin of verify.sh; same switches.
.EXAMPLE
  .\verify.ps1                  # everything
  .\verify.ps1 -SkipDatabase    # no SQL Server: backend unit tests only
  .\verify.ps1 -SkipFrontend    # backend only
  .\verify.ps1 -SkipBackend     # frontend only
#>
param(
    [switch]$SkipDatabase,
    [switch]$SkipFrontend,
    [switch]$SkipBackend
)

$ErrorActionPreference = 'Continue'
$root = $PSScriptRoot
$failed = New-Object System.Collections.Generic.List[string]

function Step([string]$Name, [string]$Dir, [scriptblock]$Command) {
    Write-Host ""
    Write-Host "=== $Name"
    Push-Location (Join-Path $root $Dir)
    try {
        & $Command
        if ($LASTEXITCODE -eq 0) { Write-Host "--- ${Name}: OK" }
        else { Write-Host "--- ${Name}: FAILED" -ForegroundColor Red; $failed.Add($Name) }
    } finally { Pop-Location }
}

if (-not $SkipBackend) {
    if ($SkipDatabase) {
        Step 'Backend unit tests' 'Backend' { .\mvnw.cmd -B -ntp test }
    } else {
        Step 'Backend unit + integration tests' 'Backend' { .\mvnw.cmd -B -ntp test -DexcludedGroups=none }
    }
    Step 'Report template audit' 'Backend' { python scripts/audit_report_data.py }
}

if (-not $SkipFrontend) {
    Step 'Frontend install (npm ci)' 'Frontend' { npm ci }
    Step 'Translation keys' 'Frontend' { npm run validate-i18n }
    Step 'Menu routes' 'Frontend' { npm run validate-menu }
    Step 'Reload streams' 'Frontend' { npm run validate-reload-streams }
    Step 'Lint' 'Frontend' { npx ng lint --quiet }
    Step 'Unit tests' 'Frontend' { npx ng test --watch=false --browsers=ChromeHeadlessWide }
    Step 'Production build + hygiene' 'Frontend' { npm run build:prod }
    Step 'Dependency advisories (high+)' 'Frontend' { npm audit --omit=dev --audit-level=high }
}

Write-Host ""
if ($failed.Count -eq 0) { Write-Host 'verify: all checks passed' -ForegroundColor Green; exit 0 }
Write-Host ("verify: FAILED: " + ($failed -join ', ')) -ForegroundColor Red
exit 1
