/*
    V062 — turn the tenant row-level-security policy on.

    V031 created MOHECORE.tenant_country_policy with STATE = OFF and V032/V037/V060
    widened it, all deliberately leaving it off: the application that sets the
    session context (TenantContextDataSource / TenantContextFilter, August 2026)
    had to be deployed first, and the P1 blockers of the first review had to be
    fixed. Both are done, and the 27 August 2026 revision ran the full backend
    suite against a database with the policy enabled.

    What changes when it is on
    --------------------------
    Every read of a covered table returns only rows whose country the session
    may see, and inserts/updates into another country are refused by the block
    predicates — enforced by the database, on top of the service-layer scope.
    A session with no context at all sees nothing (fn_tenant_predicate passes
    only TenantAll = 1, a NULL country, or a listed id), which is why V031's
    section 4 must be confirmed before running this: an application build that
    does not set SESSION_CONTEXT comes up looking empty.

    Prerequisites (from V031's header)
    ----------------------------------
      1. V029, V030, V031 (and V032, V037, V060) applied.
      2. The application build with TenantContextDataSource deployed.
      3. V031 section 4 shows the session keys arriving on a request.

    Rollback: ALTER SECURITY POLICY MOHECORE.tenant_country_policy WITH (STATE = OFF);
    takes effect immediately and needs no redeployment.

    Idempotent: enabling an enabled policy is a no-op.
*/

SET NOCOUNT ON;
SET XACT_ABORT ON;
GO

IF NOT EXISTS (SELECT 1 FROM sys.security_policies WHERE name = 'tenant_country_policy')
BEGIN
    PRINT 'V062: tenant_country_policy does not exist here (V031 not applied) — nothing to do.';
    RETURN;
END

IF EXISTS (SELECT 1 FROM sys.security_policies WHERE name = 'tenant_country_policy' AND is_enabled = 1)
    PRINT 'V062: tenant_country_policy is already enabled.';
ELSE
BEGIN
    ALTER SECURITY POLICY MOHECORE.tenant_country_policy WITH (STATE = ON);
    PRINT 'V062: tenant_country_policy enabled.';
END
GO

SELECT name, is_enabled,
       (SELECT COUNT(DISTINCT target_object_id) FROM sys.security_predicates sp WHERE sp.object_id = p.object_id) AS tables_covered
  FROM sys.security_policies p
 WHERE name = 'tenant_country_policy';
GO
