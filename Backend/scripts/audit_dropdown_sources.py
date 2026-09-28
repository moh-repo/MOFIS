#!/usr/bin/env python3
"""
Dropdown provenance audit.

Answers one question for every dropdown in the Angular app: are its options read
from the database, or asserted in the client?

A companion to audit_report_data.py, which follows the JRXML field -> bean ->
column chain for report *output*. This one follows the filter *input* chain:

    [options] / [suggestions]  ->  component field  ->  assignment  ->  response

Both bindings are audited: a p-select takes [options], a p-autoComplete takes
[suggestions], and either way the user is picking from a list that should have
come out of the database.

Classification of each assignment to a bound field:

  DB        the field is assigned inside a .subscribe(...) / .then(...) callback,
            so its value came off an HTTP response; or a getter/method returns a
            value read from a data service.
  DERIVED   the field is assigned from another field of the same component;
            resolved by following that field (one or more hops).
  LITERAL   the field is assigned an array literal written in the component.
  EMPTY     `= []` initialiser only; carries no options by itself.

A binding can also name a getter (`get reportLangOptions()`) or a method
(`options('notification-channel')`) rather than a plain field. Both are resolved
by classifying the returned expression.

A LITERAL is a finding unless it names UI grammar rather than ministry data --
the AR/EN report-language toggle, SQL operators in the Query Builder, page-size
choices. Those live in ALLOWED_LITERALS with the reason each is allowed.

Usage:
    python scripts/audit_dropdown_sources.py             # human-readable
    python scripts/audit_dropdown_sources.py --markdown  # table for the audit doc
    python scripts/audit_dropdown_sources.py --check     # exit 1 on any finding

--check is the regression guard: it fails if a literal dropdown appears that is
not on the allowlist.
"""

from __future__ import annotations

import argparse
import re
import sys
from dataclasses import dataclass, field
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
COMPONENTS = REPO / "Frontend" / "src" / "app" / "demo" / "components"

# Bound fields whose options are UI grammar, not ministry data. Each entry is
# keyed "<component stem>:<field>" and carries the reason it is not a finding.
ALLOWED_LITERALS: dict[str, str] = {
    "*:reportLangOptions": "AR/EN report-language toggle - a rendering choice, not data",
    "*:rowsPerPageOptions": "paginator page sizes - a UI preference",
    "query-builder:operatorOptions": "SQL comparison operators - query grammar",
    "query-builder:joinTypeOptions": "SQL join kinds - query grammar",
    "query-builder:sortDirectionOptions": "ASC/DESC - query grammar",
    "*:activeOptions": "yes/no active filter - a boolean, not a lookup",
    "audit-log:operationOptions": "INSERT/UPDATE/DELETE - the audit trigger's own verbs",
    "student-sessions:statusOptions": "all / active-only - a boolean, not a lookup",
    "report-settings:signatoryCountOptions": "how many signature blocks to print, 0-5",
    "student-tracer:layoutOptions": "vis-network layout algorithms - library grammar",
    # The lookup-table admin screen picks which lookup *entity* to edit. The
    # list is application structure — it names the JPA entities the backend
    # exposes — and each entity's rows are then read from the database. It must
    # stay in step with LookupTableController; LookupTableRegistryTest checks it.
    "lookuptables:studentLookups": "registry of editable lookup entities, not data",
    "lookuptables:studyUniversityLookups": "registry of editable lookup entities, not data",
    "lookuptables:financeLookups": "registry of editable lookup entities, not data",
    "lookuptables:operationsLookups": "registry of editable lookup entities, not data",
    "lookuptables:systemLookups": "registry of editable lookup entities, not data",
    "lookuptables:locationLookups": "registry of editable lookup entities, not data",
}

# Bindings traced to the database by hand because the chain runs through an
# indirection this script does not follow — a memoising getter, a cache Map, a
# helper service that reshapes rows it was handed. Each entry records the trace
# so the claim can be rechecked rather than taken on trust.
VERIFIED_INDIRECT: dict[str, str] = {
    "student-loan:studentsForDropdown":
        "studentPicker.search() -> this.students -> toOptions()",
    "student-books-allowance:studentsForDropdown":
        "studentPicker.search() -> this.students -> toOptions()",
    "finance-received-whom:studentsForDropdown":
        "studentPicker.search() -> this.students -> toOptions()",
    "student-quick-edit:yearOptions":
        "distinct studentDelegationDecisionYear over this.students (loaded line 210)",
    "mohe-quarter:filterYearOptions":
        "distinct years over this.quarters (loaded line 221)",
    "query-builder:columnOptions":
        "this.reportData, the query-builder catalogue loaded at line 125",
    "query-builder:filterableColumnOptions":
        "this.reportData, the query-builder catalogue loaded at line 125",
    "sys-parameters:typeOptions":
        "distinct valueType over this.rows, the loaded parameters",
    "lookuptables:optionsFor":
        "optionCache, filled from the lookup API at line 410",
    "code-lists:groupedLists":
        "CODE_LIST_GROUPS names the catalogue's groups; the rows come from the API",
    "code-lists:severityOptions":
        "PrimeNG severity tokens - UI grammar, not data",
}

# Expressions that configure a chart rather than fill a dropdown. PrimeNG's
# p-chart takes [options] too, so they land in the same grep.
CHART_OPTION_RE = re.compile(r"(?i)(chart|graph|map)(options|\.options)$")

# PrimeNG fills a p-select/p-multiSelect from [options] and a p-autoComplete
# from [suggestions]. Both are option lists a user picks from, so both are
# audited.
BINDING_RE = re.compile(r'\[(?:options|suggestions)\]="([^"]+)"')
# `get foo(): T {` and `foo(args): T {` — a binding may name either.
ACCESSOR_RE = re.compile(
    r"^[ \t]*(?:readonly |private |public |protected |static )*"
    r"(get\s+)?([A-Za-z_$][\w$]*)\s*(\([^)]*\))",
    re.MULTILINE,
)
# A service call whose result is data: `this.codeLists.options(...)`,
# `this.lookupService.get(...)`. Excludes the translation and formatting helpers,
# which shape a label but do not supply the set of options.
DATA_SERVICE_RE = re.compile(
    r"this\.(?!translationService|translation|router|messageService|fb|cd\b)"
    r"([A-Za-z_$][\w$]*(?:Service|Lists|lists|Scope|codeLists|lookup\w*))\s*\.",
    re.IGNORECASE,
)
# `this.foo = ...` and bare class-field initialisers `foo: T[] = ...`
ASSIGN_RE = re.compile(r"this\.([A-Za-z_$][\w$]*)\s*=\s*")
# The type annotation may itself carry braces and semicolons —
# `Array<{ label: string; value: string }>` — so it is matched as a sequence of
# plain runs and balanced brace groups rather than "anything but ; and =".
FIELD_INIT_RE = re.compile(
    r"^[ \t]*(?:readonly |private |public |protected )*"
    r"([A-Za-z_$][\w$]*)\s*(?::(?:[^=;{]|\{[^{}]*\})+)?=\s*",
    re.MULTILINE,
)


def strip_comments(src: str) -> str:
    """Blank out comments and string bodies so they cannot match as code.

    Lengths are preserved so every offset stays valid against the original.
    """
    out = list(src)
    i, n = 0, len(src)
    while i < n:
        two = src[i : i + 2]
        if two == "//":
            j = src.find("\n", i)
            j = n if j < 0 else j
            for k in range(i, j):
                out[k] = " "
            i = j
        elif two == "/*":
            j = src.find("*/", i + 2)
            j = n if j < 0 else j + 2
            for k in range(i, j):
                if out[k] != "\n":
                    out[k] = " "
            i = j
        elif src[i] in "'\"`":
            quote = src[i]
            j = i + 1
            while j < n:
                if src[j] == "\\":
                    j += 2
                    continue
                if src[j] == quote:
                    break
                j += 1
            j = min(j, n - 1)
            for k in range(i + 1, j):
                if out[k] != "\n":
                    out[k] = " "
            i = j + 1
        else:
            i += 1
    return "".join(out)


def callback_ranges(src: str) -> list[tuple[int, int]]:
    """Character ranges covered by a .subscribe(...) / .then(...) argument list.

    An assignment inside one of these took its value from an HTTP response.
    """
    ranges: list[tuple[int, int]] = []
    for m in re.finditer(r"\.(?:subscribe|then)\s*\(", src):
        depth, i, n = 0, m.end() - 1, len(src)
        while i < n:
            if src[i] == "(":
                depth += 1
            elif src[i] == ")":
                depth -= 1
                if depth == 0:
                    ranges.append((m.end(), i))
                    break
            i += 1
    return ranges


def rhs_of(src: str, start: int) -> str:
    """The right-hand side of an assignment beginning at `start`, to its end."""
    depth, i, n = 0, start, len(src)
    while i < n:
        c = src[i]
        if c in "([{":
            depth += 1
        elif c in ")]}":
            if depth == 0:
                break
            depth -= 1
        elif c in ";\n" and depth == 0 and i > start:
            if c == ";":
                break
            # a newline only ends the RHS when the statement looks complete
            if src[start:i].strip().endswith((",", "(", "[", "{", "=", "?", ":", "&&", "||")):
                pass
            else:
                break
        i += 1
    return src[start:i]


def block_body(src: str, brace: int) -> str:
    """The text between `brace` (an opening `{`) and its match."""
    depth, i, n = 0, brace, len(src)
    while i < n:
        if src[i] == "{":
            depth += 1
        elif src[i] == "}":
            depth -= 1
            if depth == 0:
                return src[brace + 1 : i]
        i += 1
    return src[brace + 1 :]


def body_brace(src: str, after_params: int) -> int:
    """Index of the `{` opening a method body, skipping any return type.

    A return type can itself contain braces — `Array<{ label: string }>`,
    `{ a: string }[]` — so the first `{` after the parameter list is not
    reliably the body. A brace is part of the type if it sits inside angle
    brackets, or if its match is followed by a type continuation (`[]`, `>`,
    `|`, `&`).
    """
    i, n, angle = after_params, len(src), 0
    while i < n:
        c = src[i]
        if c == "<":
            angle += 1
        elif c == ">":
            angle = max(0, angle - 1)
        elif c == "{":
            if angle > 0:
                depth = 0
                while i < n:
                    if src[i] == "{":
                        depth += 1
                    elif src[i] == "}":
                        depth -= 1
                        if depth == 0:
                            break
                    i += 1
                i += 1
                continue
            depth, j = 0, i
            while j < n:
                if src[j] == "{":
                    depth += 1
                elif src[j] == "}":
                    depth -= 1
                    if depth == 0:
                        break
                j += 1
            tail = src[j + 1 : j + 4].lstrip()
            if tail[:2] == "[]" or tail[:1] in (">", "|", "&"):
                i = j + 1
                continue
            return i
        elif c in ";}" and angle == 0:
            return -1
        i += 1
    return -1


def accessors(src: str) -> dict[str, tuple[int, str]]:
    """Getter and method bodies by name, as (line, body).

    Constructors and lifecycle hooks are skipped — they assign fields rather
    than supply one, and their assignments are already picked up separately.
    """
    out: dict[str, tuple[int, str]] = {}
    skip = {"constructor", "ngOnInit", "ngOnDestroy", "ngAfterViewInit", "ngOnChanges",
            "if", "for", "while", "switch", "catch", "function"}
    for m in ACCESSOR_RE.finditer(src):
        name = m.group(2)
        if name in skip:
            continue
        brace = body_brace(src, m.end(3))
        if brace < 0:
            continue
        out.setdefault(name, (src.count("\n", 0, m.start()) + 1, block_body(src, brace)))
    return out


@dataclass
class Site:
    line: int
    kind: str  # DB | DERIVED | LITERAL | EMPTY | UNKNOWN
    detail: str = ""


@dataclass
class Field_:
    name: str
    sites: list[Site] = field(default_factory=list)


def classify_component(ts_path: Path) -> tuple[dict[str, Field_], dict[str, tuple[int, str]]]:
    """Every assigned field of a component, with each assignment classified.

    Returns the fields alongside the component's getter/method bodies, which a
    binding may name instead of a field.
    """
    raw = ts_path.read_text(encoding="utf-8", errors="replace")
    src = strip_comments(raw)
    cbs = callback_ranges(src)
    fields: dict[str, Field_] = {}

    def in_callback(pos: int) -> bool:
        return any(a <= pos <= b for a, b in cbs)

    def add(name: str, pos: int, rhs: str) -> None:
        line = src.count("\n", 0, pos) + 1
        body = rhs.strip()
        if re.fullmatch(r"\[\s*\]", body):
            kind, detail = "EMPTY", ""
        elif body.startswith("["):
            # An array literal is asserted data wherever it sits. This test must
            # come before the callback test: a literal written *inside* a
            # .subscribe() handler ignores the response it was handed, and is
            # exactly the defect this audit exists to catch. Scoring it DB
            # because of where it sits would hide it.
            # The common honest shape is an "All" sentinel followed by
            # `...this.rows.map(...)` — the options come from the spread, and the
            # sentinel is a UI affordance rather than data. So a spread decides
            # it, and only a literal with no spread and no reference to fetched
            # rows is asserted data.
            inner = body[1:-1] if body.endswith("]") else body[1:]
            # A spread may name a field (`...this.rows`) or a local the method
            # built from one (`...sortedYears.map(...)`); locals are captured
            # below under their own name, so both resolve the same way.
            spread = re.findall(r"\.\.\.\s*(?:this\.)?([A-Za-z_$][\w$]*)", inner)
            refs = re.findall(r"this\.([A-Za-z_$][\w$]*)", inner)
            literals = re.findall(r"['\"`{]|\b\d+\b", inner)
            if spread and in_callback(pos):
                # `[sentinel, ...statuses]` inside a handler: the spread is the
                # response the handler was given.
                kind, detail = "DB", "sentinel plus the spread response"
            elif spread:
                kind, detail = "DERIVED", ",".join(sorted(set(spread)))
            elif refs and not literals:
                kind, detail = "DERIVED", ",".join(sorted(set(refs)))
            else:
                # String bodies were blanked by strip_comments, so echoing the
                # source would print rows of spaces. The location and the size
                # are what the reader needs; the file:line points at the rest.
                entries = inner.count(",") + 1 if inner.strip() else 0
                kind = "LITERAL"
                detail = f"array literal, {entries} entr{'y' if entries == 1 else 'ies'}"
        elif in_callback(pos):
            kind, detail = "DB", "assigned from an HTTP response"
        elif DATA_SERVICE_RE.search(body):
            # A synchronous read off a service that holds fetched rows, e.g.
            # `this.codeLists.options('wafar-beneficiary-type')`.
            svc = DATA_SERVICE_RE.search(body).group(1)
            kind, detail = "DB", f"read from {svc}"
        else:
            refs = re.findall(r"this\.([A-Za-z_$][\w$]*)", body)
            refs = [r for r in refs if r != name]
            if refs:
                kind, detail = "DERIVED", ",".join(sorted(set(refs)))
            elif re.match(r"^\(?\s*(?:await\s+)?[A-Za-z_$][\w$]*\s*(?:\?\.|\.|\)|\s*\?\?)", body):
                # a bare local/parameter: usually a callback argument hoisted
                # into a helper, e.g. `apply(metadata)`
                kind, detail = "UNKNOWN", body.split("\n")[0][:60]
            else:
                kind, detail = "UNKNOWN", body.split("\n")[0][:60]
        fields.setdefault(name, Field_(name)).sites.append(Site(line, kind, detail))

    for m in ASSIGN_RE.finditer(src):
        add(m.group(1), m.end(), rhs_of(src, m.end()))
    for m in FIELD_INIT_RE.finditer(src):
        # skip ones already caught as `this.x =` and skip local `const`/`let`
        prefix = src[max(0, m.start() - 6) : m.start()]
        if "const" in prefix or "let" in prefix or "var" in prefix:
            continue
        add(m.group(1), m.end(), rhs_of(src, m.end()))

    # Locals a method builds before spreading into an options array, e.g.
    # `const sortedYears = Array.from(yearSet)...`. Recorded under the local's
    # own name so a spread of it resolves like a field would. Names are not
    # scoped per method, which is acceptable for an audit that flags rather
    # than decides.
    for m in re.finditer(r"\b(?:const|let|var)\s+([A-Za-z_$][\w$]*)\s*(?::[^=;]+)?=\s*", src):
        if m.group(1) not in fields:
            add(m.group(1), m.end(), rhs_of(src, m.end()))

    return fields, accessors(src)


def classify_accessor(line: int, body: str) -> tuple[str, str]:
    """Verdict for a getter/method that supplies a dropdown's options."""
    if DATA_SERVICE_RE.search(body):
        m = DATA_SERVICE_RE.search(body)
        return "DB", f"returns {m.group(1)} data"
    returns = re.findall(r"return\s+([^;]+);", body)
    joined = " ".join(returns) if returns else body
    if re.search(r"\[\s*[{'\"`\d]", joined):
        return "LITERAL", f"line {line}: {joined.strip().splitlines()[0][:60]}"
    refs = re.findall(r"this\.([A-Za-z_$][\w$]*)", joined)
    if refs:
        return "DERIVED", ",".join(sorted(set(refs)))
    return "UNKNOWN", joined.strip()[:60]


def resolve(
    name: str,
    fields: dict[str, Field_],
    accs: dict[str, tuple[int, str]],
    seen: set[str] | None = None,
) -> tuple[str, str]:
    """Collapse a field's assignment sites into one verdict, following DERIVED."""
    seen = seen or set()
    if name in seen:
        return "UNKNOWN", "cycle"
    seen.add(name)
    f = fields.get(name)
    if not f:
        if name in accs:
            line, body = accs[name]
            verdict, why = classify_accessor(line, body)
            if verdict != "DERIVED":
                return verdict, why
            for parent in why.split(","):
                v, w = resolve(parent, fields, accs, seen)
                if v in ("DB", "LITERAL"):
                    return v, f"via {parent}" + (f" - {w}" if v == "LITERAL" else "")
            return "UNKNOWN", "getter derives from unresolved fields"
        return "UNKNOWN", "no assignment found"
    kinds = {s.kind for s in f.sites}
    if "DB" in kinds:
        return "DB", ""
    if "LITERAL" in kinds:
        lit = next(s for s in f.sites if s.kind == "LITERAL")
        return "LITERAL", f"line {lit.line}: {lit.detail}"
    if "DERIVED" in kinds:
        for s in f.sites:
            if s.kind != "DERIVED":
                continue
            for parent in s.detail.split(","):
                if not parent:
                    continue
                verdict, why = resolve(parent, fields, accs, seen)
                if verdict == "DB":
                    return "DB", f"via {parent}"
                if verdict == "LITERAL":
                    return "LITERAL", f"via {parent} - {why}"
        return "UNKNOWN", "derived from unresolved fields"
    if kinds == {"EMPTY"}:
        return "EMPTY", "only ever `[]`"
    return "UNKNOWN", next(iter(f.sites)).detail if f.sites else ""


def allowed(stem: str, fieldname: str) -> str | None:
    return ALLOWED_LITERALS.get(f"{stem}:{fieldname}") or ALLOWED_LITERALS.get(f"*:{fieldname}")


@dataclass
class Row:
    component: str
    binding: str
    fieldname: str
    verdict: str
    note: str


def audit() -> list[Row]:
    rows: list[Row] = []
    for html in sorted(COMPONENTS.rglob("*.component.html")):
        ts = html.with_suffix(".ts")
        if not ts.exists():
            continue
        bindings = sorted(set(BINDING_RE.findall(html.read_text(encoding="utf-8", errors="replace"))))
        bindings = [b for b in bindings if not CHART_OPTION_RE.search(b.strip())]
        if not bindings:
            continue
        fields, accs = classify_component(ts)
        stem = html.name[: -len(".component.html")]
        for b in bindings:
            root = re.match(r"\s*([A-Za-z_$][\w$]*)", b)
            if not root:
                continue
            name = root.group(1)
            verdict, note = resolve(name, fields, accs)
            if verdict in ("LITERAL", "UNKNOWN"):
                reason = allowed(stem, name)
                if reason:
                    verdict, note = "STATIC-OK", reason
                else:
                    traced = VERIFIED_INDIRECT.get(f"{stem}:{name}")
                    if traced:
                        verdict, note = "DB-TRACED", traced
            rows.append(
                Row(
                    component=str(html.relative_to(COMPONENTS)).replace("\\", "/"),
                    binding=b.strip(),
                    fieldname=name,
                    verdict=verdict,
                    note=note,
                )
            )
    return rows


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--markdown", action="store_true", help="emit a markdown table")
    ap.add_argument("--check", action="store_true", help="exit 1 on any finding")
    ap.add_argument("--all", action="store_true", help="list clean rows too")
    args = ap.parse_args()

    rows = audit()
    findings = [r for r in rows if r.verdict in ("LITERAL", "UNKNOWN")]
    counts: dict[str, int] = {}
    for r in rows:
        counts[r.verdict] = counts.get(r.verdict, 0) + 1

    if args.markdown:
        print("| Screen | Dropdown | Source |")
        print("|---|---|---|")
        for r in rows:
            note = f" — {r.note}" if r.note else ""
            print(f"| `{r.component}` | `{r.binding}` | {r.verdict}{note} |")
        return 0

    print(f"dropdown bindings={len(rows)}   " + "   ".join(f"{k}={v}" for k, v in sorted(counts.items())))
    print()
    for label in ("LITERAL", "UNKNOWN"):
        sel = [r for r in rows if r.verdict == label]
        print(f"=== {label} ({len(sel)})")
        for r in sel:
            print(f"  {r.component}\n      [options]=\"{r.binding}\"  {r.note}")
        print()
    if args.all:
        print("=== DB / STATIC-OK / EMPTY")
        for r in rows:
            if r.verdict not in ("LITERAL", "UNKNOWN"):
                print(f"  {r.verdict:10} {r.component}  [{r.binding}]  {r.note}")

    if args.check and findings:
        print(f"FAIL: {len(findings)} dropdown(s) not traced to the database", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
