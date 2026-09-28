/*
    V130 — The received-by recipient and its attachments stop relying on the
    application to keep them straight (plan W6-09, QA C12 / Q-11d).

    Why
    ---
    Three faults, one shape: the attachment table trusted code to do what the
    schema never enforced.

    1. STUDY_RECEIVED_WHOM_ID is handed out as MAX(id) + 1. Delete the highest
       recipient and the next one created takes the dead id — and with it every
       FIN_RECEIVED_WHOM_DOCUMENTS row still keyed to it, which is somebody
       else's passport scan under a new name. The service deletes the documents
       with the recipient to avoid exactly this, but that cascade is application
       code: it does not survive a DELETE in SSMS, a bulk load, or any path that
       does not go through it. It is also unguarded against two approvals being
       signed off at once, which read the same MAX and race for the key.

    2. FIN_RECEIVED_WHOM_DOCUMENTS.WHOM_ID has no foreign key, so a document can
       name a recipient that does not exist. On the review database exactly one
       attachment row exists and it is already such an orphan.

    3. V065 added STUDY_COUNTRY_IN_ID to the attachments, backfilled it once and
       enrolled it in the RLS policy — but wrote no trigger, unlike every other
       child table in V029. Nothing has stamped it since, so every upload after
       V065 carries NULL, and under the policy's BLOCK predicate an attache's
       upload is refused outright.

    What
    ----
    1. Orphan attachment rows are deleted, with the count printed. An attachment
       whose recipient is gone cannot be reached through the application and is
       precisely the litter the key below prevents.
    2. FK_FIN_RECEIVED_WHOM_DOCUMENTS_WHOM on WHOM_ID.
    3. MOHECORE.SEQ_STUDY_FIN_RECEIVED_WHOM, and the recipient key defaults to
       it, so an id is never reused (the V128 pattern).
    4. Two triggers, the pair V029 established: one stamps a new or moved
       attachment with its recipient's country, one carries a recipient's change
       of country down to its attachments.

    Idempotent; every step tests for its own result first.
    Run with sqlcmd -I -f 65001 -b.
*/
SET NOCOUNT ON;
SET XACT_ABORT ON;
EXEC sp_set_session_context @key = N'TenantAll', @value = N'1';
GO

/* ---- 1. Orphan attachments ---------------------------------------------------- */
DECLARE @orphans INT = (
    SELECT COUNT(*) FROM MOHECORE.FIN_RECEIVED_WHOM_DOCUMENTS d
     WHERE NOT EXISTS (SELECT 1 FROM MOHECORE.STUDY_FIN_RECEIVED_WHOM w
                        WHERE w.STUDY_RECEIVED_WHOM_ID = d.WHOM_ID));

IF @orphans > 0
BEGIN
    PRINT CONCAT('V130: deleting ', @orphans, ' attachment row(s) whose recipient no longer exists.');
    DELETE d FROM MOHECORE.FIN_RECEIVED_WHOM_DOCUMENTS d
     WHERE NOT EXISTS (SELECT 1 FROM MOHECORE.STUDY_FIN_RECEIVED_WHOM w
                        WHERE w.STUDY_RECEIVED_WHOM_ID = d.WHOM_ID);
END
ELSE
    PRINT 'V130: no orphan attachments.';
GO

/* ---- 2. The key the table always needed ---------------------------------------- */
IF EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name = 'FK_FIN_RECEIVED_WHOM_DOCUMENTS_WHOM')
    PRINT 'V130: FK_FIN_RECEIVED_WHOM_DOCUMENTS_WHOM already present.';
ELSE
BEGIN
    ALTER TABLE MOHECORE.FIN_RECEIVED_WHOM_DOCUMENTS WITH CHECK
        ADD CONSTRAINT FK_FIN_RECEIVED_WHOM_DOCUMENTS_WHOM
        FOREIGN KEY (WHOM_ID) REFERENCES MOHECORE.STUDY_FIN_RECEIVED_WHOM (STUDY_RECEIVED_WHOM_ID);
    PRINT 'V130: FK_FIN_RECEIVED_WHOM_DOCUMENTS_WHOM created.';
END
GO

/* ---- 3. A key that is never reused ---------------------------------------------- */
IF OBJECT_ID(N'MOHECORE.SEQ_STUDY_FIN_RECEIVED_WHOM', N'SO') IS NULL
BEGIN
    DECLARE @start INT = (SELECT ISNULL(MAX(STUDY_RECEIVED_WHOM_ID), 0) + 1
                            FROM MOHECORE.STUDY_FIN_RECEIVED_WHOM);
    DECLARE @sql NVARCHAR(400) = N'CREATE SEQUENCE MOHECORE.SEQ_STUDY_FIN_RECEIVED_WHOM AS INT START WITH '
        + CAST(@start AS NVARCHAR(12)) + N' INCREMENT BY 1 NO CYCLE;';
    EXEC (@sql);
    PRINT CONCAT('V130: sequence created, starting at ', @start);
END
ELSE
    PRINT 'V130: sequence already exists.';
GO

SET NOCOUNT ON;
DECLARE @old SYSNAME = (
    SELECT dc.name
      FROM sys.default_constraints dc
      JOIN sys.columns c ON c.object_id = dc.parent_object_id AND c.column_id = dc.parent_column_id
     WHERE dc.parent_object_id = OBJECT_ID(N'MOHECORE.STUDY_FIN_RECEIVED_WHOM')
       AND c.name = N'STUDY_RECEIVED_WHOM_ID');
IF @old IS NOT NULL AND @old <> N'DF_STUDY_FIN_RECEIVED_WHOM_ID'
BEGIN
    DECLARE @drop NVARCHAR(400) = N'ALTER TABLE MOHECORE.STUDY_FIN_RECEIVED_WHOM DROP CONSTRAINT '
        + QUOTENAME(@old) + N';';
    EXEC (@drop);
    PRINT CONCAT('V130: dropped default ', @old);
END

IF NOT EXISTS (SELECT 1 FROM sys.default_constraints WHERE name = N'DF_STUDY_FIN_RECEIVED_WHOM_ID'
                AND parent_object_id = OBJECT_ID(N'MOHECORE.STUDY_FIN_RECEIVED_WHOM'))
BEGIN
    ALTER TABLE MOHECORE.STUDY_FIN_RECEIVED_WHOM
        ADD CONSTRAINT DF_STUDY_FIN_RECEIVED_WHOM_ID
        DEFAULT (NEXT VALUE FOR MOHECORE.SEQ_STUDY_FIN_RECEIVED_WHOM) FOR STUDY_RECEIVED_WHOM_ID;
    PRINT 'V130: the recipient key now defaults to the sequence.';
END
GO

/* ---- 4. The country stamp V065 never wrote -------------------------------------- */

-- Catch up whatever was uploaded between V065 and now.
UPDATE d
   SET d.STUDY_COUNTRY_IN_ID = NULLIF(w.FINANCE_COUNTRY_ID, 0)
  FROM MOHECORE.FIN_RECEIVED_WHOM_DOCUMENTS d
  JOIN MOHECORE.STUDY_FIN_RECEIVED_WHOM w ON w.STUDY_RECEIVED_WHOM_ID = d.WHOM_ID
 WHERE d.STUDY_COUNTRY_IN_ID IS NULL
   AND NULLIF(w.FINANCE_COUNTRY_ID, 0) IS NOT NULL;
GO

/*
    The child-side stamp. The trailing WHERE is load-bearing, as V029 explains:
    without it the trigger's own UPDATE fires the trigger again.
*/
CREATE OR ALTER TRIGGER MOHECORE.TR_FIN_RECEIVED_WHOM_DOCUMENTS_TENANT
ON MOHECORE.FIN_RECEIVED_WHOM_DOCUMENTS AFTER INSERT, UPDATE
AS
BEGIN
    SET NOCOUNT ON;
    IF NOT EXISTS (SELECT 1 FROM inserted) RETURN;

    UPDATE d
       SET d.STUDY_COUNTRY_IN_ID = NULLIF(w.FINANCE_COUNTRY_ID, 0)
      FROM MOHECORE.FIN_RECEIVED_WHOM_DOCUMENTS d
      JOIN inserted i ON i.DOCUMENT_ID = d.DOCUMENT_ID
      JOIN MOHECORE.STUDY_FIN_RECEIVED_WHOM w ON w.STUDY_RECEIVED_WHOM_ID = d.WHOM_ID
     WHERE ISNULL(d.STUDY_COUNTRY_IN_ID, -1) <> ISNULL(NULLIF(w.FINANCE_COUNTRY_ID, 0), -1);
END;
GO

/*
    The parent-side cascade: a recipient can be edited into another country, and
    its attachments have to follow or they become invisible to the people who
    own them.
*/
CREATE OR ALTER TRIGGER MOHECORE.TR_STUDY_FIN_RECEIVED_WHOM_TENANT_CASCADE
ON MOHECORE.STUDY_FIN_RECEIVED_WHOM AFTER UPDATE
AS
BEGIN
    SET NOCOUNT ON;
    IF NOT UPDATE(FINANCE_COUNTRY_ID) RETURN;

    UPDATE d
       SET d.STUDY_COUNTRY_IN_ID = NULLIF(i.FINANCE_COUNTRY_ID, 0)
      FROM MOHECORE.FIN_RECEIVED_WHOM_DOCUMENTS d
      JOIN inserted i ON i.STUDY_RECEIVED_WHOM_ID = d.WHOM_ID
     WHERE ISNULL(d.STUDY_COUNTRY_IN_ID, -1) <> ISNULL(NULLIF(i.FINANCE_COUNTRY_ID, 0), -1);
END;
GO

/* ---- Checks --------------------------------------------------------------------- */
IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name = 'FK_FIN_RECEIVED_WHOM_DOCUMENTS_WHOM')
    THROW 51300, 'V130: the attachment foreign key is missing', 1;
IF OBJECT_ID(N'MOHECORE.SEQ_STUDY_FIN_RECEIVED_WHOM', N'SO') IS NULL
    THROW 51301, 'V130: the recipient sequence is missing', 1;
IF OBJECT_ID(N'MOHECORE.TR_FIN_RECEIVED_WHOM_DOCUMENTS_TENANT', N'TR') IS NULL
    THROW 51302, 'V130: the attachment country trigger is missing', 1;
PRINT 'V130: done.';
GO
