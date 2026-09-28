/*
    V031 — Row-Level Security on every tenanted table.

    Why
    ---
    Until now tenancy has been enforced entirely by convention: each query
    remembers its own country predicate. That works right up until one query
    forgets, and one did — six student reports were filtering on the student's
    *nationality* instead of their study country (V030), and nothing in the
    system noticed, because there was no layer below the query to disagree.

    Row-Level Security moves the predicate into the database. A query that omits
    its country filter now returns the caller's rows rather than everyone's, and
    a query that tries to write outside the caller's countries is refused.

    Verified on this server: RLS applies to sysadmin and db_owner too. The
    application connects as a member of db_owner, and with a policy enabled and
    no session context set it sees zero rows. That is the protection working, and
    it is also the operational hazard below.

    !!  These policies are created with STATE = OFF  !!
    ------------------------------------------------------------------------
    They must stay off until the application that sets SESSION_CONTEXT is
    deployed (config/tenant/TenantContextDataSource.java). With the policies on
    and nothing setting the context, EVERY query returns zero rows — the
    application would come up looking empty rather than broken.

    Deployment order:
      1. Apply V029, V030 and this script. Nothing changes yet: the policies
         exist but are off.
      2. Deploy the application build containing TenantContextDataSource,
         TenantContextFilter and TenantContext.
      3. Confirm the context is arriving (section 4 below).
      4. Enable the policies (section 5). Roll back by setting STATE = OFF again;
         that takes effect immediately and needs no redeployment.

    The predicate
    -------------
    Two session keys, both set per connection by the application:

        TenantAll         '1' for ministry users and system work, else '0'
        TenantCountryIds  comma-separated STUDY_COUNTRY_IN ids, e.g. '1004,1005'

    A NULL tenant column means "ministry-wide" — the convention already used by
    MOHE_QUARTER.COUNTRY_ID (V021) and by SAVED_QUERY / SCHEDULED_REPORT rows that
    predate V029 — so such rows stay visible to everyone.

    Performance
    -----------
    The predicate runs per row. At current volumes (941 students, 5051 year
    results, 3764 archived finance rows) this is immaterial, and V029 indexed
    every tenant column. STRING_SPLIT per row will not scale indefinitely; if a
    table grows past a few million rows, measure before assuming this is free.

    Apply in SSMS, after V029 and V030.
*/

SET NOCOUNT ON;
SET QUOTED_IDENTIFIER ON;
GO

/* ── 0. Run once: skip everything if the policy already exists (DB-02) ─────────
 * This section used to DROP the policy so that the function below could be
 * altered (a policy schema-binds its function, Msg 3729), claiming the policy
 * was "recreated below in the same state it was in". It was not: section 2
 * recreates it with V031's predicate list and STATE = OFF. Re-running this
 * script on a live database therefore switched row-level security OFF and
 * dropped every predicate added since (V032, V037, V041, V042, V060, V065,
 * V095, V102) — silently, since the script succeeds. V062 had turned it on.
 *
 * So the script now only ever creates. When tenant_country_policy exists, the
 * remaining batches up to section 3 are compiled but not executed
 * (SET NOEXEC ON) and the policy, its predicates and its state are left exactly
 * as they are. To change the predicate function or the predicate list, write a
 * new numbered script that drops and recreates the policy with the COMPLETE
 * current predicate list and its CURRENT state (see V065 / V102 for the pattern).
 *
 * Scripts up to V096 were applied by hand and sit below Flyway's baseline (96),
 * so editing this one changes no Flyway checksum.
 * ──────────────────────────────────────────────────────────────────────────── */
IF EXISTS (SELECT 1 FROM sys.security_policies WHERE name = 'tenant_country_policy')
BEGIN
    PRINT 'V031: MOHECORE.tenant_country_policy already exists; leaving the policy, its predicates '
        + 'and its state unchanged (sections 1-2 skipped).';
    SET NOEXEC ON;
END
GO

/* ── 1. The predicate function ─────────────────────────────────────────────────
 * Inline table-valued, SCHEMABINDING — both required for a security predicate.
 * Returns a row (meaning "visible") when the caller may see this country.
 * ──────────────────────────────────────────────────────────────────────────── */
CREATE OR ALTER FUNCTION MOHECORE.fn_tenant_predicate(@CountryId INT)
RETURNS TABLE
WITH SCHEMABINDING
AS
RETURN
    SELECT 1 AS is_visible
    WHERE
        -- Ministry users and system work (migrations, startup, elevated jobs).
        TRY_CAST(CAST(SESSION_CONTEXT(N'TenantAll') AS NVARCHAR(4)) AS BIT) = 1
        -- Rows with no country belong to the ministry as a whole.
        OR @CountryId IS NULL
        -- Otherwise the row's country must be one of the caller's.
        OR @CountryId IN (
            SELECT TRY_CAST(value AS INT)
            FROM STRING_SPLIT(CAST(SESSION_CONTEXT(N'TenantCountryIds') AS NVARCHAR(4000)), ',')
            WHERE TRY_CAST(value AS INT) IS NOT NULL
        );
GO

/* ── 2. The policy ─────────────────────────────────────────────────────────────
 * FILTER predicates hide rows from reads. BLOCK predicates stop writes that
 * would put a row outside the caller's countries — without them a scoped user
 * could not see another country's data but could still create or move rows into
 * it.
 *
 * STUDENT_DATA is the root: everything else derives its country from it.
 * ──────────────────────────────────────────────────────────────────────────── */
CREATE SECURITY POLICY MOHECORE.tenant_country_policy
    ADD FILTER PREDICATE MOHECORE.fn_tenant_predicate(STUDY_COUNTRY_IN)    ON MOHECORE.STUDENT_DATA,
    ADD BLOCK  PREDICATE MOHECORE.fn_tenant_predicate(STUDY_COUNTRY_IN)    ON MOHECORE.STUDENT_DATA AFTER INSERT,
    ADD BLOCK  PREDICATE MOHECORE.fn_tenant_predicate(STUDY_COUNTRY_IN)    ON MOHECORE.STUDENT_DATA AFTER UPDATE,

    ADD FILTER PREDICATE MOHECORE.fn_tenant_predicate(STUDY_COUNTRY_IN_ID) ON MOHECORE.STUDENT_FINANCE,
    ADD BLOCK  PREDICATE MOHECORE.fn_tenant_predicate(STUDY_COUNTRY_IN_ID) ON MOHECORE.STUDENT_FINANCE AFTER INSERT,
    ADD BLOCK  PREDICATE MOHECORE.fn_tenant_predicate(STUDY_COUNTRY_IN_ID) ON MOHECORE.STUDENT_FINANCE AFTER UPDATE,

    ADD FILTER PREDICATE MOHECORE.fn_tenant_predicate(STUDY_COUNTRY_IN_ID) ON MOHECORE.STUDENT_FINANCE_ARCH,
    ADD FILTER PREDICATE MOHECORE.fn_tenant_predicate(STUDY_COUNTRY_IN_ID) ON MOHECORE.STUDENT_FINANCE_WAFAR,

    ADD FILTER PREDICATE MOHECORE.fn_tenant_predicate(STUDY_COUNTRY_IN_ID) ON MOHECORE.STUDENT_EXTENSION,
    ADD BLOCK  PREDICATE MOHECORE.fn_tenant_predicate(STUDY_COUNTRY_IN_ID) ON MOHECORE.STUDENT_EXTENSION AFTER INSERT,

    ADD FILTER PREDICATE MOHECORE.fn_tenant_predicate(STUDY_COUNTRY_IN_ID) ON MOHECORE.STUDENT_LOAN,
    ADD BLOCK  PREDICATE MOHECORE.fn_tenant_predicate(STUDY_COUNTRY_IN_ID) ON MOHECORE.STUDENT_LOAN AFTER INSERT,

    ADD FILTER PREDICATE MOHECORE.fn_tenant_predicate(STUDY_COUNTRY_IN_ID) ON MOHECORE.STUDENT_BOOKS_ALLOWANCE,
    ADD BLOCK  PREDICATE MOHECORE.fn_tenant_predicate(STUDY_COUNTRY_IN_ID) ON MOHECORE.STUDENT_BOOKS_ALLOWANCE AFTER INSERT,

    ADD FILTER PREDICATE MOHECORE.fn_tenant_predicate(STUDY_COUNTRY_IN_ID) ON MOHECORE.STUDENT_YEAR_RESULT,
    ADD BLOCK  PREDICATE MOHECORE.fn_tenant_predicate(STUDY_COUNTRY_IN_ID) ON MOHECORE.STUDENT_YEAR_RESULT AFTER INSERT,

    ADD FILTER PREDICATE MOHECORE.fn_tenant_predicate(STUDY_COUNTRY_IN_ID) ON MOHECORE.STUDENT_DOCUMENTS,
    ADD BLOCK  PREDICATE MOHECORE.fn_tenant_predicate(STUDY_COUNTRY_IN_ID) ON MOHECORE.STUDENT_DOCUMENTS AFTER INSERT,

    ADD FILTER PREDICATE MOHECORE.fn_tenant_predicate(STUDY_COUNTRY_IN_ID) ON MOHECORE.PAYMENT_AUTH,
    ADD BLOCK  PREDICATE MOHECORE.fn_tenant_predicate(STUDY_COUNTRY_IN_ID) ON MOHECORE.PAYMENT_AUTH AFTER INSERT,

    ADD FILTER PREDICATE MOHECORE.fn_tenant_predicate(STUDY_COUNTRY_IN_ID) ON MOHECORE.WAFAR_RECONCILIATION,

    ADD FILTER PREDICATE MOHECORE.fn_tenant_predicate(STUDY_COUNTRY_IN_ID) ON MOHECORE.SAVED_QUERY,
    ADD FILTER PREDICATE MOHECORE.fn_tenant_predicate(STUDY_COUNTRY_IN_ID) ON MOHECORE.SCHEDULED_REPORT,
    ADD FILTER PREDICATE MOHECORE.fn_tenant_predicate(STUDY_COUNTRY_IN_ID) ON MOHECORE.REPORT_ACCESS_LOG
WITH (STATE = OFF);
GO

-- End of the create-only part (DB-02): always resume execution here.
SET NOEXEC OFF;
GO

/* ── 3. What is covered ────────────────────────────────────────────────────── */
PRINT '=== tables under the tenant policy ===';
SELECT OBJECT_NAME(sp.target_object_id) AS table_name,
       sp.predicate_type_desc,
       p.is_enabled AS policy_enabled
FROM sys.security_predicates sp
JOIN sys.security_policies p ON p.object_id = sp.object_id
WHERE p.name = 'tenant_country_policy'
ORDER BY table_name, sp.predicate_type_desc;
GO

/* ── 4. Check the application is setting the context ───────────────────────────
 * Run this while the application is up and serving requests. Both keys should be
 * present on its sessions. If they are NULL, do NOT enable the policies.
 * ──────────────────────────────────────────────────────────────────────────── */
/*
SELECT session_id,
       CAST(SESSION_CONTEXT(N'TenantAll') AS NVARCHAR(4))        AS tenant_all,
       CAST(SESSION_CONTEXT(N'TenantCountryIds') AS NVARCHAR(64)) AS tenant_ids
FROM sys.dm_exec_sessions
WHERE program_name LIKE '%jTDS%' OR program_name LIKE '%Microsoft JDBC%';
*/

/* ── 5. Enable / disable ───────────────────────────────────────────────────────
 * Only after step 4 confirms the context is arriving.
 * ──────────────────────────────────────────────────────────────────────────── */
-- ALTER SECURITY POLICY MOHECORE.tenant_country_policy WITH (STATE = ON);
-- ALTER SECURITY POLICY MOHECORE.tenant_country_policy WITH (STATE = OFF);   -- rollback
