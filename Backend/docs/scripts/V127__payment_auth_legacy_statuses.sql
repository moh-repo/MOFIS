/*
    V127 — Payment authorisations with a legacy status become drafts again
    (plan W4-03; review G4).

    Since 19 Sep (review F6) a payment authorisation is edited, deleted or
    released only while it is DRAFT, and its status is one of the codes of
    LU_PAYMENT_AUTH_STATUS (DRAFT, APPROVED, PAID). Rows written before that,
    through the form's free status list, carry NULL, 'PENDING' or a code in
    another case, and could no longer be edited, deleted or released.

    1. A code in another case or with spaces ('approved ', 'Paid') becomes the code.
    2. NULL, '' and PENDING (in any case) become DRAFT: none was ever released.
    3. Anything else is listed and left alone: guessing whether it was paid is
       not a script's call.

    Idempotent. Run with sqlcmd -I -f 65001 -b.
*/
SET NOCOUNT ON;
SET XACT_ABORT ON;
EXEC sp_set_session_context @key = N'TenantAll', @value = N'1';

UPDATE pa
   SET pa.AUTH_STATUS = c.CODE
  FROM MOHECORE.PAYMENT_AUTH pa
  JOIN MOHECORE.LU_PAYMENT_AUTH_STATUS c ON UPPER(LTRIM(RTRIM(pa.AUTH_STATUS))) = c.CODE
 WHERE pa.AUTH_STATUS <> c.CODE COLLATE Latin1_General_CS_AS;
PRINT CONCAT('V127: statuses normalised to their code: ', @@ROWCOUNT);

UPDATE MOHECORE.PAYMENT_AUTH
   SET AUTH_STATUS = N'DRAFT'
 WHERE AUTH_STATUS IS NULL
    OR LTRIM(RTRIM(AUTH_STATUS)) = N''
    OR UPPER(LTRIM(RTRIM(AUTH_STATUS))) = N'PENDING';
PRINT CONCAT('V127: legacy NULL/PENDING rows made DRAFT: ', @@ROWCOUNT);

DECLARE @left int = (SELECT COUNT(*) FROM MOHECORE.PAYMENT_AUTH pa
                      WHERE NOT EXISTS (SELECT 1 FROM MOHECORE.LU_PAYMENT_AUTH_STATUS c WHERE c.CODE = pa.AUTH_STATUS));
IF @left > 0
BEGIN
    PRINT CONCAT('V127: ', @left, ' rows still carry a status that is not a code; correct them by hand:');
    SELECT AUTH_STATUS, COUNT(*) AS rows_
      FROM MOHECORE.PAYMENT_AUTH pa
     WHERE NOT EXISTS (SELECT 1 FROM MOHECORE.LU_PAYMENT_AUTH_STATUS c WHERE c.CODE = pa.AUTH_STATUS)
     GROUP BY AUTH_STATUS;
END
