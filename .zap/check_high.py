#!/usr/bin/env python3
"""Fail when a ZAP JSON report holds any High-risk alert.

Used by the zap-baseline job in .github/workflows/ci.yml and by
Backend/scripts/zap_baseline.ps1 / zap_local.ps1:

    python .zap/check_high.py zap-out/report-*.json

Reads ZAP's "traditional-json" report (site[].alerts[] with riskcode 0-3).
Alerts the rules.tsv ignore list turned into false positives are already out of
the report. Exit code: 0 = no High alerts, 1 = at least one, 2 = bad input.
"""
import glob
import json
import sys

HIGH = "3"


def high_alerts(path):
    with open(path, encoding="utf-8") as fh:
        report = json.load(fh)
    found = []
    for site in report.get("site", []) or []:
        for alert in site.get("alerts", []) or []:
            if str(alert.get("riskcode")) == HIGH:
                found.append((site.get("@name", "?"), alert.get("pluginid", "?"),
                              alert.get("name") or alert.get("alert", "?"),
                              len(alert.get("instances", []) or [])))
    return found


def main(argv):
    paths = [p for arg in argv for p in (glob.glob(arg) or [arg])]
    if not paths:
        print("check_high: no report files given", file=sys.stderr)
        return 2
    total = 0
    for path in paths:
        try:
            alerts = high_alerts(path)
        except (OSError, ValueError) as exc:
            print(f"check_high: cannot read {path}: {exc}", file=sys.stderr)
            return 2
        for site, plugin, name, count in alerts:
            print(f"HIGH  {path}: [{plugin}] {name} ({count} instance(s)) on {site}")
        total += len(alerts)
    print(f"check_high: {total} High alert(s) in {len(paths)} report(s)")
    return 1 if total else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
