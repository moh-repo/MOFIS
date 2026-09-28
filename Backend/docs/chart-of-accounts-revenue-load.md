# Revenue chart of accounts (مفردات الموارد): how V064 read and loaded it

`Backend/docs/scripts/V064__chart_of_accounts_revenue.sql` loads the ministry's economic classification of revenue into
`FINCORE.COA_ACCOUNT`. It is **generated** by `Backend/scripts/coa_from_workbook.py` from the ministry's monthly revenue
statement form, `Backend/docs/reference/مفردات الموارد.xls`. V064 is not meant to be edited by hand. If the source is
corrected, re-run the generator instead (V064 header).

## Background: the chart scaffolding (V041)

`Backend/docs/scripts/V041__chart_of_accounts.sql` creates the tables and seeds **no accounts**. Its comment explains why:
the account codes are a controlled document of the Executive Regulation, and they must not be invented.

- `FINCORE.COA_SEGMENT` has six segments: `ECONOMIC`, `ADMIN`, `FUNDING_SOURCE`, `FUNCTIONAL`, `PROJECT` and `GOV`.
- `FINCORE.COA_LEVEL` holds the level names and `CODE_LENGTH` for each segment. `CODE_LENGTH` is seeded `NULL`, which means "not specified, do not validate". V041 seeded the economic levels with the Law's wording: 1 باب, 2 بند, 3 نوع, 4 مجموعة, 5 حساب.
- `FINCORE.COA_ACCOUNT` is versioned per fiscal year (`FISCAL_YEAR_ID`) and unique on `(SEGMENT_ID, FISCAL_YEAR_ID, FULL_CODE)`. Its `FULL_CODE` is a materialised path: the parent's `FULL_CODE` followed by `CODE`.
- The trigger `FINCORE.TR_COA_ACCOUNT_VALIDATE` enforces these rules:
  - the parent is in the same segment and year, exactly one level up;
  - `FULL_CODE = fn_coa_full_code(parent, CODE)`;
  - `LEN(CODE)` matches the level's `CODE_LENGTH` when one is set;
  - only a leaf may be postable;
  - `CODE` cannot change once the account has children;
  - there are no cycles.
- `CK_COA_ACCOUNT_NORMAL_BALANCE` ties `REVENUE` to `NORMAL_BALANCE = 'C'`.

The JPA side is `Backend/src/main/java/com/dilaw/mohe/accounting/model/CoaAccount.java`, `CoaLevel.java`, `CoaSegment.java`,
`accounting/service/CoaAccountService.java` and `accounting/controller/ChartOfAccountsController.java`
(`api/v1/accounting/chart`: `/segments`, `/levels`, `/accounts`, `/accounts/postable`, `/summary`; writes need `COA_EDIT`).

## How the workbook is read (`coa_from_workbook.py`)

- **Opening the file.** The workbook is encrypted with Excel's default key `VelvetSweatshop`, which is sheet protection rather than a password. `msoffcrypto` decrypts it and `xlrd` reads it (`open_workbook`).
- **Sheet.** The default is `--sheet 2`, described in the script as "the first 2019 sheet". V064 was generated from sheet `"2"` (V064 header). The header records the decision: *"The 2019 sheets are the source; sheet 1 (2026) has three code typos."* Neither the code nor the script names the three typos.
- **Columns.** Column 0 is the description. Columns 1 to 4 are الباب / الفصل / البند / النوع (`read_tree`).
- **Row rule.** A row is a classification row only when **exactly one** of the four code columns holds an integer (`norm_code` accepts integral floats and digit strings). The column it is in gives the level (1 to 4). Every other row is skipped.
- **Names.** Names are cleaned (`clean_name` removes tatweel `ـ` and collapses whitespace). The script stops with an error if a code row has no name, or if its name starts with a total or heading word: `جملة`, `إجمالي`, `اجمالي`, `بيان` or `المستبعد` (`NOTE_PREFIXES`).
- **Tree.** The tree is rebuilt with a stack of the most recent code at each level. A code whose parent levels are not all open above it is an error.
- **Validation** (`validate`):
  - duplicate code paths are an error (the comment gives "a sheet with typos?" as the cause);
  - a code wider than its level's width is an error;
  - leaves are the paths with no descendants.

## Hierarchy and code format

V064 renames economic levels 1 to 4 to the form's names and sets their widths. It does this only while level 2 still carries V041's seed (`NAME_AR = N'بند' AND CODE_LENGTH IS NULL`):

| Level | NAME_AR / NAME_EN | CODE_LENGTH | Count in V064 |
|---|---|---|---|
| 1 | باب / Chapter | 1 | 5 |
| 2 | فصل / Section | 1 | 22 |
| 3 | بند / Item | 1 | 63 |
| 4 | نوع / Type | 2 | 253 |
| 5 | حساب / Account | unchanged (NULL) | 0 |

The widths are "read from the manual itself (largest code at each level: 5, 7, 7, 23)" (`LEVELS` comment in the script).
Codes at level 4 are zero-padded to two digits (`code_str`), so `FULL_CODE` is unambiguous. For example, chapter `1`,
section `1`, item `1` and type `01` give `FULL_CODE` **`11101`** (زكاة الحبوب). Total: **343 accounts per fiscal year, of which 260 are
postable leaves**: all 253 types plus 7 items that have no types (`142`, `152`, `164`, `165`, `166`, `313`, `324`).
The counts are asserted by `Backend/src/test/java/com/dilaw/mohe/accounting/ChartOfAccountsRevenueSeedTest.java`.

The five chapters (level 1) are:

| Code | Chapter | Sections / items / types |
|---|---|---|
| 1 | الإيرادات الضريبية | 7 / 24 / 86 |
| 2 | المنح | 3 / 6 / 20 |
| 3 | إيردات دخل الملكية ومبيعات السلع والخدمات والتحويلات والمتنوعة | 5 / 14 / 90 |
| 4 | التصرف في الأصول غير المالية | 3 / 9 / 23 |
| 5 | التصرف في الأصول المالية وتحمل الخصوم | 4 / 10 / 34 |

The counts were taken from the `@chart` rows in V064. Names are loaded exactly as they appear on the sheet, including its spelling (e.g. `إيردات`).

## How rows become accounts (V064 body)

Each tree node becomes one row of the `@chart` table variable: `(level_no, code, full_code, parent_full, name_ar, postable)`,
in sheet order, so a parent always comes before its children. Then, for **every fiscal year with `STATUS = 'OPEN'`**
(`FINCORE.FISCAL_YEAR`), one transaction per year does the following:

- If `(segment, year, FULL_CODE)` already exists, the row is **skipped**, and names are never overwritten.
- Otherwise the parent is resolved by `parent_full`. If the parent is missing, the script throws 50065 ("generator ordering broken").
- The account is inserted with fixed values:

| Column | Value |
|---|---|
| `SEGMENT_ID` | ECONOMIC |
| `NAME_EN` | NULL |
| `ACCOUNT_NATURE` | `REVENUE` |
| `NORMAL_BALANCE` | `'C'` |
| `IS_POSTABLE` | 1 for a leaf |
| `IS_BUDGETARY` | 1 |
| `STUDY_COUNTRY_IN_ID` | NULL (the national chart) |

The rationale given for these values: "the form calls all five chapters موارد".

The script prints how many rows were inserted and skipped for each year, then a verification count by year and level. It needs
`QUOTED_IDENTIFIER ON` (`sqlcmd -I`) and UTF-8 (`-f 65001`). It throws 50064 if the ECONOMIC segment is missing.

## Regenerating

From `Backend/` (script docstring):

```
pip install xlrd msoffcrypto-tool
python scripts/coa_from_workbook.py "docs/reference/مفردات الموارد.xls" \
    --sheet 2 --out docs/scripts/V064__chart_of_accounts_revenue.sql
```

The script prints the account count per level and the number of postable leaves. After regenerating, compare these with the
numbers in `ChartOfAccountsRevenueSeedTest` (5 / 22 / 63 / 253, 260 leaves), which will fail if the shape changes.
Re-applying V064 to a database is idempotent. It adds only the accounts that are missing, including for any fiscal year opened since. V064 is numbered below the Flyway baseline (96), and V138's header says scripts below it "never run again", so a re-run must be done by hand (`sqlcmd -I -f 65001 -b -i docs/scripts/V064__chart_of_accounts_revenue.sql`).

The workbook itself was not opened while writing this page, because `xlrd` and `msoffcrypto` are not installed in this environment.
Everything above is taken from the script and the generated SQL.

## What is still missing (according to the code)

- **Other segments are empty.** `ADMIN`, `FUNDING_SOURCE`, `FUNCTIONAL`, `PROJECT` and `GOV` have no accounts, and "every other segment must still be empty until the Regulation's lists for it arrive" (`accounting/ChartOfAccountsTest.java`, `onlyTheManualIsSeeded`).
- **Only revenue is loaded.** The economic segment holds revenue only (`ACCOUNT_NATURE = 'REVENUE'` throughout). `DerivedAccountingDataTest` treats any non-revenue, non-budgetary or country-specific row as "invented".
- **No fee-type mappings.** `FINCORE.COA_ACCOUNT_MAP` stays empty: the V045 derivation "must not map fee types to accounts it did not have" (`accounting/DerivedAccountingDataTest.java`). As a result, the ministry's student payments are not yet classified against any account.
- **Level 5 and the Law's level names.** Level 5 (حساب) has no width and no accounts. The Law's level مجموعة (V041 level 4) no longer appears: V064 replaced the level names 1 to 4 with the form's باب / فصل / بند / نوع.
- **No English names.** `NAME_EN` is NULL for all 343 accounts.
- **New fiscal years get no chart.** Years opened after V064 ran (for example by V126, or through `POST /accounting/fiscal-years`) are not given the chart. V126 states "the chart of accounts is kept per year and is not rolled forward here (DEC-20)". A re-run of V064 would fill them.
- **The 2026 sheet is unused.** Sheet 1 (2026) was not used because of three code typos, which are not identified anywhere in the code.
