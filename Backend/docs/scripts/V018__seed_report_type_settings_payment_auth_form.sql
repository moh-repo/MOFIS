/*
 * Seed REPORT_TYPE_SETTINGS for the payment authorisation voucher
 * ("استمارة اعتماد الصرف" — نموذج رقم ١١ حسابات).
 *
 * Distinct from the existing PAYMENT_AUTH row, which configures the tabular
 * list of authorisations: this one configures the single-record statutory form.
 *
 * These columns drive the printed page head and page foot, so the wording is
 * editable from /settings/report-settings rather than frozen in the template:
 *   TITLE_01     republic line, above the form title
 *   TITLE_02     ministry line, below the form title
 *   TITLE_03     issuing department line (optional; omitted when blank)
 *   FOOTER_TEXT  centred note on every page foot
 *
 * Idempotent — safe to re-run. The UPDATE branch deliberately only fills
 * columns that are still NULL/blank, so a row an administrator has already
 * edited in the settings screen is never overwritten.
 */

IF NOT EXISTS (SELECT 1 FROM MOHECORE.REPORT_TYPE_SETTINGS WHERE REPORT_TYPE_CODE = 'PAYMENT_AUTH_FORM')
BEGIN
    INSERT INTO MOHECORE.REPORT_TYPE_SETTINGS
        (REPORT_TYPE_CODE, REPORT_TYPE_NAME_AR, REPORT_TYPE_NAME_EN, IS_ACTIVE,
         TITLE_01_AR, TITLE_01_EN, TITLE_02_AR, TITLE_02_EN, TITLE_03_AR, TITLE_03_EN,
         FOOTER_TEXT_AR, FOOTER_TEXT_EN, SIGNATORY_COUNT)
    VALUES
        ('PAYMENT_AUTH_FORM', N'استمارة اعتماد الصرف', 'Payment Authorization Form', 1,
         N'الجمهورية اليمنية', 'Republic of Yemen',
         N'وزارة المالية', 'Ministry of Finance',
         N'إدارة البعثات والعلاقات الثقافية', 'Department of Scholarships and Cultural Relations',
         N'هذه الاستمارة غير صالحة للصرف ما لم تكن مختومة وموقعة من الجهات المختصة.',
         'This form is not valid for disbursement unless stamped and signed by the competent authorities.',
         0);
END
ELSE
BEGIN
    UPDATE MOHECORE.REPORT_TYPE_SETTINGS
       SET TITLE_01_AR    = COALESCE(NULLIF(LTRIM(RTRIM(TITLE_01_AR)),    N''), N'الجمهورية اليمنية'),
           TITLE_01_EN    = COALESCE(NULLIF(LTRIM(RTRIM(TITLE_01_EN)),     ''),  'Republic of Yemen'),
           TITLE_02_AR    = COALESCE(NULLIF(LTRIM(RTRIM(TITLE_02_AR)),    N''), N'وزارة المالية'),
           TITLE_02_EN    = COALESCE(NULLIF(LTRIM(RTRIM(TITLE_02_EN)),     ''),  'Ministry of Finance'),
           FOOTER_TEXT_AR = COALESCE(NULLIF(LTRIM(RTRIM(FOOTER_TEXT_AR)), N''), N'هذه الاستمارة غير صالحة للصرف ما لم تكن مختومة وموقعة من الجهات المختصة.'),
           FOOTER_TEXT_EN = COALESCE(NULLIF(LTRIM(RTRIM(FOOTER_TEXT_EN)),  ''),  'This form is not valid for disbursement unless stamped and signed by the competent authorities.')
     WHERE REPORT_TYPE_CODE = 'PAYMENT_AUTH_FORM';
END;

/*
 * Repair for rows seeded by earlier revisions of this script, which carried
 * 'State of Libya' as the English republic line while the Arabic said
 * الجمهورية اليمنية — so the voucher named a different country depending on the
 * language it was printed in. The fill-only-blanks UPDATE above cannot correct
 * this, because the wrong value is not blank. Narrowly scoped to the values this
 * script has actually written, so a deliberate administrator edit is left alone.
 *
 * The previous revision of this repair matched `TITLE_01_EN = 'State of Yemen'`
 * — the value it was setting rather than the ones it was replacing — so it could
 * never fire, and the dev database still read 'State of Libya' on 1 Sep 2026
 * when the voucher was first shown as a PDF and someone could finally see it.
 * That is also why the country name is asserted now, in
 * ReportSettingsNameTheSameCountryTest.
 */
UPDATE MOHECORE.REPORT_TYPE_SETTINGS
   SET TITLE_01_EN = 'Republic of Yemen'
 WHERE REPORT_TYPE_CODE = 'PAYMENT_AUTH_FORM'
   AND TITLE_01_AR = N'الجمهورية اليمنية'
   AND TITLE_01_EN IN ('State of Libya', 'State of Yemen');
