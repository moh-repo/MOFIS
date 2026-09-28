/*
    V115 — Each change-request field says what kind of value it takes.

    Review round 2, item R2b (19 Sep 2026). A student asked to change their
    birth date to "1-1-2000" from a free-text box; the request was accepted,
    and approving it failed because the server only understood an ISO instant.
    The portal form and the review screen draw an input for every field in
    MOHECORE.CHANGE_REQUEST_FIELD, but the table only told them whether a field
    was a dropdown (LOOKUP_LIST), so every date was a text box.

    What
    ----
    CHANGE_REQUEST_FIELD.DATA_TYPE, one of TEXT, DATE, LOOKUP, EMAIL, PHONE:
      * DATE   — studentBirthdate, studentPassportIssueDate, studentPassportExpireDate
      * LOOKUP — every row with a LOOKUP_LIST
      * EMAIL  — studentEmail
      * PHONE  — studentCellphone, studentHomeNumber, studentEmergencyContact
      * TEXT   — the rest, and the default for a row added later.

    The screens pick their input from it (a date picker for DATE). The server
    does not trust it for storage: a value is parsed by the student field's own
    Java type, at submission and again at approval, so a mislabelled row cannot
    let a bad value through.

    Rollback
    --------
        ALTER TABLE MOHECORE.CHANGE_REQUEST_FIELD DROP CONSTRAINT CK_CHANGE_REQUEST_FIELD_DATA_TYPE;
        ALTER TABLE MOHECORE.CHANGE_REQUEST_FIELD DROP CONSTRAINT DF_CHANGE_REQUEST_FIELD_DATA_TYPE;
        ALTER TABLE MOHECORE.CHANGE_REQUEST_FIELD DROP COLUMN DATA_TYPE;

    Idempotent. Run with sqlcmd -I -f 65001 -b.
*/
SET NOCOUNT ON;
SET XACT_ABORT ON;

EXEC sp_set_session_context @key = N'TenantAll', @value = N'1';

IF COL_LENGTH(N'MOHECORE.CHANGE_REQUEST_FIELD', N'DATA_TYPE') IS NULL
BEGIN
    ALTER TABLE MOHECORE.CHANGE_REQUEST_FIELD
        ADD DATA_TYPE NVARCHAR(20) NOT NULL
            CONSTRAINT DF_CHANGE_REQUEST_FIELD_DATA_TYPE DEFAULT (N'TEXT');
    PRINT 'V115: DATA_TYPE added';
END
ELSE
    PRINT 'V115: DATA_TYPE already exists';
GO

IF NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE name = N'CK_CHANGE_REQUEST_FIELD_DATA_TYPE')
    ALTER TABLE MOHECORE.CHANGE_REQUEST_FIELD
        ADD CONSTRAINT CK_CHANGE_REQUEST_FIELD_DATA_TYPE
        CHECK (DATA_TYPE IN (N'TEXT', N'DATE', N'LOOKUP', N'EMAIL', N'PHONE'));
GO

UPDATE MOHECORE.CHANGE_REQUEST_FIELD
   SET DATA_TYPE = CASE
           WHEN CODE IN (N'studentBirthdate', N'studentPassportIssueDate', N'studentPassportExpireDate') THEN N'DATE'
           WHEN LOOKUP_LIST IS NOT NULL AND LTRIM(RTRIM(LOOKUP_LIST)) <> N''                          THEN N'LOOKUP'
           WHEN CODE = N'studentEmail'                                                                 THEN N'EMAIL'
           WHEN CODE IN (N'studentCellphone', N'studentHomeNumber', N'studentEmergencyContact')       THEN N'PHONE'
           ELSE N'TEXT' END
 WHERE DATA_TYPE = N'TEXT';
PRINT CONCAT('V115: typed ', @@ROWCOUNT, ' rows');
GO

/* ---- Verify --------------------------------------------------------------------- */
SELECT CODE, LOOKUP_LIST, DATA_TYPE FROM MOHECORE.CHANGE_REQUEST_FIELD ORDER BY SORT_ORDER;
