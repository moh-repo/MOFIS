/*
    V025 — a read-only login for the query builder's composed statements.

    OPTIONAL, and the only script here that creates a principal. Read it before
    running it, and change the password.

    Why
    ---
    The application connects as a member of db_owner. I verified this:

        SELECT SUSER_SNAME(), USER_NAME(), IS_ROLEMEMBER('db_owner')
        -- sa, dbo, 1

    QueryExecutionService validates every identifier against the schema catalogue,
    binds every value, and composes only SELECT. Those controls are the first
    line and they are meant to hold. But they are application code, and a gap in
    application code that runs as db_owner is a gap that can change data.

    Pointing the engine at a login granted only db_datareader turns the worst
    case from a modified row into a query that fails. It does not replace the
    validation; it bounds what a failure of the validation can do.

    After applying
    --------------
    Add to application.properties (or the environment):

        mohe.query-engine.datasource.url=jdbc:sqlserver://<host>:1433;databaseName=<db>;encrypt=true;trustServerCertificate=true
        mohe.query-engine.datasource.username=mohe_query_reader
        mohe.query-engine.datasource.password=<the password set below>

    QueryEngineDataSourceConfig picks these up. Left unset, the engine shares the
    application's connection and logs a warning at startup saying so.

    Not applying this is a legitimate choice — the feature works without it. The
    warning in the log is there so the state is visible rather than assumed.
*/

/* ---------------------------------------------------------------------------
   1. Server login. CHANGE THIS PASSWORD before running.
   --------------------------------------------------------------------------- */
IF NOT EXISTS (SELECT 1 FROM sys.server_principals WHERE name = 'mohe_query_reader')
BEGIN
    CREATE LOGIN mohe_query_reader
        WITH PASSWORD = 'CHANGE_ME_BEFORE_RUNNING',
             CHECK_POLICY = ON,
             DEFAULT_DATABASE = [master];
END
GO

/* ---------------------------------------------------------------------------
   2. Database user, read-only.

      db_datareader grants SELECT on every table and view in the database. The
      query engine narrows that much further — it will only read what the
      catalogue lists and what its own deny list permits — but the database-side
      grant is deliberately simple, because a complex grant is one that drifts.

      DENY on the credential-bearing tables is belt and braces: the catalogue
      already omits those columns, so the engine cannot name them. The DENY means
      that stops being the only thing preventing it.
   --------------------------------------------------------------------------- */
IF NOT EXISTS (SELECT 1 FROM sys.database_principals WHERE name = 'mohe_query_reader')
BEGIN
    CREATE USER mohe_query_reader FOR LOGIN mohe_query_reader;
END
GO

ALTER ROLE db_datareader ADD MEMBER mohe_query_reader;
GO

DENY SELECT ON MOHECORE.StudentPortalAccount TO mohe_query_reader;
DENY SELECT ON MOHECORE.StudentPortalSession TO mohe_query_reader;
DENY SELECT ON MOHECORE.NOTIFICATION_SETTINGS TO mohe_query_reader;
DENY SELECT ON MOHECORE.AUDIT_LOG TO mohe_query_reader;
GO

/* Explicitly withhold everything that writes or executes, in case the user is
   ever added to another role by accident. */
DENY INSERT, UPDATE, DELETE, ALTER, EXECUTE ON SCHEMA::MOHECORE TO mohe_query_reader;
GO

/* ---------------------------------------------------------------------------
   3. Verify. Expect is_datareader = 1 and both writer flags = 0.
   --------------------------------------------------------------------------- */
EXECUTE AS USER = 'mohe_query_reader';
SELECT USER_NAME()                        AS db_user,
       IS_ROLEMEMBER('db_datareader')     AS is_datareader,
       IS_ROLEMEMBER('db_datawriter')     AS is_datawriter,
       IS_ROLEMEMBER('db_owner')          AS is_db_owner;
REVERT;
GO
