/*
    Turn the staff row-level-security policy on.  —  DBA script, not a migration.

    V102 creates UMSCORE.staff_country_policy with STATE = OFF. This switches it
    on, and it is deliberately *not* a numbered migration: Flyway applies those on
    startup (pom.xml copies docs/scripts/V*.sql into db/migration), so an enable
    script among them would turn staff RLS on at the next boot of every
    environment, unattended, with nobody watching whether sign-in still works.
    The same reasoning that put enable_tde.sql in this folder puts this one here.

    The tenant policy's own two-step (V031 creates OFF, V062 enables) is the
    precedent; the two were months and a deployment apart, which is the gap this
    script preserves.

    What changes when it is on
    --------------------------
    A connection scoped to one attaché can no longer read another attaché's staff
    accounts, sessions, roles, direct grants, action log, MFA state or
    access-review items — enforced by the database, underneath `StaffCountrySpec`
    and `UmsUserPolicy` rather than instead of them. And no connection can assign,
    move or remove a `UserCountries` row for an attaché it does not itself hold.

    Ministry tiers and system work are unaffected (TenantAll = 1). A connection
    with no context at all sees no staff account except one awaiting assignment,
    which is why the prerequisites are not optional: a build that does not set
    SESSION_CONTEXT, or does not carry the two sign-in elevations, comes up unable
    to authenticate anybody.

    Prerequisites — confirm all four before running
    ----------------------------------------------
      1. V102 applied (the policy exists; this script checks).
      2. The application build of 7 September 2026 or later is deployed, with
         JwtAuthFilter.establishingIdentity and TenantContextFilter.STAFF_AUTH_PATHS.
         Check the running jar, not the branch.
      3. V102 section 5(a) shows TenantAll and TenantCountryIds arriving on the
         application's own connections.
      4. V102 section 5(c) answers correctly for a real account, run as the
         application login rather than as sa.

    Then, immediately after running this: sign in. If it fails, run the rollback
    below before doing anything else — with this policy on and a build that cannot
    authenticate, nobody can get in to fix it, including you.

    Rollback
    --------
        ALTER SECURITY POLICY UMSCORE.staff_country_policy WITH (STATE = OFF);

    Immediate, and needs no redeployment.

    Guarded and idempotent: enabling an enabled policy is a no-op. Run with
    sqlcmd -I -f 65001 -b.
*/

SET NOCOUNT ON;
SET XACT_ABORT ON;
GO

IF NOT EXISTS (SELECT 1 FROM sys.security_policies WHERE name = 'staff_country_policy')
BEGIN
    PRINT 'staff_country_policy does not exist here (V102 not applied) — nothing to do.';
    RETURN;
END

IF EXISTS (SELECT 1 FROM sys.security_policies WHERE name = 'staff_country_policy' AND is_enabled = 1)
    PRINT 'staff_country_policy is already enabled.';
ELSE
BEGIN
    ALTER SECURITY POLICY UMSCORE.staff_country_policy WITH (STATE = ON);
    PRINT 'staff_country_policy enabled.';
END
GO

SELECT name, is_enabled,
       (SELECT COUNT(DISTINCT target_object_id) FROM sys.security_predicates sp WHERE sp.object_id = p.object_id) AS tables_covered
  FROM sys.security_policies p
 WHERE name IN ('staff_country_policy', 'tenant_country_policy')
 ORDER BY name;
GO
