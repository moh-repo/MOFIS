/*
    V026 — saved queries for the query builder.

    Stores the query as its structured spec, in JSON, and never as SQL.

    That is the point of the table rather than an implementation detail. A saved
    SQL string would be a stored statement that later runs with whatever
    privileges the engine has, and any tightening of the validation rules would
    not apply to it. A saved spec is re-validated on every run: identifiers are
    matched against the schema catalogue again, the deny list applies again, and
    the country scope applied is the scope of whoever is running it — not
    whoever saved it.

    Sharing
    -------
    IS_SHARED lets a query be visible to other administrators. It does not carry
    any data with it: a shared query is a shape, and the rows it returns are
    still narrowed to the country scope of the person who runs it.

    The query builder endpoints are already restricted to SYSTEM_ADMIN, so every
    row here belongs to an administrator.

    Until this script is applied the saved-query endpoints will fail and their
    tests skip; the query builder itself works without it.

    Apply in SSMS.
*/

IF NOT EXISTS (
    SELECT 1 FROM INFORMATION_SCHEMA.TABLES
    WHERE TABLE_SCHEMA = 'MOHECORE' AND TABLE_NAME = 'SAVED_QUERY'
)
BEGIN
    CREATE TABLE MOHECORE.SAVED_QUERY (
        SAVED_QUERY_ID  BIGINT IDENTITY(1,1) NOT NULL,
        QUERY_NAME      NVARCHAR(200)  NOT NULL,
        DESCRIPTION     NVARCHAR(1000) NULL,
        -- Denormalised from the spec so the list can be filtered and read
        -- without parsing every row's JSON.
        SOURCE_NAME     NVARCHAR(128)  NOT NULL,
        SPEC_JSON       NVARCHAR(MAX)  NOT NULL,
        OWNER_USERNAME  NVARCHAR(200)  NOT NULL,
        IS_SHARED       BIT            NOT NULL CONSTRAINT DF_SAVED_QUERY_IS_SHARED DEFAULT (0),
        CREATED_AT      DATETIME2(3)   NOT NULL CONSTRAINT DF_SAVED_QUERY_CREATED   DEFAULT (SYSUTCDATETIME()),
        UPDATED_AT      DATETIME2(3)   NULL,
        CONSTRAINT PK_SAVED_QUERY PRIMARY KEY CLUSTERED (SAVED_QUERY_ID)
    );

    -- One name per owner, so saving over a name updates rather than duplicates.
    CREATE UNIQUE INDEX UX_SAVED_QUERY_OWNER_NAME
        ON MOHECORE.SAVED_QUERY (OWNER_USERNAME, QUERY_NAME);

    CREATE INDEX IX_SAVED_QUERY_SHARED
        ON MOHECORE.SAVED_QUERY (IS_SHARED) INCLUDE (QUERY_NAME, SOURCE_NAME, OWNER_USERNAME);
END
GO
