# Migration runbook

The database schema is changed only by the numbered scripts in `Backend/docs/scripts/V*.sql`.
They **alter an existing `MOHE` database**; they do not create one from nothing (for that, see
[`scripts/baseline/README.md`](scripts/baseline/README.md)).

## Two regimes

| Versions | Applied by | Recorded in |
|---|---|---|
| V001 – V096 | **hand**, in version order, with `sqlcmd` or SSMS, as a login that owns the schemas (`sa` in dev) | nowhere. Check by the objects each script creates (below) |
| V097 onward | **Flyway**, at backend startup (`spring.flyway.*` in `application.properties`) | `dbo.flyway_schema_history` |

Flyway is configured with `baseline-on-migrate=true`, `baseline-version=96`, `validate-on-migrate=true`,
`out-of-order=false`. The first time the backend starts against a database that has no history
table, Flyway records a baseline row at 96 and then runs V097 and everything after it, in order.
Every later start runs only the scripts not yet in the history.

`pom.xml` copies **only the top-level `V*.sql` files** into the jar as `classpath:db/migration`.
`baseline/` (the generated reference schema) and `adhoc/` (one-off operator scripts — read
[`scripts/adhoc/README.md`](scripts/adhoc/README.md) first) are never run on startup.

## Bringing a database up to date

1. **Back up** the database.
2. Find which hand scripts are missing. Open each script and test for what it creates, e.g.
   `SELECT OBJECT_ID('MOHECORE.LU_…')` or `SELECT COL_LENGTH('MOHECORE.STUDENT_DATA','…')`. Most
   V001–V096 scripts guard their DDL with `IF NOT EXISTS`, `COL_LENGTH` or `OBJECT_ID` checks, so
   re-running an applied one is harmless. **These have no statement-level guard and may rely on
   `WHERE NOT EXISTS` / `MERGE` inside their inserts: read them before re-running:** V043, V046, V050,
   V055, V063, V070, V089.
   **Exception, V031:** it is safe to re-run *since the DB-02 fix*. Older copies of V031 dropped and
   recreated the row-level-security policy with only its original predicates and `STATE = OFF`.
3. Apply the missing V001–V096 scripts in version order:
   ```
   sqlcmd -S localhost,1433 -U sa -P <password> -d MOHE -b -i Backend/docs/scripts/V0xx__name.sql
   ```
   `-b` stops on the first error. Do not continue past a failing script.
4. Start the backend (`mvnw spring-boot:run -Dspring-boot.run.profiles=dev`, or the deployed jar).
   Flyway applies V097 onwards and logs each version. It runs as `spring.flyway.user` (dev: `sa`;
   prod: the datasource login unless `SPRING_FLYWAY_USER` / `SPRING_FLYWAY_PASSWORD` are set). That
   login needs DDL rights, which the least-privilege application login (V090) does not have.
5. Check the result:
   ```sql
   SELECT installed_rank, version, description, success, installed_on
   FROM dbo.flyway_schema_history ORDER BY installed_rank;
   ```
   Every row must have `success = 1`, and the last version must be the highest script in the folder.

## When a Flyway script fails

- Startup stops and the failed version is recorded with `success = 0`. SQL Server runs each script in
  a transaction where it can, but DDL such as `CREATE VIEW` batches and security policies can leave
  partial work. Read the script's "Rollback" notes, if any, and repair by hand.
- Remove the failed row (`DELETE FROM dbo.flyway_schema_history WHERE success = 0`), or run `flyway
  repair`, then restart.
- **Never edit a script Flyway has already applied.** `validate-on-migrate` compares checksums, and the
  next start will fail. Write a new, higher-numbered script instead.

## Writing a new script

- Name it `V<next number>__short_description.sql` in `Backend/docs/scripts/`. Numbers must stay
  contiguous and unique; `out-of-order=false` refuses a number lower than one already applied.
- Make it idempotent and schema-qualified, like the existing V1xx scripts. Start the header comment
  with `V<n> — title`: the table below is built from that line.
- A `CHECK` constraint on existing data: check the data first. Add the constraint `WITH NOCHECK` only
  when the existing rows cannot be fixed, and say why in the script.
- After applying, regenerate `baseline/V000__baseline_schema.sql` (`python scripts/db_baseline.py`) and
  this file (`python scripts/gen_reference_docs.py`).

## Fixes from the 2026-09-28 system test

| Script | Changes | Report IDs |
|---|---|---|
| V148 | `ROW_VERSION` on `FINCORE.PAYMENT_BATCH`, `ATTACHE_ALLOCATION`, `ATTACHE_ADVANCE_BALANCE`; `CANCELLED_BY`/`CANCELLED_AT` on `PAYMENT_BATCH`; `CK_WAFAR_SPENDING_AMOUNT_POSITIVE` on `MOHECORE.WAFAR_SPENDING` (`WITH NOCHECK` only when existing rows break it: they are listed in the output) | FIN-01, FIN-03, FIN-16 |
| V149 | `MOHECORE.STUDENT_STUDY_STATUS_CHANGE` plus the trigger that fills it and a backfill from `STUDENT_DATA_ARCH`; `locked_until` and `totp_last_step` on `student_users` | PRT-01, PRT-04, PRT-07 |

Both were written without access to SQL Server. **Run them against a copy of the dev database before
production**, and check the `PRINT` output of V148 for Wafar rows with a zero or negative amount.

<!-- GENERATED BELOW by scripts/gen_reference_docs.py; edit above this line only -->

## Scripts

| Version | Applied by | File | Title |
|---|---|---|---|
| 001 | hand | `V001__add_pk_student_finance_wafar.sql` | Add primary key to MOHECORE.STUDENT_FINANCE_WAFAR (one row per student). |
| 002 | hand | `V002__add_pk_study_wafar_type.sql` | Add primary key to MOHECORE.STUDY_WAFAR_TYPE. |
| 003 | hand | `V003__add_lu_business_lookup_tables.sql` | Business lookup tables for dropdowns (fees, alerts, yes/no, active status, years, semesters). |
| 004 | hand | `V004__add_lu_enum_and_report_lookup_tables.sql` | Extended business lookup tables: books/loan status, UMS/approval enums, report dimensions. |
| 005 | hand | `V005__add_student_family_and_death.sql` | Student parents, studying relatives, and death-while-studying case tables. |
| 006 | hand | `V006__dedup_student_data_audit_triggers.sql` | Deduplicate audit triggers on MOHECORE.STUDENT_DATA. |
| 007 | hand | `V007__add_lookup_unique_constraints.sql` | Add UNIQUE constraints on lookup CODE columns (idempotent) |
| 008 | hand | `V008__v005_death_integrity_fixes.sql` | V005 student death/family integrity fixes (idempotent) |
| 009 | hand | `V009__student_document_history_and_encryption.sql` | Student document encryption metadata + version history table. |
| 010 | hand | `V010__add_report_footer_columns.sql` | Add DB-driven footer text columns to per-report-type settings. |
| 011 | hand | `V011__student_document_delete_approval.sql` | Approval workflow for student document deletion. |
| 012 | hand | `V012__restructure_student_data_arch.sql` | Restructure STUDENT_DATA_ARCH so it mirrors STUDENT_DATA and behaves as a proper |
| 013 | hand | `V013__add_current_quarter.sql` | Add CURRENT_QUARTER to STUDENT_DATA and STUDENT_DATA_ARCH. |
| 014 | hand | `V014__add_student_finance_currency.sql` | Add STUDY_CURRENCY_IN_ID to STUDENT_FINANCE. |
| 015 | hand | `V015__add_student_finance_net_amount.sql` | Add NET_AMOUNT to STUDENT_FINANCE and STUDENT_FINANCE_ARCH. |
| 016 | hand | `V016__report_student_document_views.sql` | Views backing the four official single-student documents |
| 017 | hand | `V017__seed_report_type_settings_student_docs.sql` | Seed REPORT_TYPE_SETTINGS rows for the four official single-student |
| 018 | hand | `V018__seed_report_type_settings_payment_auth_form.sql` | Seed REPORT_TYPE_SETTINGS for the payment authorisation voucher |
| 019 | hand | `V019__fix_student_data_orphan_lookups.sql` | Repair orphaned lookup ids on MOHECORE.STUDENT_DATA. |
| 020 | hand | `V020__seed_report_type_settings_quarterly_financial.sql` | Complete the REPORT_TYPE_SETTINGS row for the quarterly financial report. |
| 021 | hand | `V021__fix_budget_analysis_view_period_join.sql` | make V_Budget_Analysis return rows. |
| 022 | hand | `V022__fix_trend_analysis_view_period.sql` | period V_Trend_Analysis by the month the money belongs to. |
| 023 | hand | `V023__fix_comparative_statistics_view_cohort.sql` | compare cohorts in V_Comparative_Statistics, not years of birth. |
| 024 | hand | `V024__fix_query_builder_catalog_view.sql` | make V_Query_Builder describe the schema it claims to describe. |
| 025 | hand | `V025__create_query_engine_readonly_login.sql` | a read-only login for the query builder's composed statements. |
| 026 | hand | `V026__create_saved_query_table.sql` | saved queries for the query builder. |
| 027 | hand | `V027__rebuild_report_management_views.sql` | make the three report-management views return real rows. |
| 028 | hand | `V028__report_reference_data.sql` | give the report layer real reference data to key on. |
| 029 | hand | `V029__add_tenant_country_columns.sql` | give every tenant-owned table its own country column. |
| 030 | hand | `V030__expose_tenant_column_on_report_views.sql` | expose the tenant key on the report views, and stop two reports |
| 031 | hand | `V031__row_level_security.sql` | Row-Level Security on every tenanted table. |
| 032 | hand | `V032__close_tenant_coverage_gaps.sql` | the tenant tables V029 missed. |
| 033 | hand | `V033__capture_report_views_in_source_control.sql` | the 34 report views that existed only in the database. |
| 034 | hand | `V034__remove_fabricated_report_values.sql` | stop five report views asserting values nobody recorded. |
| 035 | hand | `V035__drop_leftover_tables.sql` | drop the five leftover tables. |
| 036 | hand | `V036__student_session_country.sql` | carry the study country on a student-portal session. |
| 037 | hand | `V037__studentcore_country_scope.sql` | country scope across every table in STUDENTCORE. |
| 038 | hand | `V038__finance_quarter_uniqueness.sql` | one finance row per student per quarter, enforced. |
| 039 | hand | `V039__currency_and_fx_foundation.sql` | a currency master an accounting system can rely on, and real |
| 040 | hand | `V040__fiscal_calendar.sql` | fiscal years and periods. |
| 041 | hand | `V041__chart_of_accounts.sql` | the chart of accounts (الدليل المحاسبي). |
| 042 | hand | `V042__fund_chain.sql` | the fund chain: appropriation -> allocation -> batch -> advance, |
| 043 | hand | `V043__accounting_permissions.sql` | UMS permissions for the accounting module. |
| 044 | hand | `V044__grant_accounting_to_admins.sql` | grant the accounting permissions to the administrator roles. |
| 045 | hand | `V045__derive_accounting_from_existing_data.sql` | populate the accounting tables from the operational data that |
| 046 | hand | `V046__backfill_country_scope.sql` | Backfill the country on rows that lost it |
| 047 | hand | `V047__audit_trail_country.sql` | A country on the audit trail |
| 048 | hand | `V048__standardise_egypt_country_id.sql` | Standardise Egypt on STUDY_COUNTRY_IN_ID = 1 |
| 049 | hand | `V049__country_scope_reference_data.sql` | A country dimension for the study-years and study-amount lookups |
| 050 | hand | `V050__report_type_settings_for_new_reports.sql` | report type settings for the new reports |
| 051 | hand | `V051__student_scholarship_data_import.sql` | STUDENT_SCHOLARSHIP_DATA_IMPORT: landing table for the quarterly |
| 052 | hand | `V052__etl_lookup_alias.sql` | ETL_LOOKUP_ALIAS: the spellings a human has decided the resolver should |
| 053 | hand | `V053__student_data_staging_edits.sql` | STUDENT_DATA_STAGING: mark the rows a person has corrected by hand, so |
| 054 | hand | `V054__create_student_data_staging.sql` | STUDENT_DATA_STAGING: create the table the scholarship ETL loads into |
| 055 | hand | `V055__insert_default_bank_branches.sql` | STUDY_FINANCE_BANK_BRANCH: a default "Main Branch" for every bank that |
| 056 | hand | `V056__financial_audit_view_scope.sql` | V_Financial_Audit: filter on the amount, as the entity does |
| 057 | hand | `V057__financial_data_form_v2.sql` | Financial Data Form V2 ("كشوفات المساعدة المالية الربعية") |
| 058 | hand | `V058__reconcile_hand_applied_schema.sql` | put every hand-applied column and table into the numbered run. |
| 059 | hand | `V059__indexes_and_uniques_the_code_assumes.sql` | the indexes and unique constraints the application already relies on. |
| 060 | hand | `V060__tenant_policy_staging_and_import.sql` | bring the two ETL landing tables under the tenant policy. |
| 061 | hand | `V061__drop_backup_tables_20260824.sql` | drop the three hand-made backup tables of 24 August 2026. |
| 062 | hand | `V062__enable_tenant_policy.sql` | turn the tenant row-level-security policy on. |
| 063 | hand | `V063__payment_batch_fx_budget_permissions.sql` | UMS permissions for the round-2 accounting screens: payment |
| 064 | hand | `V064__chart_of_accounts_revenue.sql` | the revenue chart of accounts (مفردات الموارد), from the ministry's own form. |
| 065 | hand | `V065__tenant_policy_coverage_and_audit_columns.sql` | close the remaining tenant-policy gaps, and three small columns the |
| 066 | hand | `V066__schema_hygiene.sql` | schema hygiene: the objects that exist only by hand, the ones that |
| 067 | hand | `V067__fincore_audit_triggers.sql` | audit triggers on the FINCORE money tables. |
| 068 | hand | `V068__merge_duplicate_lookup_names.sql` | merge the lookup rows that share a name, then stop it recurring. |
| 069 | hand | `V069__grant_unassigned_permissions.sql` | grant the permissions that no role holds. |
| 070 | hand | `V070__dashboard_permissions.sql` | Dashboard permissions: the whole page, or one section at a time. |
| 071 | hand | `V071__dashboard_sections_per_user.sql` | Dashboard sections granted to every user, to be revoked per user. |
| 072 | hand | `V072__code_lists_own_names_colours_and_system_codes.sql` | The code lists own their names, colours, icons and "system" codes. |
| 073 | hand | `V073__new_code_lists_and_report_catalogue.sql` | The code lists that had no table, and the report catalogue. |
| 074 | hand | `V074__remaining_status_lists.sql` | Four status columns that still had no code list. |
| 075 | hand | `V075__report_views_read_what_is_recorded.sql` | Eleven report views read what is recorded, and nothing else. |
| 076 | hand | `V076__report_labels_named_by_java.sql` | The report labels Java asks for by name. |
| 077 | hand | `V077__report_labels_from_the_templates.sql` | The report labels the templates declare. |
| 078 | hand | `V078__scholarship_and_payment_reports_read_what_is_recorded.sql` | V078__scholarship_and_payment_reports_read_what_is_recorded.sql |
| 079 | hand | `V079__inline_template_labels_to_the_table.sql` | The inline bilingual labels the templates still carried. |
| 080 | hand | `V080__login_lockout_is_the_servers_decision.sql` | V080__login_lockout_is_the_servers_decision.sql |
| 081 | hand | `V081__payment_voucher_currency_and_amount_in_words.sql` | The payment voucher's amount column, and its التفقيط line. |
| 082 | hand | `V082__student_gender_code_is_required.sql` | A gender the reports cannot count is not a gender. |
| 083 | hand | `V083__approval_requests_keep_their_country.sql` | A request nobody can see is not pending; it is lost. |
| 084 | hand | `V084__remove_orphaned_scheduled_reports.sql` | A schedule owned by nobody, delivering to nobody. |
| 085 | hand | `V085__student_data_date_indexes.sql` | indexes on the STUDENT_DATA date columns the code filters on. |
| 086 | hand | `V086__hash_portal_session_tokens.sql` | Store student-portal session tokens as SHA-256 hashes. |
| 087 | hand | `V087__user_password_history.sql` | Password history for staff accounts. |
| 088 | hand | `V088__drop_users_password_salt.sql` | Drop the unused UMSCORE.Users.PasswordSalt column. |
| 089 | hand | `V089__permission_catalog_seed.sql` | Seed the permission codes the application enforces but the table lacked. |
| 090 | hand | `V090__least_privilege_app_login.sql` | A least-privilege login for the application. |
| 091 | hand | `V091__direct_grants_are_time_boxed.sql` | Direct permission grants are time-boxed exceptions. |
| 092 | hand | `V092__separation_of_duties.sql` | Separation of duties as data. |
| 093 | hand | `V093__positions_and_named_approvers.sql` | Staff positions, and approval levels that name who approves. |
| 094 | hand | `V094__explicit_admin_roles.sql` | Explicit administrator tiers as roles. |
| 095 | hand | `V095__wafar_write_key.sql` | Row-level backstop for rule N-04: only a country connection writes Wafar. |
| 096 | hand | `V096__four_eyes_approval_configs.sql` | Approval configurations for privileged administration (four-eyes). |
| 097 | Flyway | `V097__mfa.sql` | Multi-factor authentication (TOTP) for staff accounts. |
| 098 | Flyway | `V098__role_windows_and_wafar_correction.sql` | Time-bound roles, delegation, and the ministry's Wafar correction path. |
| 099 | Flyway | `V099__access_reviews.sql` | Quarterly access review (security plan S-04). |
| 100 | Flyway | `V100__session_binding.sql` | Session binding (security plan S-09). |
| 101 | Flyway | `V101__portal_hardening.sql` | Student portal hardening (security plan S-11). |
| 102 | Flyway | `V102__staff_country_policy.sql` | Row-Level Security on the staff tables. |
| 103 | Flyway | `V103__fix_quarterly_report_period_derivation.sql` | Quarterly Report: derive the period from the payment date |
| 104 | Flyway | `V104__bilingual_student_report_views.sql` | English reports print English data: the student report views |
| 105 | Flyway | `V105__bilingual_report_views.sql` | English reports print English data: university, statistics and payment views |
| 106 | Flyway | `V106__record_report_usage_and_export_size.sql` | the report-management screens report what is recorded |
| 107 | Flyway | `V107__bilingual_remaining_report_views.sql` | English reports print English data: the last of the printed views |
| 108 | Flyway | `V108__report_label_and_dimension_for_usage_and_charts.sql` | The two catalogue rows the templates need to stop printing constants. |
| 109 | Flyway | `V109__data_visualization_and_distribution_read_their_names.sql` | The last two reports that printed constants instead of data. |
| 110 | Flyway | `V110__dashboard_and_legacy_views_read_their_names.sql` | The dashboard aggregates and the last of the legacy views. |
| 111 | Flyway | `V111__seed_report_type_settings_chart_report.sql` | Seed REPORT_TYPE_SETTINGS for the chart/visualisation report ("تقرير الرسوم البيانية"). |
| 112 | Flyway | `V112__study_country_english_names.sql` | English names for study countries that have none (review item Q5). |
| 113 | Flyway | `V113__finance_export_permission.sql` | FINANCE.EXPORT: the permission behind the finance Excel export button. |
| 114 | Flyway | `V114__study_university_fee_arch_key.sql` | The fee log (سجل الرسوم) gets a key the application can generate. |
| 115 | Flyway | `V115__change_request_field_data_type.sql` | Each change-request field says what kind of value it takes. |
| 116 | Flyway | `V116__travel_ticket_document.sql` | A travel ticket can carry the ticket itself, as a PDF. |
| 117 | Flyway | `V117__notification_whatsapp_channel.sql` | WhatsApp as a notification channel. |
| 118 | Flyway | `V118__notification_whatsapp_template_mode.sql` | WhatsApp: free text, an approved template, or free text falling back |
| 119 | Flyway | `V119__fix_orphan_approval_roles.sql` | the approval levels that nobody but a super admin could sign. |
| 120 | Flyway | `V120__whatsapp_message_mode_code_list.sql` | the WhatsApp message mode becomes a code list. |
| 121 | Flyway | `V121__must_change_password_and_screen_approval_configs.sql` | Wave 1 of the 22 Sep 2026 plan (MASTER-STATUS-AND-PLAN-2026-09-22.md). |
| 122 | Flyway | `V122__english_names_quarters_permissions_approval_types.sql` | English names the screens printed in Arabic, or as raw codes |
| 123 | Flyway | `V123__info_request_kind_english_names.sql` | English names for the information-request kinds (plan W3-04, QA SP-007, D3). |
| 124 | Flyway | `V124__report_english_columns.sql` | English names for the export-manager report |
| 125 | Flyway | `V125__notices_english_and_source.sql` | System notices in both languages, and tied to what raised them |
| 126 | Flyway | `V126__fiscal_years_open_and_seed.sql` | Fiscal years: open them from the screen, seed the ones the quarters |
| 127 | Flyway | `V127__payment_auth_legacy_statuses.sql` | Payment authorisations with a legacy status become drafts again |
| 128 | Flyway | `V128__university_fee_key_sequence.sql` | University fees (رسوم الجامعة) get a key the application can generate |
| 129 | Flyway | `V129__location_hierarchy_foreign_keys.sql` | The location lookups get the foreign keys they always implied |
| 130 | Flyway | `V130__received_whom_documents_integrity.sql` | The received-by recipient and its attachments stop relying on the |
| 131 | Flyway | `V131__student_degree_lookup_duplicates.sql` | WD-05 (DEC-34) — two bad rows in MOHECORE.STUDENT_DEGREE. |
| 132 | Flyway | `V132__etl_degree_aliases_follow_the_legacy_family.sql` | WD-05 (DEC-35) — the import resolver is pointed at the same degree family |
| 133 | Flyway | `V133__portal_lookup_names.sql` | the names the student portal reads, in both languages, and one |
| 134 | Flyway | `V134__student_study_status_active_wording.sql` | the ACTIVE study status is "نشط", not "ثابت" (QA I18N-001, DSH-004). |
| 135 | Flyway | `V135__finance_received_status_not_specified.sql` | the finance rows nobody has been paid for say so (live QA H2, 23 Sep 2026). |
| 136 | Flyway | `V136__system_name_ministry_of_higher_education.sql` | the system's own name (live QA L4, 23 Sep 2026). |
| 137 | Flyway | `V137__student_data_arch_audit_trigger.sql` | an audit trail on the student archive (live QA H1, 23 Sep 2026). |
| 138 | Flyway | `V138__report_settings_ministry_of_finance.sql` | the ministry named on every printed report (live QA M1, 24 Sep 2026). |
| 139 | Flyway | `V139__finance_and_fee_archive_audit_triggers.sql` | an audit trail on the finance and fee archives (live QA H2, 24 Sep 2026). |
| 140 | Flyway | `V140__notification_log_recipient.sql` | who a notification actually went to (25 Sep 2026). |
| 141 | Flyway | `V141__student_info_requests_edit_permission.sql` | STUDENT_INFO_REQUESTS.EDIT: the permission behind raising, cancelling |
| 142 | Flyway | `V142__approval_config_edit_permission.sql` | APP_CONFIG.EDIT: the permission behind changing approval rules. |
| 143 | Flyway | `V143__report_cards_follow_data_permission.sql` | three report cards are shown to the people who may read their data. |
| 144 | Flyway | `V144__alert_reads_per_user.sql` | an alert is read per user, not for everybody (live QA N10, FIX-PLAN P5-3). |
| 145 | Flyway | `V145__allocation_maker_codes_to_finance_officer.sql` | the maker codes leave the seeded admin roles; FINANCE_OFFICER makes allocations. |
| 146 | Flyway | `V146__budget_edit_to_admin_roles.sql` | the seeded Admin and Super Admin roles edit the budget appropriation. |
| 147 | Flyway | `V147__bank_branch_and_study_lookup_foreign_keys.sql` | bank branches, study amounts and study years get the foreign keys they |
| 148 | Flyway | `V148__finance_integrity_fixes.sql` | finance integrity fixes (system test report FIN-01, FIN-03, FIN-16). |
| 149 | Flyway | `V149__portal_and_retention_fixes.sql` | the student-status date retention measures from, and two portal |
