# Financial Data Form V2 (كشوفات المساعدة المالية الربعية), field by field

This is the second financial data form. It sits beside the first form (`/api/reports/financial-data-form`) and is laid
out like the ministry's own quarterly statement: one row per student, grouped by sponsoring entity, with five
signatories in the page footer (`Backend/docs/scripts/V057__financial_data_form_v2.sql`, header comment).

## Where it lives

| Piece | File |
|---|---|
| Read view `MOHECORE.Q_Stud_Financial_Data_Form_V2` | Created in `Backend/docs/scripts/V057__financial_data_form_v2.sql`. The current definition, which adds the `*_EN` columns, is in `Backend/docs/scripts/V107__bilingual_remaining_report_views.sql`. |
| Entity (bean) | `Backend/src/main/java/com/dilaw/mohe/model/StudentFinancialDataFormV2.java` (`@Immutable`, `@Id` = `STUDENT_ID`) |
| Repository | `Backend/src/main/java/com/dilaw/mohe/repository/StudentFinancialDataFormV2Repository.java` |
| Service | `Backend/src/main/java/com/dilaw/mohe/service/StudentFinancialDataFormV2Service.java` |
| Controller | `Backend/src/main/java/com/dilaw/mohe/controler/StudentFinancialDataFormV2Controller.java` |
| Template | `Backend/src/main/resources/reports/financial-data-form-v2.jrxml` (A4 portrait, font Cairo) |
| Masthead and signatories | `MOHECORE.REPORT_TYPE_SETTINGS` row `FINANCIAL_DATA_FORM_V2` (seeded by V057, reworded by `V138__report_settings_ministry_of_finance.sql`) |
| Column headings | `LU_REPORT_LABEL` rows seeded in `Backend/docs/scripts/V077__report_labels_from_the_templates.sql` |
| Catalogue entry | `REP_FINANCIAL_DATA_FORM_V2` in `Backend/docs/scripts/V073__new_code_lists_and_report_catalogue.sql`, route `/reports/financial-data-form-v2` |

## Endpoints

The controller is mapped at `/api/reports/financial-data-form-v2` and guarded by `@PreAuthorize(MoheSecurityExpressions.FINANCE)`.

- `GET /by-quarter-year?quarter&year[&countryId]` returns the rows as JSON.
- `GET /by-country-quarter-year?country&quarter&year[&countryId]` does the same, filtered by the country **name** (`STUDY_COUNTRY_IN_NAME`).
- `GET /metadata[?countryId]` returns the distinct years, quarters (1 to 4) and countries.
- `GET /export-report` renders the report with `quarter`, `year`, `lang` (default `ar`), `country` and `disposition`. The paths `/report-pdf`, `/doc`, `/export-excel` and `/report-xlsx` are aliases. The output file name is `financial-data-form-v2-q{quarter}-{year}`.

Country scope is applied by id: `STUDY_COUNTRY_IN_ID IN :countryIds` (repository `SCOPE`). Rows are ordered by
`entityName, studentFirstReceiptOfDue, studentName` (repository `ORDER`), and the template groups on `entityName`.
A quarter is chosen by `STUDENT_FIN_RECIEVED_YEAR = :year AND STUDENT_FIN_RECIEVED_MONTH = :quarter`. The
`..._MONTH` column holds the quarter number despite its name (comment on the entity field).

## Printed columns (detail band, right to left)

Headings come from `COL_*` parameters. `ReportParamBuilder.addReportLabels` fills each one from the generic `LU_REPORT_LABEL`
row and then applies any `CODE@FINANCIAL_DATA_FORM_V2` override on top. The labels below are the V077 seed; an override is marked *(V2)*.
In the view, the aliases are `sd` = `MOHECORE.STUDENT_DATA` and `sf` = `MOHECORE.STUDENT_FINANCE`. Lookups are joined with `LEFT JOIN` on `sd`, except `sci`, which is joined on `sf`.

| # | Heading ar / en | Bean property | View column | SQL expression and source |
|---|---|---|---|---|
| 1 | م / # | none (`$V{ROW_NUMBER}`) | none | A running count of `studentId` in the template |
| 2 | اسم الطالب / Student Name | `studentName` | `STUDENT_NAME` | `ISNULL(sd.STUDENT_NAME, N'')` |
| 3 | الجهة / Entity | `entityName` (EN: `entityNameEn`) | `ENTITY_NAME` | First non-blank of `STUDENT_UNIVERSITY_TYPE.STUDENT_UNIVERSITY_TYPE_NAME`, then `STUDENT_MINISTRY_TYPE.STUDENT_MINISTRY_TYPE_NAME`, then `STUDENT_AGENCY_TYPE.STUDENT_AGENCY_TYPE_NAME`, else `''`. Each lookup is joined on the id of the same name in `sd`. The `_EN` form tries the same order. |
| 4 | الدولة / Country *(V2)* | `studyCountryInName` (EN: `studyCountryInNameEn`) | `STUDY_COUNTRY_IN_NAME` | `ISNULL(STUDY_COUNTRY_IN.STUDY_COUNTRY_IN_NAME, N'')`, joined on **`sf`**`.STUDY_COUNTRY_IN_ID` (the country on the finance row) |
| 5 | عام الإيفاد / Delegation Year | `studentDelegationDecisionYear` | same | `sd.STUDENT_DELEGATION_DECISION_YEAR` (mapped as `String`) |
| 6 | الدرجة / Degree *(V2)* | `studentDegreeName` (EN sibling) | `STUDENT_DEGREE_NAME` | `ISNULL(STUDENT_DEGREE.STUDENT_DEGREE_NAME, N'')` on `sd.STUDENT_DEGREE_ID` |
| 7 | التخصص / Major | `studentMajorName` (EN sibling) | `STUDENT_MAJOR_NAME` | `ISNULL(STUDENT_MAJOR.STUDENT_MAJOR_NAME, N'')` on `sd.STUDENT_MAJOR_ID` |
| 8 | رقم القرار / Decision No. *(V2)* | `studentDelegateDecisionNo` | same | `sd.STUDENT_DELEGATE_DECISION_NO` (Integer) |
| 9 | مدة الدراسة / Study Duration | `studentStudyNoOfYears` | same | `sd.STUDENT_STUDY_NO_OF_YEARS` (Integer) |
| 10 | المستوى الحالي / Current Level *(V2)* | `currentLevelLabel` | `CURRENT_LEVEL_LABEL` | `CASE`: if `STUDENT_CURRENT_LEVEL.STUDENT_CURRENT_LEVEL_ORDER_NO` is set **and** the level name is a year (`..._NAME_EN LIKE '%Year%'` or `..._NAME LIKE N'السنة%'`), the order number as text (e.g. `7`). Otherwise the Arabic level name (e.g. `تمديد نظامي`). |
| 11 | أول مصرف / First Payment | `studentFirstReceiptOfDue` | same | `sd.STUDENT_FIRST_RECEIPT_OF_DUE` (Integer; the entity comment gives `20173` as an example, a year followed by a quarter). The cell background colour changes with each entity group (`firstReceipt` style, six colours, `ENTITY_INDEX % 6`). |
| 12 | المبلغ / Amount | `studentAmount` | `STUDENT_AMOUNT` | `sd.STUDENT_AMOUNT`, printed as `"$" + %,d` |
| 13 | توقيع الطالب / Student Signature | `studentNote2` | `STUDENT_NOTE_2` | `CAST(sd.STUDENT_NOTE_2 AS NVARCHAR(MAX))`, printed in dark red. The cell is blank for the student to sign unless a note is present; the template and entity describe such a note as a payment-stop flag. |
| 14 | ملاحظات / Notes | `studentNote1` | `STUDENT_NOTE_1` | `CAST(sd.STUDENT_NOTE_1 AS NVARCHAR(MAX))` |

The view keeps only rows where `ISNULL(sd.IS_DELETED, 0) = 0`. It **inner**-joins `STUDENT_FINANCE`, so a student with no
finance row does not appear (V057 comment).

### Other parts of the page

- **Masthead, three lines** (`TITLE_01..03_AR/EN`). These come from `REPORT_TYPE_SETTINGS`, with defaults in the JRXML. After V138 they read: الجمهورية اليمنية / وزارة المالية / الإدارة العامة للبعثات - إدارة مالية البعثات.
- **Report title** (`REPORT_TITLE`). The fallback is `كشوفات المساعدة المالية الربعية`.
- **Quarter** (`LBL_QUARTER` الربع + `QUARTER_LABEL`). The quarter label is the ordinal quarter name from the `report-label` code list (`StudentFinancialDataFormV2Service.quarterLabel`).
- **Year** (`LBL_YEAR` العام + `FILTER_YEAR` + `LBL_YEAR_SUFFIX` م). The report date is `REPORT_DATE`.
- **Entity** (`LBL_ENTITY` + `ISSUING_ENTITY`). This is the masthead's second line in the report language (`StudentFinancialDataFormV2Controller.issuingEntity`), not a student's entity.
- **Country** (`LBL_COUNTRY` + `FILTER_COUNTRY`, plus `PRINTED_BY`). The value is the requested country, or the single country the rows share (`singleCountryName`).
- **Notes** (`LBL_NOTES_TITLE` + `LBL_NOTE_1..4`). These are four fixed notes from V077 (payment suspension, final warning, stopping payment, embassy responsibility).
- **Summary.** `LBL_GRAND_TOTAL (LBL_STUDENTS: n)` followed by `TOTAL_AMOUNT_SUM`, the template's sum of `studentAmount`.
- **Footer.** Up to five signatories (`SIG_NAME_n` / `SIG_POS_n`, printed while `SIG_COUNT >= n`), `FOOTER_TEXT`, and the generated date and page number. V057 seeds only the five positions and leaves the names blank; the names are entered at `/settings/report-settings`. V138 renames the fifth position to وزير المالية / Minister of Finance.

## English rendering

With `lang=en`, the labels come from the `NAME_EN` of each label row, and `BilingualRows`
(`Backend/src/main/java/com/dilaw/mohe/report/BilingualRows.java`) replaces each `*Name` property with its `*NameEn` sibling.

## Gaps and open points

1. **Only the current quarter can print.** The view reads `STUDENT_FINANCE` only. Per `V038__finance_quarter_uniqueness.sql`,
   that table's primary key is `STUDENT_ID` alone and it holds the current quarter; history is in `STUDENT_FINANCE_ARCH`,
   which the view does not read. So `/metadata` can offer only the quarter currently loaded, and an earlier quarter returns no rows.
2. **The amount is not the paid amount.** Column 12 prints `STUDENT_DATA.STUDENT_AMOUNT`, the allowance on the student record.
   The finance row's `STUDENT_FIN_RECIEVED_AMOUNT` is in the view and the bean (`studentFinReceivedAmount`) but is not printed.
3. **The same total is printed twice.** The summary band shows `TOTAL_AMOUNT_SUM` twice: under the amount column (x=131) and
   across the signature and notes columns (x=0). The controller also passes `TOTAL_AMOUNT` and `TOTAL_STUDENTS`, but the
   template does not use them.
4. **The level is not translated in English.** `CURRENT_LEVEL_LABEL` has no English sibling, so an English report prints the Arabic
   level name whenever the level is not a numbered year. `CURRENT_LEVEL_NAME_EN` exists, but it is not what column 10 prints.
5. **The signature and notes columns are swapped relative to the note numbers.** The signature column prints `STUDENT_NOTE_2` and the
   notes column prints `STUDENT_NOTE_1`. The template comment says the signature column is meant for payment-stop flags, but nothing in
   the code shows that `STUDENT_NOTE_2` holds only such flags.
6. **The country filter compares names.** `by-country-quarter-year` matches the Arabic `STUDY_COUNTRY_IN_NAME`
   (the service comment calls it "the Arabic name the rows are filtered by"), not an id.
7. **Entity id.** `@Id` is `STUDENT_ID`. This is unique only because `STUDENT_FINANCE` holds one row per student (V038). If that key is
   ever widened, rows for the same student in the entity would collide.
8. **Undocumented formats.** The code does not document the format of `STUDENT_DELEGATION_DECISION_YEAR` (a string) or of
   `STUDENT_FIRST_RECEIPT_OF_DUE` beyond the one example `20173`.
