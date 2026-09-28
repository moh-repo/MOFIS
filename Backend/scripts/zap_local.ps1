<#
.SYNOPSIS
  OWASP ZAP baseline scan of a running MOHE instance with the ZAP desktop install, no Docker (security plan O-37).
.DESCRIPTION
  Runs the automation plan .zap/baseline-local.yaml (spider, every operation of
  the OpenAPI description, passive rules) against the target, writes
  report-<pass>.html/.json to Backend/target/zap, applies the ignore list from
  .zap/rules.tsv, and fails on any High alert (.zap/check_high.py) - the same
  gate as the CI job. Without a token the pass is "anonymous"; with -BearerToken
  every request carries the token and the pass is "authenticated". Docker users
  can run scripts/zap_baseline.ps1 instead, which is the CI procedure.
.PARAMETER Target       Base URL of the running instance (default http://localhost:8082/mohe).
.PARAMETER ZapHome      Folder holding zap.bat (default: the winget install location).
.PARAMETER BearerToken  A staff JWT for the authenticated pass. Use a throwaway
                        account with few rights: the OpenAPI import calls every
                        operation, writes included, with generated values.
.EXAMPLE
  .\scripts\zap_local.ps1
  .\scripts\zap_local.ps1 -Target https://staging.example/mohe
  .\scripts\zap_local.ps1 -BearerToken (Get-Content token.txt)
#>
param(
    [string]$Target = 'http://localhost:8082/mohe',
    [string]$ZapHome = '',
    [string]$BearerToken = ''
)
$ErrorActionPreference = 'Stop'
$repo = Resolve-Path (Join-Path $PSScriptRoot '..\..')
$out = Join-Path $PSScriptRoot '..\target\zap'
New-Item -ItemType Directory -Force $out | Out-Null
$out = (Resolve-Path $out).Path
$suffix = if ($BearerToken) { 'authenticated' } else { 'anonymous' }

if (-not $ZapHome) {
    $candidates = @("$env:ProgramFiles\ZAP\Zed Attack Proxy", "${env:ProgramFiles(x86)}\ZAP\Zed Attack Proxy",
                    "$env:LOCALAPPDATA\Programs\ZAP\Zed Attack Proxy", "$env:ProgramFiles\OWASP\Zed Attack Proxy")
    $ZapHome = $candidates | Where-Object { Test-Path (Join-Path $_ 'zap.bat') } | Select-Object -First 1
    if (-not $ZapHome) { throw "zap.bat not found; install ZAP (winget install ZAP.ZAP) or pass -ZapHome" }
}

# The plan with the target, the output directory and the pass name filled in.
$plan = Get-Content (Join-Path $repo '.zap\baseline-local.yaml') -Raw
$plan = $plan.Replace('TARGET: "http://localhost:8082/mohe"', "TARGET: `"$Target`"")
$plan = $plan.Replace('OUT: "zap-out"', "OUT: `"$($out.Replace('\', '/'))`"")
$plan = $plan.Replace('report-anonymous', "report-$suffix").Replace('(anonymous)', "($suffix)")

# Authenticated pass: the replacer add-on puts the token on every request ZAP sends.
if ($BearerToken) {
    $replacer = "  - type: replacer`n    rules:`n      - description: auth`n        url: ''`n" +
                "        matchType: req_header`n        matchString: Authorization`n" +
                "        replacementString: 'Bearer $BearerToken'`n        tokenProcessing: false`n`n"
    $idx = $plan.IndexOf("  - type: passiveScan-config")
    if ($idx -lt 0) { throw "baseline-local.yaml has no passiveScan-config job" }
    $plan = $plan.Substring(0, $idx) + $replacer + $plan.Substring($idx)
}

# The ignore list from rules.tsv as alertFilter rules (the desktop ZAP has no -c);
# alertFilter must run before the reports, so it goes ahead of the first report job.
$ignored = @()
foreach ($line in Get-Content (Join-Path $repo '.zap\rules.tsv')) {
    if ($line -match '^\s*#' -or $line -notmatch '\t') { continue }
    $parts = $line -split "`t"
    if ($parts.Count -ge 2 -and $parts[1].Trim() -eq 'IGNORE') { $ignored += $parts[0].Trim() }
}
if ($ignored.Count -gt 0) {
    $filters = "  - type: alertFilter`n    parameters:`n      deleteGlobalAlerts: true`n    alertFilters:`n"
    foreach ($id in $ignored) { $filters += "      - ruleId: $id`n        newRisk: 'False Positive'`n" }
    $idx = $plan.IndexOf("  - type: report")
    if ($idx -lt 0) { throw "baseline-local.yaml has no report job" }
    $plan = $plan.Substring(0, $idx) + $filters + $plan.Substring($idx)
}
$planFile = Join-Path $out "baseline-local.$suffix.yaml"
$plan | Out-File -Encoding utf8 $planFile

Write-Host "ZAP baseline ($suffix) against $Target using $ZapHome"
Push-Location $ZapHome
try {
    & (Join-Path $ZapHome 'zap.bat') -cmd -silent -dir (Join-Path $out 'home') -autorun $planFile 2>&1 | Tee-Object -FilePath (Join-Path $out "zap-$suffix.log")
} finally { Pop-Location }
if ($BearerToken) { Remove-Item $planFile -Force }   # the resolved plan holds the token

$json = @(Get-ChildItem $out -Filter "report-$suffix.json" | ForEach-Object FullName)
if ($json.Count -eq 0) { throw "ZAP wrote no JSON report; see $out\zap-$suffix.log" }
& python (Join-Path $repo '.zap\check_high.py') @json
if ($LASTEXITCODE -ne 0) { exit 1 }
Write-Host "Reports: $out"
