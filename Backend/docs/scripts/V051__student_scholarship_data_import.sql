/*
================================================================================
V051 — STUDENT_SCHOLARSHIP_DATA_IMPORT: landing table for the quarterly
        financial-aid spreadsheets the attachés send in
================================================================================

Why
---
Each attaché sends the ministry one Excel workbook per quarter — "كشوفات
المساعدة المالية الربعية" — listing every student it expects to pay that
quarter. Those sheets arrive by mail and are read by eye today. This table is
where an upload lands so the rows can be queried, reconciled and later promoted
into the operational tables.

It is a landing table, not a master table: nothing here is authoritative, and
no other table references it. Re-importing appends a new batch rather than
replacing the old one, which is what the IMPORTED_AT / SOURCE_FILE columns are
for — they are how you tell one upload from another.

The three groups of columns
---------------------------
1. The grid itself (STUDENT_NAME … FINANCIAL_NOTES). One row per student line
   in the sheet, exactly as the ministry specified it.

2. The sheet's header block (HDR_*). Rows 1-8 of the workbook carry the
   republic, the ministry, the issuing department, the quarter, the year and
   four pages of policy notes — none of which is in the grid, and all of which
   is needed to know what a batch of rows *is*. They are per-file values
   repeated on every row of the batch: denormalised on purpose, so that one row
   of this table is self-describing without a join.

   HDR_STATEMENT_DATE stays text. The sample carries '17.08.2026' on a Q3-2025
   statement, which is not a Gregorian statement date and not reliably a Hijri
   one either; storing it raw keeps the fact without inventing a calendar for
   it.

3. Provenance (COUNTRY_ID … IMPORTED_BY). Which attaché the batch belongs to,
   which archived file it came from, which row of that file, and who uploaded
   it. The archived workbook lives at
   uploads/countries/country_{COUNTRY_ID}/dataImportXLS/ and SOURCE_FILE is the
   path relative to the uploads root.

Applied by hand, like V001-V050 — this project runs spring.jpa.hibernate.
ddl-auto=none and does not carry a migration runner.
================================================================================
*/

IF OBJECT_ID('MOHECORE.STUDENT_SCHOLARSHIP_DATA_IMPORT', 'U') IS NOT NULL
BEGIN
    PRINT 'MOHECORE.STUDENT_SCHOLARSHIP_DATA_IMPORT already exists - nothing to do.';
END
ELSE
BEGIN
    CREATE TABLE MOHECORE.[STUDENT_SCHOLARSHIP_DATA_IMPORT]
    (
        [ID]                    INT IDENTITY(1,1) NOT NULL,

        -- ── The grid (header row of the sheet, column by column) ────────────
        [STUDENT_NAME]          NVARCHAR(250)     NULL,   -- اسم الطالب
        [ENTITY]                NVARCHAR(150)     NULL,   -- الجهة
        [COUNTRY]               NVARCHAR(100)     NULL,   -- الدولة
        [SCHOLARSHIP_YEAR]      INT               NULL,   -- عام الإيفاد
        [DEGREE]                NVARCHAR(150)     NULL,   -- الدرجة
        [SPECIALIZATION]        NVARCHAR(250)     NULL,   -- التخصص
        [DECISION_NO]           NVARCHAR(50)      NULL,   -- رقم القرار
        [STUDY_DURATION]        INT               NULL,   -- مدة الدراسة
        [CURRENT_LEVEL]         INT               NULL,   -- المستوى الحالي
        [FIRST_DISBURSEMENT]    INT               NULL,   -- أول مصرف
        [AMOUNT]                DECIMAL(18,2)     NULL,   -- المبلغ
        [STUDENT_SIGNATURE]     NVARCHAR(250)     NULL,   -- توقيع الطالب
        [NOTES]                 NVARCHAR(MAX)     NULL,   -- ملاحظات
        [FINANCIAL_NOTES]       NVARCHAR(MAX)     NULL,   -- ملاحظات المالية

        -- ── The sheet's header block, above the grid ───────────────────────
        [HDR_REPUBLIC]          NVARCHAR(150)     NULL,   -- الجمهورية اليمنية
        [HDR_MINISTRY]          NVARCHAR(250)     NULL,   -- وزارة المالية
        [HDR_DEPARTMENT]        NVARCHAR(250)     NULL,   -- الإدارة العامة للبعثات - إدارة مالية البعثات
        [HDR_REPORT_TITLE]      NVARCHAR(250)     NULL,   -- كشوفات المساعدة المالية الربعية
        [HDR_ENTITY]            NVARCHAR(250)     NULL,   -- الجهة: <value>
        [HDR_QUARTER]           NVARCHAR(50)      NULL,   -- الربع: <الأول|الثاني|الثالث|الرابع>
        [HDR_QUARTER_NO]        INT               NULL,   -- the same, as 1-4, when it can be read
        [HDR_YEAR]              NVARCHAR(20)      NULL,   -- العام <2025م>
        [HDR_YEAR_NO]           INT               NULL,   -- the same, as 2025, when it can be read
        [HDR_STATEMENT_DATE]    NVARCHAR(50)      NULL,   -- raw; see the note above
        -- The four numbered notes under 'ملاحظات هامة:'. A fifth or later note
        -- is appended to the fourth rather than dropped.
        [HDR_NOTE_1]            NVARCHAR(MAX)     NULL,
        [HDR_NOTE_2]            NVARCHAR(MAX)     NULL,
        [HDR_NOTE_3]            NVARCHAR(MAX)     NULL,
        [HDR_NOTE_4]            NVARCHAR(MAX)     NULL,

        -- ── Provenance ─────────────────────────────────────────────────────
        [COUNTRY_ID]            INT               NULL,   -- MOHECORE.STUDY_COUNTRY_IN
        [SOURCE_FILE]           NVARCHAR(400)     NULL,   -- path under the uploads root
        [SOURCE_SHEET]          NVARCHAR(150)     NULL,
        [SOURCE_ROW]            INT               NULL,   -- 1-based row in that sheet
        [IMPORTED_AT]           DATETIME2(0)      NOT NULL
            CONSTRAINT [DF_STUDENT_SCHOLARSHIP_DATA_IMPORT_IMPORTED_AT] DEFAULT SYSUTCDATETIME(),
        [IMPORTED_BY]           INT               NULL,   -- UMSCORE.Users.Id

        CONSTRAINT [PK_STUDENT_SCHOLARSHIP_DATA]
            PRIMARY KEY ([ID])
    );

    -- Every read of this table is "one attaché's most recent batch".
    CREATE INDEX [IX_STUDENT_SCHOLARSHIP_DATA_IMPORT_COUNTRY]
        ON MOHECORE.[STUDENT_SCHOLARSHIP_DATA_IMPORT] ([COUNTRY_ID], [IMPORTED_AT] DESC);

    -- Reconciliation looks a student up by name within a batch.
    CREATE INDEX [IX_STUDENT_SCHOLARSHIP_DATA_IMPORT_STUDENT]
        ON MOHECORE.[STUDENT_SCHOLARSHIP_DATA_IMPORT] ([STUDENT_NAME]);

    PRINT 'Created MOHECORE.STUDENT_SCHOLARSHIP_DATA_IMPORT.';
END
GO
