#!/usr/bin/env python3
"""
Static audit of the report data path: JRXML field -> Java bean property -> view column.

Finds the two defect classes users actually report:

  MISSING_PROPERTY  a <field name="x"> the template prints but no bean supplies.
                    Jasper does not fail on this at export time in every case,
                    and where it does the failure is a 500 with no hint which
                    field is at fault - so these show up as blank cells.

  RAW_ID            a bean/view exposes a *_ID / *Id column with no sibling
                    *_NAME / *Name, i.e. the report can only print the foreign
                    key, never the Arabic/English lookup name.

Resolution of a template to its bean is by controller: each report controller
calls jasperReportService.generatePdf("<template>", params, <data>), and the
data element type is the bean. That mapping is parsed from the controllers so
the audit follows real wiring instead of a hand-maintained list.

  INJECTABLE_QUERY  a template that runs SQL of its own (<queryString>) or pastes
                    a parameter into it ($P!{}, $X{}). Every template here is
                    filled from a bean collection, so any of the three is a
                    change of data path (security plan O-12). This one fails
                    the run; the two above are informational.

Usage:
    python scripts/audit_report_data.py                 # human-readable report
    python scripts/audit_report_data.py --markdown      # docs/report-data-audit.md body
"""

from __future__ import annotations

import argparse
import glob
import os
import re
import sys
from collections import defaultdict

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
REPORTS = os.path.join(ROOT, "src", "main", "resources", "reports")
JAVA = os.path.join(ROOT, "src", "main", "java", "com", "dilaw", "mohe")

# Parameters, not data fields - templates get these from ReportParamBuilder.
PARAM_LIKE = re.compile(r"^(TITLE_|SIG_|LBL_|COL_|REPORT_|FOOTER_|IS_RTL|LANG|LOGO_|PRINTED_)")


def template_fields() -> dict[str, list[str]]:
    """<field name="..."> declared by each template."""
    out = {}
    for path in sorted(glob.glob(os.path.join(REPORTS, "*.jrxml"))):
        src = open(path, encoding="utf-8").read()
        name = os.path.basename(path)[: -len(".jrxml")]
        out[name] = re.findall(r'<field\s+name="([^"]+)"', src)
    return out


# Field declarations. Access modifier is optional on purpose: several report
# DTOs declare package-private fields (Lombok still generates getters, and
# Jasper still reads them), so requiring "private" silently under-reports.
FIELD_DECL = re.compile(
    r"^[ \t]{0,8}"
    r"(?:(?:public|protected|private|static|final|transient|volatile)[ \t]+)*"
    r"(?!(?:return|new|if|for|while|throw|else)\b)"
    r"(?P<type>[A-Z][\w.]*(?:<[^;>]*>)?(?:\[\])?|[a-z][\w.]*\.[A-Z][\w.]*(?:<[^;>]*>)?|int|long|double|float|boolean|char|byte|short)[ \t]+"
    r"(?P<name>[a-z]\w*)[ \t]*(?:=[^;]*)?;[ \t]*$"
)
NOT_FIELDS = {"serialVersionUID", "log", "logger"}

# Tokens too generic to tell one lookup from another.
GENERIC_TOKENS = {"id", "student", "study", "mohe", "ref", "in", "all", "no", "type"}


def _tokens(name: str) -> set[str]:
    """camelCase / PascalCase identifier -> lowercase word set."""
    return {w.lower() for w in re.findall(r"[A-Z]+(?![a-z])|[A-Z][a-z]*|[a-z]+", name)}


GETTER_DECL = re.compile(
    r"\bpublic\s+(?!class\b|record\b)[\w.<>\[\]]+\s+(get|is)([A-Z]\w*)\s*\(\s*\)", re.M
)


def _getter_properties(src: str) -> list[str]:
    """
    Bean property names this class exposes through getters.

    JavaBeans decapitalisation, which is what Jasper applies: `getChartIndex` ->
    `chartIndex`, `getH0` -> `h0`, `isActive` -> `active`. A name whose first two
    letters are both capitals keeps them (`getURL` -> `URL`), per the same rule.
    """
    names = []
    for _, raw in GETTER_DECL.findall(src):
        if len(raw) > 1 and raw[1].isupper():
            names.append(raw)
        else:
            names.append(raw[0].lower() + raw[1:])
    return names


def _record_components(src: str) -> list[str]:
    m = re.search(r"\brecord\s+\w+\s*\(([^)]*)\)", src, re.S)
    if not m:
        return []
    return re.findall(r"[\w.<>\[\], ]*?\b(\w+)\s*(?:,|$)", m.group(1).strip())


def java_types() -> dict[str, dict]:
    """
    Every model/dto class: its declared properties and, for @Table-mapped
    classes, the SQL table/view it reads and each property's column.

    `report/chart` is here because a report's rows need not be an entity or a
    DTO. chart-report.jrxml is filled from ChartReportRow, a record built at
    request time by ChartReportRows.flatten() — nothing reads a table for it.
    Scanning only model/ and dto/ left that type unknown, so the template
    resolved to no bean and was reported as MISSING_PROPERTY: a real template
    with a real row type, failing the audit for living in the wrong folder.
    """
    types = {}
    for folder in ("model", "dto", os.path.join("report", "chart")):
        for path in glob.glob(os.path.join(JAVA, folder, "*.java")):
            src = open(path, encoding="utf-8").read()
            cls = os.path.basename(path)[: -len(".java")]
            table = re.search(r'@Table\(\s*name\s*=\s*"([^"]+)"', src)

            # property name -> column name (column empty for unmapped DTO fields).
            # Scanned line by line so the @Column that belongs to a field is the
            # last one seen before it, which is how the sources are written.
            props: dict[str, str] = {}
            ptypes: dict[str, str] = {}
            pending_col = ""
            for line in src.splitlines():
                cm = re.search(r'@Column\(\s*name\s*=\s*"([^"]+)"', line)
                if cm:
                    pending_col = cm.group(1)
                fm = FIELD_DECL.match(line)
                if fm and fm.group("name") not in NOT_FIELDS:
                    props[fm.group("name")] = pending_col
                    ptypes[fm.group("name")] = fm.group("type")
                    pending_col = ""
            for comp in _record_components(src):
                props.setdefault(comp, "")
            # Jasper resolves a bean field through its *getter*, not its backing
            # field, so a property computed on the way out is as real to the
            # template as a stored one. ChartReportRow is the case that forced
            # this: hasData, h0-h6 and c0-c6 have no field behind them — they read
            # off two arrays — and were reported as sixteen missing properties on
            # a template that renders correctly.
            for name in _getter_properties(src):
                props.setdefault(name, "")

            types[cls] = {
                "table": table.group(1) if table else None,
                "props": props,
                "ptypes": ptypes,
                "immutable": "@Immutable" in src,
                "path": os.path.relpath(path, ROOT),
            }

            # Nested row beans (e.g. QuarterlyFinancialReportDto.EntityFinancialSummary
            # is what quarterly-financial.jrxml is actually filled with) are indexed
            # under their simple name, which is how call sites refer to them.
            for nm in re.finditer(
                r"^[ \t]+(?:public\s+|protected\s+|private\s+)?static\s+class\s+(\w+)[^{]*\{",
                src, re.M,
            ):
                body = _balanced_body(src, nm.end() - 1)
                nprops: dict[str, str] = {}
                ntypes: dict[str, str] = {}
                for line in body.splitlines():
                    # Nested-class fields sit a level deeper than FIELD_DECL's
                    # indent budget allows, so match against the dedented line.
                    fm = FIELD_DECL.match(line.strip())
                    if fm and fm.group("name") not in NOT_FIELDS:
                        nprops[fm.group("name")] = ""
                        ntypes[fm.group("name")] = fm.group("type")
                if nprops:
                    types.setdefault(nm.group(1), {
                        "table": None, "props": nprops, "ptypes": ntypes,
                        "immutable": False, "path": os.path.relpath(path, ROOT),
                    })
    return types


def _balanced_body(src: str, open_brace: int) -> str:
    """Source between a class's { and its matching }."""
    depth = 0
    for i in range(open_brace, len(src)):
        if src[i] == "{":
            depth += 1
        elif src[i] == "}":
            depth -= 1
            if depth == 0:
                return src[open_brace + 1:i]
    return src[open_brace + 1:]


# Two ways a controller hands a bean collection to Jasper:
#   jasperReportService.generatePdf("<template>", params, <data>);
#   reportExports.render("<template>", params, <data>, "<file>", disposition);
# The second was introduced when PDF and Excel export were unified behind
# ReportExports; without it this audit resolved only 9 of 45 templates and
# reported the other 36 as MISSING_PROPERTY, which looked like 36 broken
# templates rather than one unrecognised call shape.
CALL_SITE_PATTERNS = (
    re.compile(r'generatePdf\(\s*"([^"]+)"\s*,\s*\w+\s*(?:,\s*([^;]*?))?\)\s*;', re.S),
    re.compile(r'render\(\s*"([^"]+)"\s*,\s*\w+\s*,\s*((?:[^,()]|\([^()]*\))+?)\s*,', re.S),
)

# The same two calls, but naming the template through a constant rather than a
# literal:  private static final String TEMPLATE = "chart-report";
#           reportExports.render(TEMPLATE, params, rows, ...)
# ChartReportController is written that way, so the literal-only patterns above
# matched nothing and chart-report was reported as "(bean not resolved)" — an
# audit gap, not a broken template. The constant is resolved from the same file.
CONST_CALL_SITE_PATTERNS = (
    re.compile(r'generatePdf\(\s*([A-Z][A-Z0-9_]*)\s*,\s*\w+\s*(?:,\s*([^;]*?))?\)\s*;', re.S),
    re.compile(r'render\(\s*([A-Z][A-Z0-9_]*)\s*,\s*\w+\s*,\s*((?:[^,()]|\([^()]*\))+?)\s*,', re.S),
)


def _string_constants(src: str) -> dict[str, str]:
    """`static final String NAME = "value";` declared in this source."""
    return {
        m.group(1): m.group(2)
        for m in re.finditer(
            r'static\s+final\s+String\s+([A-Z][A-Z0-9_]*)\s*=\s*"([^"]+)"\s*;', src
        )
    }


# `List<Row> name(` / `java.util.List<pkg.Row> name(`: what a method hands back.
METHOD_RETURNS = re.compile(
    r"\b(?:java\.util\.)?(?:List|Collection)<\s*([A-Za-z0-9_.]+)\s*>\s+([a-z]\w*)\s*\("
)

# The data argument is a call on some receiver: `service.reportRows(items, lang)`.
METHOD_CALL = re.compile(r"^\s*(?:[a-z]\w*\s*\.\s*)+([a-z]\w*)\s*\(")


def _method_return_types(sources: dict[str, str]) -> dict[str, set[str]]:
    """method name -> element types of the List/Collection it is declared to return."""
    out: dict[str, set[str]] = defaultdict(set)
    for src in sources.values():
        for m in METHOD_RETURNS.finditer(src):
            out[m.group(2)].add(m.group(1).split(".")[-1])
    return out


def template_beans() -> dict[str, list[str]]:
    """template name -> bean class names, parsed from the Jasper call sites."""
    mapping: dict[str, set[str]] = defaultdict(set)
    sources = {
        path: open(path, encoding="utf-8").read()
        for path in glob.glob(os.path.join(JAVA, "**", "*.java"), recursive=True)
    }
    returns = _method_return_types(sources)
    for path, src in sources.items():
        if "generatePdf(" not in src and ".render(" not in src:
            continue
        constants = _string_constants(src)
        matches = [(m, False) for pat in CALL_SITE_PATTERNS for m in pat.finditer(src)]
        matches += [(m, True) for pat in CONST_CALL_SITE_PATTERNS for m in pat.finditer(src)]
        for m, via_constant in matches:
            template, data = m.group(1), (m.group(2) or "").strip()
            if via_constant:
                template = constants.get(template, "")
                if not template:
                    continue        # a constant declared elsewhere; nothing to resolve
            if not data:
                continue
            # RPT-00: the data is what a method returns, e.g. growth-analysis's
            # `service.reportRows(items, lang)`. Its declared return type is the
            # bean; the arguments are only its input. Reading the arguments'
            # types instead resolved the template to GrowthAnalysis (the type of
            # `items`) and reported two fields that GrowthAnalysisReportRow
            # does supply as missing - a false positive on a working template.
            call = METHOD_CALL.match(data)
            if call and returns.get(call.group(1)):
                mapping[template].update(returns[call.group(1)])
                continue
            # Types named in the surrounding method give us the element type.
            for cand in re.findall(r"\b([A-Z][A-Za-z0-9]+)\b", data):
                mapping[template].add(cand)
            # List.of(x) / a local variable: fall back to declared types nearby.
            for var in re.findall(r"\b([a-z]\w*)\b", data):
                decl = re.search(
                    rf"(?:List|Collection)<\s*([A-Za-z0-9_.]+)\s*>\s+{var}\b", src
                )
                if decl:
                    mapping[template].add(decl.group(1).split(".")[-1])
                else:
                    decl2 = re.search(rf"\b([A-Z][A-Za-z0-9]+)\s+{var}\s*=", src)
                    if decl2:
                        mapping[template].add(decl2.group(1))
    return {k: sorted(v) for k, v in mapping.items()}


def audit() -> tuple[list[dict], list[dict], dict]:
    fields = template_fields()
    types = java_types()
    beans = template_beans()

    missing: list[dict] = []
    for template, flds in sorted(fields.items()):
        data_fields = [f for f in flds if not PARAM_LIKE.match(f)]
        if not data_fields:
            continue
        candidates = [c for c in beans.get(template, []) if c in types]
        if not candidates:
            missing.append({
                "template": template, "bean": "?", "field": "(bean not resolved)",
                "note": "no generatePdf() call site resolved to a known model/dto",
            })
            continue
        # A field is satisfied if ANY candidate bean declares it.
        for f in data_fields:
            if not any(f in types[c]["props"] for c in candidates):
                missing.append({
                    "template": template, "bean": "/".join(candidates), "field": f,
                    "note": "template prints this field but no bound bean declares it",
                })

    # Only report views matter here. A base table such as STUDENT_DATA is
    # *supposed* to store bare foreign keys; the defect is a view that a report
    # reads from exposing an id the template can only print as a number.
    report_beans = {b for t in fields for b in beans.get(t, [])}

    # Which templates print which bean, so a bare FK can be reported only when a
    # template actually puts it on paper.
    printed_by: dict[str, set[str]] = defaultdict(set)
    for template, blist in beans.items():
        for b in blist:
            for f in fields.get(template, []):
                printed_by[b].add(f)

    raw_ids: list[dict] = []
    for cls, info in sorted(types.items()):
        table = info["table"]
        if not table:
            continue
        is_view = bool(re.match(r"(?i)^(V_|VW_|Q_)", table))
        if not (is_view or cls in report_beans):
            continue
        props = info["props"]
        ptypes = info["ptypes"]
        # Name-bearing properties on the same class, tokenised for matching.
        name_tokens: list[set[str]] = [
            _tokens(p) for p in props
            if re.search(r"(?:Name|Label|Desc|Title)(?:Ar|En)?$", p)
        ]
        class_stem = _tokens(cls)

        for prop in sorted(props):
            if not re.search(r"(?:^|[a-z])Id$", prop):
                continue
            # A String "id" is a human-entered reference (cheque number, form
            # number), not a foreign key into a lookup table.
            if ptypes.get(prop) == "String":
                continue
            toks = _tokens(prop) - GENERIC_TOKENS
            if not toks:
                continue                      # e.g. plain "studentId" - the key
            if toks & class_stem:
                continue                      # the view's own surrogate key
            if prop.startswith("ref"):
                continue                      # cross-record pointer, not a lookup
            # Satisfied when some name property shares a distinguishing token
            # (studentGenderId <-> genderNameAr, amountCurrencyId <-> currencyNameEn).
            if any(toks & nt for nt in name_tokens):
                continue
            raw_ids.append({
                "class": cls, "table": info["table"], "prop": prop,
                "path": info["path"],
                # Only a printed FK is a user-visible defect; an unused view
                # column is just dead weight.
                "printed": prop in printed_by.get(cls, set()),
            })

    stats = {
        "templates": len(fields),
        "types": len(types),
        "resolved": sum(1 for t in fields if beans.get(t)),
    }
    return missing, raw_ids, stats


INJECTABLE = (("$P!{", "raw parameter substitution $P!{}"),
              ("$X{", "query clause function $X{}"),
              ("<queryString", "template runs its own query"))


def injectable_queries() -> list[str]:
    """Templates that query a connection or paste a parameter into SQL (O-12)."""
    problems = []
    for path in sorted(glob.glob(os.path.join(REPORTS, "**", "*.jrxml"), recursive=True)):
        with open(path, encoding="utf-8") as fh:
            for no, line in enumerate(fh, 1):
                for token, why in INJECTABLE:
                    if token in line:
                        problems.append(f"{os.path.relpath(path, REPORTS)}:{no}: {why}")
    return problems


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--markdown", action="store_true")
    ap.add_argument(
        "--check",
        action="store_true",
        help="exit 1 on a template field no bean supplies, or on a printed raw "
             "FK. Without it this script only reports, which is how V106's "
             "removal of template_status left report-templates.jrxml printing a "
             "field that no longer existed and verify.ps1 still went green.",
    )
    args = ap.parse_args()

    injectable = injectable_queries()
    if injectable:
        print(f"=== INJECTABLE_QUERY ({len(injectable)}) "
              "- templates are bean-fed; a query in a template is injectable with $P!{}", file=sys.stderr)
        for p in injectable:
            print(f"  {p}", file=sys.stderr)
        return 1

    missing, raw_ids, stats = audit()

    if args.markdown:
        print("| Template | Bean | Field | Finding |")
        print("|---|---|---|---|")
        for r in missing:
            print(f"| `{r['template']}` | `{r['bean']}` | `{r['field']}` | {r['note']} |")
        print()
        print("| Class | View / table | FK property without a name sibling |")
        print("|---|---|---|")
        for r in raw_ids:
            print(f"| `{r['class']}` | `{r['table']}` | `{r['prop']}` |")
        return 0

    print(f"templates={stats['templates']}  model+dto types={stats['types']}  "
          f"templates with a resolved bean={stats['resolved']}")
    print(f"\n=== MISSING_PROPERTY ({len(missing)}) "
          "— template field with no supplying bean property")
    for r in missing:
        print(f"  {r['template']:38s} {r['field']:28s} [{r['bean']}]")
    printed = [r for r in raw_ids if r["printed"]]
    unused = [r for r in raw_ids if not r["printed"]]

    print(f"\n=== RAW_ID / printed ({len(printed)}) "
          "— a template prints this FK and no name is available")
    for r in printed:
        print(f"  {r['class']:32s} {r['prop']:26s} ({r['table']})")
    print(f"\n=== RAW_ID / not printed ({len(unused)}) "
          "— FK without a name sibling, but no template prints it (informational)")
    by_table: dict[str, list[str]] = defaultdict(list)
    for r in unused:
        by_table[f"{r['class']}  ({r['table']})"].append(r["prop"])
    for k in sorted(by_table):
        print(f"  {k}: {', '.join(sorted(by_table[k]))}")

    # A field no bean supplies is not a style problem: JRBeanCollectionDataSource
    # resolves fields by bean property, so the fill throws as soon as there is a
    # row to read. A printed raw FK is the report showing an id where a name
    # belongs. Neither is informational, so --check fails on both.
    #
    # The unprinted FKs stay informational on purpose: they are filter columns,
    # and nothing reaches a page through them.
    if args.check and (missing or printed):
        print("", file=sys.stderr)
        print(f"FAILED: {len(missing)} template field(s) with no supplying bean "
              f"property, {len(printed)} printed raw FK(s).", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
