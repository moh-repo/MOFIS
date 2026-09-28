/*
    Wafar spends that name no attaché  --  live QA M1 (FIX-PLAN P5-12, 26 Sep 2026).
    READ-ONLY: this script changes nothing. Run it any time.

    Why: Wafar is held per attaché, and since the M1 fix a spend is checked against its own attaché's
    balance when it is recorded, edited, approved or corrected. A spend with no attaché
    (STUDY_COUNTRY_IN_ID NULL) cannot be checked: a PENDING one can no longer be approved (409, "names
    no attaché") until it is corrected, and an APPROVED one is counted against no attaché's balance.
    Read-only on dev (26 Sep 2026, this script run): 11 rows — 8 APPROVED (22 750.00), 1 PENDING (8 768.00,
    spend 16), 1 CANCELLED (950.00), 1 REJECTED (2 300.00). All 11 are OTHER beneficiaries (staff, suppliers,
    a property company) that name no student and no travel ticket, so for every one the attaché must be
    chosen by hand; 10 are seed rows from 2024-2025, created by user 1.

    What it lists: every such spend, with its beneficiary, the student it names (if any), that student's
    attaché (the likely right value), the travel ticket it paid (if any) and that ticket student's
    attaché. Correct each on the Wafar screen (correction request), which records who changed it and why;
    this script proposes, it does not decide.

        sqlcmd -S localhost,1433 -d MOHE -I -f 65001 -b -i probe_wafar_spend_without_attache.sql
*/
SET NOCOUNT ON;

EXEC sp_set_session_context @key = N'TenantAll', @value = N'1';

SELECT ws.STATUS, COUNT(*) AS spends, SUM(ws.AMOUNT) AS total
FROM MOHECORE.WAFAR_SPENDING ws
WHERE ws.STUDY_COUNTRY_IN_ID IS NULL
GROUP BY ws.STATUS
ORDER BY ws.STATUS;

SELECT ws.SPENDING_ID,
       ws.STATUS,
       ws.AMOUNT,
       ws.SPENDING_DATE,
       ws.BENEFICIARY_TYPE,
       ws.REFERENCE_NUMBER,
       COALESCE(ws.PAYEE_NAME_EN, ws.PAYEE_NAME_AR)          AS payee,
       ws.STUDENT_ID,
       sd.STUDY_COUNTRY_IN                                   AS student_attache_id,
       sci.STUDY_COUNTRY_IN_NAME_EN                          AS student_attache,
       ws.TRAVEL_TICKET_ID,
       tsd.STUDY_COUNTRY_IN                                  AS ticket_student_attache_id,
       CASE
           WHEN sd.STUDY_COUNTRY_IN IS NOT NULL THEN 'use the student''s attaché'
           WHEN tsd.STUDY_COUNTRY_IN IS NOT NULL THEN 'use the ticket student''s attaché'
           ELSE 'no student: the attaché must be chosen by hand'
       END                                                   AS proposal,
       ws.CREATED_BY,
       ws.CREATED_AT
FROM MOHECORE.WAFAR_SPENDING ws
LEFT JOIN MOHECORE.STUDENT_DATA sd ON sd.STUDENT_ID = ws.STUDENT_ID
LEFT JOIN MOHECORE.STUDY_COUNTRY_IN sci ON sci.STUDY_COUNTRY_IN_ID = sd.STUDY_COUNTRY_IN
LEFT JOIN MOHECORE.STUDENT_TRAVEL_TICKET tt ON tt.TRAVEL_TICKET_ID = ws.TRAVEL_TICKET_ID
LEFT JOIN MOHECORE.STUDENT_DATA tsd ON tsd.STUDENT_ID = tt.STUDENT_ID
WHERE ws.STUDY_COUNTRY_IN_ID IS NULL
ORDER BY CASE ws.STATUS WHEN 'PENDING' THEN 0 WHEN 'APPROVED' THEN 1 ELSE 2 END, ws.SPENDING_ID;
