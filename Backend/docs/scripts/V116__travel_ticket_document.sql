/*
    V116 — A travel ticket can carry the ticket itself, as a PDF.

    Review round 2, item T3 (19 Sep 2026). Students asked to see their tickets in
    the portal; the ticket record had no attachment and there was no document type
    for one.

    What
    ----
    1. MOHECORE.STUDENT_TRAVEL_TICKET.DOCUMENT_ID, nullable, referencing
       MOHECORE.STUDENT_DOCUMENTS(DOCUMENT_ID). The PDF is an ordinary student
       document (encrypted in the student's folder); the ticket points at it.
    2. The document type "تذكرة سفر" / "Travel ticket". STUDENT_DOCUMENTS stores the
       type by name, and the application (StudentTravelTicketService.TICKET_DOCUMENT_TYPE)
       looks for exactly this name.

    The application uploads the PDF before the ticket is submitted for approval, with
    IS_ACTIVE = 0, and makes it active when the approved ticket is written, so a
    rejected ticket's file never shows in the student's documents.

    Rollback
    --------
        ALTER TABLE MOHECORE.STUDENT_TRAVEL_TICKET DROP CONSTRAINT FK_STUDENT_TRAVEL_TICKET_DOCUMENT;
        ALTER TABLE MOHECORE.STUDENT_TRAVEL_TICKET DROP COLUMN DOCUMENT_ID;
        DELETE FROM MOHECORE.DOCUMENT_TYPES WHERE DOCUMENT_TYPE_NAME = N'تذكرة سفر';

    Idempotent. Run with sqlcmd -I -f 65001 -b.
*/
SET NOCOUNT ON;
SET XACT_ABORT ON;

EXEC sp_set_session_context @key = N'TenantAll', @value = N'1';

IF COL_LENGTH(N'MOHECORE.STUDENT_TRAVEL_TICKET', N'DOCUMENT_ID') IS NULL
BEGIN
    ALTER TABLE MOHECORE.STUDENT_TRAVEL_TICKET ADD DOCUMENT_ID INT NULL;
    PRINT 'V116: DOCUMENT_ID added';
END
ELSE
    PRINT 'V116: DOCUMENT_ID already exists';
GO

IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name = N'FK_STUDENT_TRAVEL_TICKET_DOCUMENT')
BEGIN
    ALTER TABLE MOHECORE.STUDENT_TRAVEL_TICKET
        ADD CONSTRAINT FK_STUDENT_TRAVEL_TICKET_DOCUMENT
        FOREIGN KEY (DOCUMENT_ID) REFERENCES MOHECORE.STUDENT_DOCUMENTS (DOCUMENT_ID);
    PRINT 'V116: foreign key added';
END
GO

IF NOT EXISTS (SELECT 1 FROM MOHECORE.DOCUMENT_TYPES
                WHERE DOCUMENT_TYPE_NAME = N'تذكرة سفر' OR DOCUMENT_TYPE_NAME_EN = N'Travel Ticket')
BEGIN
    INSERT INTO MOHECORE.DOCUMENT_TYPES (DOCUMENT_TYPE_NAME, DOCUMENT_TYPE_NAME_EN, DOCUMENT_TYPE_DESCRIPTION, IS_ACTIVE, SORT_ORDER)
    VALUES (N'تذكرة سفر', N'Travel Ticket', N'Air ticket attached to a travel ticket record (PDF)', 1,
            (SELECT ISNULL(MAX(SORT_ORDER), 0) + 1 FROM MOHECORE.DOCUMENT_TYPES));
    PRINT 'V116: document type added';
END
GO

/* ---- Verify --------------------------------------------------------------------- */
SELECT COL_LENGTH(N'MOHECORE.STUDENT_TRAVEL_TICKET', N'DOCUMENT_ID') AS document_id_column,
       (SELECT COUNT(*) FROM MOHECORE.DOCUMENT_TYPES WHERE DOCUMENT_TYPE_NAME = N'تذكرة سفر') AS ticket_type_rows;
