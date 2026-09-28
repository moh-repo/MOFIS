/*
    V102 — Row-Level Security on the staff tables.

    Why
    ---
    Every tenanted resource in this system is defended three times: a permission
    code, a policy class, and a predicate in the database. Staff accounts had
    only the first two. `StaffCountrySpec` hides an attaché's officers from
    another attaché's administrator and `UmsUserPolicy` refuses to act on them,
    but both live in Java, and both protect only the queries that remember to
    ask. UMSCORE has never had a policy of its own — `tenant_country_policy`
    (V031, V032, V062) covers MOHECORE and nothing else — so a finder added
    without `UmsUserService.scoped()` reads every attaché's staff, and nothing
    below it disagrees. That is the shape of the bug V031 was written to end,
    still open on the one table that lists the people.

    This script closes it. `TenantContextFilter` already resolves a scope per
    request and `TenantContextDataSource` already stamps it on every connection,
    so no new session key is needed: the two the tenant predicate reads are
    enough.

    !!  This policy is created with STATE = OFF  !!
    ------------------------------------------------------------------------
    It must stay off until the application build of 7 September 2026 or later is
    deployed. Two paths in that build read staff tables *before* a scope exists
    and were given an explicit elevation for it:

      * JwtAuthFilter — the session lookup, the S-09 binding check and the
        authority snapshot all run before TenantContextFilter has resolved
        anything. Without the elevation the account does not find itself, an
        empty snapshot is *cached* under its id, and every request afterwards is
        a 403 with nothing in the log to say why.
      * TenantContextFilter.STAFF_AUTH_PATHS — /login, /captcha, /refresh,
        /reset-password and the three MFA steps that carry the pending token.
        Without the elevation a correct password is refused, because the row
        holding the hash is behind the predicate the sign-in would establish.

    On an older build, with this policy on, nobody can sign in.

    Deployment order (the V031 → V062 shape, for the same reasons):
      1. Apply this script. Nothing changes: the policy exists but is off.
      2. Deploy the application build above.
      3. Confirm sign-in works and the context is arriving (sections 4 and 5).
      4. Run adhoc/enable_staff_country_policy.sql. Roll back with STATE = OFF; immediate, no redeploy.

    The predicate
    -------------
    A staff account has a *set* of countries, not a country column, so the
    predicate is a correlated subquery over UMSCORE.UserCountries. Verified on
    this server (7 Sep 2026) that this is allowed even though UserCountries is
    itself under the policy: the function's own read of that table is not
    filtered, so there is no recursion, and both predicates evaluate normally.

    A staff row is visible when:

      1. TenantAll = 1                      — ministry tiers and system work;
      2. the account holds one of the caller's countries; or
      3. the account holds no active country at all — awaiting assignment.

    Clause 2 is an INTERSECTION, and the Java rule it backs is a SUBSET
    --------------------------------------------------------------------
    This divergence is deliberate and is the one design decision in this script.

    `StaffCountrySpec` asks "are this account's attachés *within* mine", so a
    ministry-wide account is not a country admin's to administer even though it
    covers that country. The right question for an administration screen, and
    the wrong one for a database predicate: enforced here it would hide every
    ministry-wide account from every country connection, and the ordinary reads
    that resolve a name — who signed this approval, who is the named approver,
    who changed this row — would silently come back empty across the whole
    application.

    So the database asks the weaker question: *do we share an attaché at all*.
    A Jordan connection still cannot read a Saudi-only officer, which is the
    leak worth a backstop. The strict rule stays in `StaffCountrySpec`, where it
    can afford to be strict because it guards one screen rather than every
    query. Layer 3 is a floor, not a copy of layer 2.

    Clause 3 is likewise ungated. `StaffCountrySpec` shows an unassigned account
    to administrators only; here it is visible to anyone, because the account
    that has just been created and not yet assigned is the one the creation
    wizard is holding, and hiding it in the database breaks the wizard for the
    admin who is allowed to see it anyway.

    Nothing here reproduces the tier matrix. `UmsUserPolicy` is layer 2 and is
    not reimplemented in SQL; this predicate is about countries only.

    The block on UserCountries
    --------------------------
    The one write worth stopping in the database is handing out an attaché you
    do not hold — that is how a country admin would otherwise widen its own
    reach. The block predicate for that is not this function (which tests the
    row's *user*, and a user already inside your scope stays inside it however
    many countries you add) but the tenant predicate on the row's *country*.
    MOHECORE.fn_tenant_predicate is exactly that test and is reused unchanged.

    Performance
    -----------
    Per row, as V031's is. UMSCORE.Users is in the hundreds and UserCountries
    held 36 rows when this was written; the subquery is an index seek on
    UserId. If staff ever reaches a scale where this matters, measure before
    assuming — the same warning V031 carries about STRING_SPLIT applies.

    Rollback
    --------
        ALTER SECURITY POLICY UMSCORE.staff_country_policy WITH (STATE = OFF);   -- immediate
        DROP SECURITY POLICY UMSCORE.staff_country_policy;                       -- complete
        DROP FUNCTION UMSCORE.fn_staff_predicate;

    The function is SCHEMABINDING and reads UMSCORE.UserCountries, so that
    table's UserId, CountryId and IsActive columns cannot be altered or dropped
    while it exists. Drop the policy and the function first if a later migration
    needs to change them.

    Idempotent. Run with sqlcmd -I -f 65001 -b.
*/
SET NOCOUNT ON;
SET XACT_ABORT ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;
GO

/* ── 0. Drop the policy first ──────────────────────────────────────────────────
 * A security policy schema-binds the function it names, so the function cannot
 * be replaced while the policy exists (Msg 3729). Dropping first makes this
 * script re-runnable; section 3 recreates it, always in the OFF state.
 * ──────────────────────────────────────────────────────────────────────────── */
IF EXISTS (SELECT 1 FROM sys.security_policies WHERE name = 'staff_country_policy')
    DROP SECURITY POLICY UMSCORE.staff_country_policy;
GO

/* ── 1. The predicate ─────────────────────────────────────────────────────────
 * Inline table-valued and SCHEMABINDING, both required of a security predicate.
 * Returns a row — "visible" — when the caller may see this staff account.
 * ──────────────────────────────────────────────────────────────────────────── */
CREATE OR ALTER FUNCTION UMSCORE.fn_staff_predicate(@UserId BIGINT)
RETURNS TABLE
WITH SCHEMABINDING
AS
RETURN
    SELECT 1 AS is_visible
    WHERE
        -- 1. Ministry tiers and system work (startup, migrations, @Scheduled jobs,
        --    and the sign-in paths that run before a scope exists).
        TRY_CAST(CAST(SESSION_CONTEXT(N'TenantAll') AS NVARCHAR(4)) AS BIT) = 1

        -- 2. We share an attaché. Intersection, not subset — see the header.
        OR EXISTS (
            SELECT 1
            FROM UMSCORE.UserCountries uc
            WHERE uc.UserId = @UserId
              AND uc.IsActive = 1
              AND uc.CountryId IN (
                  SELECT TRY_CAST(value AS INT)
                  FROM STRING_SPLIT(CAST(SESSION_CONTEXT(N'TenantCountryIds') AS NVARCHAR(4000)), ',')
                  WHERE TRY_CAST(value AS INT) IS NOT NULL
              )
        )

        -- 3. Awaiting assignment: no active attaché, so it belongs to nobody yet
        --    and the account nobody can see is the account nobody can fix.
        OR NOT EXISTS (
            SELECT 1
            FROM UMSCORE.UserCountries uc
            WHERE uc.UserId = @UserId
              AND uc.IsActive = 1
        );
GO

/* ── 2. Sanity check before the policy names it ────────────────────────────── */
IF OBJECT_ID('UMSCORE.fn_staff_predicate') IS NULL
    THROW 50100, 'UMSCORE.fn_staff_predicate was not created; not creating the policy.', 1;
GO

/* ── 3. The policy ────────────────────────────────────────────────────────────
 * FILTER on the account and on everything that hangs off it: a satellite row
 * says as much as the account does. UserSessions carries an address and a
 * device; UserActions carries who granted what to whom; UserMfa says whether a
 * second factor is on; AccessReviewItems is the attestation trail.
 *
 * BLOCK on UserCountries only, and on the row's COUNTRY rather than its user —
 * see the header. It stops an administrator assigning, moving or removing an
 * attaché it does not itself hold.
 * ──────────────────────────────────────────────────────────────────────────── */
CREATE SECURITY POLICY UMSCORE.staff_country_policy
    ADD FILTER PREDICATE UMSCORE.fn_staff_predicate(Id)     ON UMSCORE.Users,

    ADD FILTER PREDICATE UMSCORE.fn_staff_predicate(UserId) ON UMSCORE.UserCountries,
    ADD BLOCK  PREDICATE MOHECORE.fn_tenant_predicate(CountryId) ON UMSCORE.UserCountries AFTER INSERT,
    ADD BLOCK  PREDICATE MOHECORE.fn_tenant_predicate(CountryId) ON UMSCORE.UserCountries AFTER UPDATE,
    ADD BLOCK  PREDICATE MOHECORE.fn_tenant_predicate(CountryId) ON UMSCORE.UserCountries BEFORE UPDATE,
    ADD BLOCK  PREDICATE MOHECORE.fn_tenant_predicate(CountryId) ON UMSCORE.UserCountries BEFORE DELETE,

    ADD FILTER PREDICATE UMSCORE.fn_staff_predicate(UserId) ON UMSCORE.UserRoles,
    ADD FILTER PREDICATE UMSCORE.fn_staff_predicate(UserId) ON UMSCORE.UserGroupMemberships,
    ADD FILTER PREDICATE UMSCORE.fn_staff_predicate(UserId) ON UMSCORE.UserPermissions,
    ADD FILTER PREDICATE UMSCORE.fn_staff_predicate(UserId) ON UMSCORE.UserSessions,
    ADD FILTER PREDICATE UMSCORE.fn_staff_predicate(UserId) ON UMSCORE.UserActions,
    ADD FILTER PREDICATE UMSCORE.fn_staff_predicate(UserId) ON UMSCORE.UserPasswordHistory,
    ADD FILTER PREDICATE UMSCORE.fn_staff_predicate(UserId) ON UMSCORE.PasswordHistory,
    ADD FILTER PREDICATE UMSCORE.fn_staff_predicate(UserId) ON UMSCORE.UserMfa,
    ADD FILTER PREDICATE UMSCORE.fn_staff_predicate(UserId) ON UMSCORE.UserMfaRecoveryCodes,
    ADD FILTER PREDICATE UMSCORE.fn_staff_predicate(UserId) ON UMSCORE.AccessReviewItems
WITH (STATE = OFF);
GO

/* ── 4. What is covered ────────────────────────────────────────────────────── */
PRINT '=== tables under the staff policy ===';
SELECT OBJECT_NAME(sp.target_object_id) AS table_name,
       sp.predicate_type_desc,
       sp.operation_desc,
       p.is_enabled AS policy_enabled
FROM sys.security_predicates sp
JOIN sys.security_policies p ON p.object_id = sp.object_id
WHERE p.name = 'staff_country_policy'
ORDER BY table_name, sp.predicate_type_desc, sp.operation_desc;
GO

/* ── 5. Before enabling, confirm all four ───────────────────────────────
 *
 * a) The application is up and its connections carry the context. Both keys
 *    should be present; if they are NULL, do NOT enable.
 *
 *      SELECT session_id,
 *             CAST(SESSION_CONTEXT(N'TenantAll') AS NVARCHAR(4))         AS tenant_all,
 *             CAST(SESSION_CONTEXT(N'TenantCountryIds') AS NVARCHAR(64)) AS tenant_ids
 *      FROM sys.dm_exec_sessions
 *      WHERE program_name LIKE '%Microsoft JDBC%';
 *
 * b) The deployed build has the two elevations. Both of these must be present
 *    in the running jar, not merely in the branch:
 *      JwtAuthFilter.establishingIdentity, TenantContextFilter.STAFF_AUTH_PATHS.
 *
 * c) The predicate answers correctly for a real account. Run as the application
 *    login (mohe_app), not as sa, so that the function's read of UserCountries
 *    is exercised with the permissions it will really have:
 *
 *      EXEC sp_set_session_context @key = N'TenantAll',        @value = N'0', @read_only = 0;
 *      EXEC sp_set_session_context @key = N'TenantCountryIds', @value = N'1004', @read_only = 0;
 *      SELECT UserName FROM UMSCORE.Users ORDER BY Id;   -- Jordan's staff, plus the unassigned
 *
 * d) Sign-in works after enabling — check it first, and be ready to run the
 *    rollback in the header. A failure here locks everybody out, including you.
 * ──────────────────────────────────────────────────────────────────────────── */
