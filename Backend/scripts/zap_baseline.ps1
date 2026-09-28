<#
.SYNOPSIS
  OWASP ZAP baseline scan of a running MOHE instance from a developer machine (security plan O-37).

.DESCRIPTION
  Needs Docker. Runs the ZAP baseline (passive spider + passive rules) against the
  target, unauthenticated, and again with a bearer token when one is given; writes
  HTML and JSON reports to Backend/target/zap and fails on any High alert
  (.zap/check_high.py). Same procedure as the zap-baseline job in ci.yml.

.EXAMPLE
  .\scripts\zap_baseline.ps1 -Target http://host.docker.internal:8082/mohe
  .\scripts\zap_baseline.ps1 -Target https://staging.example/mohe -BearerToken (Get-Content token.txt)
#>
param(
    [Parameter(Mandatory = $true)] [string]$Target,
    [string]$BearerToken = '',
    [string]$Image = 'ghcr.io/zaproxy/zaproxy:stable'
)

$ErrorActionPreference = 'Stop'
$repo = Resolve-Path (Join-Path $PSScriptRoot '..\..')
$out = Join-Path $PSScriptRoot '..\target\zap'
New-Item -ItemType Directory -Force $out | Out-Null
Copy-Item (Join-Path $repo '.zap\rules.tsv') (Join-Path $out 'rules.tsv') -Force

function Run-Baseline([string]$suffix, [string]$extra) {
    $args = @('run', '--rm', '-v', "$((Resolve-Path $out).Path):/zap/wrk:rw", $Image,
              'zap-baseline.py', '-t', $Target, '-c', 'rules.tsv', '-I',
              '-r', "report-$suffix.html", '-J', "report-$suffix.json")
    if ($extra) { $args += @('-z', $extra) }
    Write-Host "ZAP baseline ($suffix) against $Target"
    & docker @args
    # zap-baseline exits 2 on warnings (ignored by -I) and 1 on FAIL rules; the risk gate is below.
}

Run-Baseline 'anonymous' ''
if ($BearerToken) {
    $replacer = '-config replacer.full_list(0).description=auth ' +
                '-config replacer.full_list(0).enabled=true ' +
                '-config replacer.full_list(0).matchtype=REQ_HEADER ' +
                '-config replacer.full_list(0).matchstr=Authorization ' +
                '-config replacer.full_list(0).regex=false ' +
                "-config replacer.full_list(0).replacement=Bearer%20$BearerToken"
    Run-Baseline 'authenticated' $replacer
}

$reports = Get-ChildItem $out -Filter 'report-*.json' | ForEach-Object { $_.FullName }
python (Join-Path $repo '.zap\check_high.py') @reports
exit $LASTEXITCODE
