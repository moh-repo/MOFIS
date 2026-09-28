/*
    V090 — A least-privilege login for the application.

    Security plan S-01 (6 Sep 2026).

    Why
    ---
    The application connects as a member of db_owner (verified in V025). A SQL
    injection, or a compromised application server, can therefore alter the
    schema, disable tenant_country_policy, read every schema including
    MOHENTracer, and grant itself anything. Row-Level Security applies to
    db_owner (V031), so the tenancy work is honest — but nothing stopped the
    same connection from turning the policy off.

    What
    ----
    Creates login `mohe_app` and database user `mohe_app` holding, per
    application schema: SELECT, INSERT, UPDATE, DELETE, EXECUTE, and nothing
    else. No ALTER, no CONTROL, no membership in a fixed role. The RLS policy
    still applies (it applies to everyone), and sp_set_session_context needs no
    grant. Backup and restore, which need server-level rights, run through a
    separate login the application takes from MOHE_ADMIN_DATASOURCE_USERNAME /
    _PASSWORD (BackupService.openDedicatedConnection) — give that login the
    `db_backupoperator` role plus `dbcreator` at server level, or simply keep
    the previous login for it.

    The query engine already has its own read-only login (V025) and is not
    touched here.

    Running it
    ----------
    Run as a sysadmin. Replace the placeholder password (or better, pass it at
    run time with sqlcmd -v APP_PASSWORD="...") and then set
        MOHE_DATASOURCE_USERNAME=mohe_app
        MOHE_DATASOURCE_PASSWORD=<the password>
    on the application host. Verify with section 3 while still connected as
    the old login. Roll back by pointing the environment variables back at the
    previous login; nothing here removes it.

    Idempotent: every CREATE and GRANT is guarded.
*/
SET NOCOUNT ON;

:setvar APP_PASSWORD "CHANGE-ME-before-running-9x!"

-- 1. Server login ------------------------------------------------------------
IF NOT EXISTS (SELECT 1 FROM sys.server_principals WHERE name = N'mohe_app')
BEGIN
    CREATE LOGIN [mohe_app] WITH PASSWORD = N'$(APP_PASSWORD)', CHECK_POLICY = ON, CHECK_EXPIRATION = OFF,
        DEFAULT_DATABASE = [MOHE];
    PRINT 'login mohe_app created';
END
ELSE
    PRINT 'login mohe_app already exists';

-- 2. Database user and schema grants ------------------------------------------
USE [MOHE];
IF NOT EXISTS (SELECT 1 FROM sys.database_principals WHERE name = N'mohe_app')
BEGIN
    CREATE USER [mohe_app] FOR LOGIN [mohe_app] WITH DEFAULT_SCHEMA = [MOHECORE];
    PRINT 'user mohe_app created';
END

DECLARE @schemas TABLE (name sysname);
INSERT INTO @schemas VALUES
    (N'MOHECORE'), (N'UMSCORE'), (N'STUDENTCORE'), (N'ALERTCORE'), (N'ALERTEVENTCASES'),
    (N'AUDITLOG'), (N'FINCORE'), (N'MOHE_ALERTS'), (N'MOHECORE_ERR'), (N'MOHENTracer');

DECLARE @s sysname, @sql NVARCHAR(MAX);
DECLARE c CURSOR LOCAL FAST_FORWARD FOR SELECT name FROM @schemas;
OPEN c; FETCH NEXT FROM c INTO @s;
WHILE @@FETCH_STATUS = 0
BEGIN
    IF SCHEMA_ID(@s) IS NOT NULL
    BEGIN
        SET @sql = N'GRANT SELECT, INSERT, UPDATE, DELETE, EXECUTE ON SCHEMA::' + QUOTENAME(@s) + N' TO [mohe_app];';
        EXEC sp_executesql @sql;
        PRINT CONCAT('granted DML+EXECUTE on ', @s);
    END
    FETCH NEXT FROM c INTO @s;
END
CLOSE c; DEALLOCATE c;

-- Explicit denials, so a later "GRANT ALL" by habit cannot widen quietly.
-- Not DENY CONTROL: CONTROL covers CONNECT, so that deny locks the login out
-- of the database altogether (found on the first dev run, 6 Sep 2026).
-- Named permissions instead: no DDL, no ownership, no backups, no principals.
REVOKE CONTROL ON DATABASE::[MOHE] FROM [mohe_app];
DENY ALTER ANY SCHEMA TO [mohe_app];
DENY ALTER ANY SECURITY POLICY TO [mohe_app];
DENY ALTER, TAKE OWNERSHIP, BACKUP DATABASE, BACKUP LOG, CHECKPOINT,
     CREATE TABLE, CREATE VIEW, CREATE PROCEDURE, CREATE FUNCTION, CREATE SCHEMA, CREATE TYPE,
     CREATE ASSEMBLY, CREATE SYNONYM, CREATE ROLE,
     ALTER ANY USER, ALTER ANY ROLE, ALTER ANY APPLICATION ROLE, ALTER ANY DATABASE AUDIT
  TO [mohe_app];

-- Backup/restore are deliberately NOT granted: BackupService uses the admin login.

-- Catalog metadata (sys.tables, sys.security_predicates, sys.sql_modules...):
-- read-only, and what the integration suite checks the tenant policy with
-- (AccountingCountryScopeTest). A login holding DML on every application
-- schema learns nothing from it that the schema does not already tell it.
GRANT VIEW DEFINITION TO [mohe_app];

-- Flyway (O-29) records its runs in dbo.flyway_schema_history under its own
-- login; the application only reads the table (FlywayHistoryTest, the
-- runbook probes). Granted when the table already exists; otherwise the
-- first Flyway run creates it and this script is re-run (idempotent).
IF OBJECT_ID('dbo.flyway_schema_history', 'U') IS NOT NULL
BEGIN
    GRANT SELECT ON dbo.flyway_schema_history TO [mohe_app];
    PRINT 'granted SELECT on dbo.flyway_schema_history';
END

-- 3. Verify (run while still connected as the old login) -------------------------
SELECT dp.name AS principal, dp.type_desc,
       (SELECT COUNT(*) FROM sys.database_role_members rm JOIN sys.database_principals r ON r.principal_id = rm.role_principal_id
         WHERE rm.member_principal_id = dp.principal_id AND r.name = 'db_owner') AS is_db_owner
  FROM sys.database_principals dp WHERE dp.name = N'mohe_app';

SELECT class_desc, major_id, permission_name, state_desc
  FROM sys.database_permissions
 WHERE grantee_principal_id = USER_ID(N'mohe_app')
 ORDER BY class_desc, permission_name;

-- 4. After switching the application to mohe_app, from the application:
--    SELECT SUSER_SNAME(), IS_ROLEMEMBER('db_owner')   -> mohe_app, 0
--    and a full pass of verify.ps1 (the integration tests exercise every DML path).
