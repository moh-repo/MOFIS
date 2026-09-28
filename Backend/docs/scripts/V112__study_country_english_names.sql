/* ============================================================================
   V112 — English names for study countries that have none (review item Q5).

   The English UI shows STUDY_COUNTRY_IN_NAME_EN and falls back to the Arabic
   name where it is NULL, so a country with no English name stays Arabic in the
   top-bar country selector and on every screen that lists study countries.

   This fills STUDY_COUNTRY_IN_NAME_EN from the Arabic name for the countries
   below, and only where it is NULL or blank: a name someone already entered is
   never overwritten. The Arabic name is matched after trimming, and a few
   common spellings are listed for the same country.

   UX_StudyCountryIn_NameEn (V048) allows one row per English name, so a row is
   skipped when another row already holds that English name -- a duplicate
   country is a data question, not something to paper over here.

   The closing report lists every active row still without an English name, to
   be completed by hand in the lookup screen.
   ============================================================================ */

SET NOCOUNT ON;

DECLARE @names TABLE (name_ar nvarchar(200) NOT NULL, name_en nvarchar(200) NOT NULL);

INSERT INTO @names (name_ar, name_en) VALUES
    (N'مصر', N'Egypt'), (N'جمهورية مصر العربية', N'Egypt'),
    (N'الأردن', N'Jordan'), (N'الاردن', N'Jordan'), (N'المملكة الأردنية الهاشمية', N'Jordan'),
    (N'السعودية', N'Saudi Arabia'), (N'المملكة العربية السعودية', N'Saudi Arabia'),
    (N'الإمارات', N'United Arab Emirates'), (N'الامارات', N'United Arab Emirates'),
    (N'الإمارات العربية المتحدة', N'United Arab Emirates'),
    (N'قطر', N'Qatar'), (N'الكويت', N'Kuwait'), (N'البحرين', N'Bahrain'),
    (N'عمان', N'Oman'), (N'عُمان', N'Oman'), (N'سلطنة عمان', N'Oman'),
    (N'اليمن', N'Yemen'),
    (N'العراق', N'Iraq'), (N'سوريا', N'Syria'), (N'سورية', N'Syria'), (N'لبنان', N'Lebanon'),
    (N'فلسطين', N'Palestine'),
    (N'السودان', N'Sudan'), (N'ليبيا', N'Libya'), (N'تونس', N'Tunisia'),
    (N'الجزائر', N'Algeria'), (N'المغرب', N'Morocco'), (N'موريتانيا', N'Mauritania'),
    (N'الصومال', N'Somalia'), (N'جيبوتي', N'Djibouti'),
    (N'تركيا', N'Turkey'), (N'إيران', N'Iran'), (N'ايران', N'Iran'),
    (N'ماليزيا', N'Malaysia'), (N'إندونيسيا', N'Indonesia'), (N'اندونيسيا', N'Indonesia'),
    (N'الهند', N'India'), (N'باكستان', N'Pakistan'), (N'بنغلاديش', N'Bangladesh'),
    (N'الصين', N'China'), (N'اليابان', N'Japan'),
    (N'كوريا الجنوبية', N'South Korea'), (N'تايلاند', N'Thailand'),
    (N'روسيا', N'Russia'), (N'روسيا الاتحادية', N'Russia'),
    (N'أوكرانيا', N'Ukraine'), (N'اوكرانيا', N'Ukraine'), (N'بيلاروسيا', N'Belarus'),
    (N'كازاخستان', N'Kazakhstan'), (N'أوزبكستان', N'Uzbekistan'),
    (N'ألمانيا', N'Germany'), (N'المانيا', N'Germany'), (N'فرنسا', N'France'),
    (N'بريطانيا', N'United Kingdom'), (N'المملكة المتحدة', N'United Kingdom'),
    (N'إيطاليا', N'Italy'), (N'ايطاليا', N'Italy'), (N'إسبانيا', N'Spain'), (N'اسبانيا', N'Spain'),
    (N'هولندا', N'Netherlands'), (N'بلجيكا', N'Belgium'), (N'السويد', N'Sweden'),
    (N'النمسا', N'Austria'), (N'سويسرا', N'Switzerland'), (N'بولندا', N'Poland'),
    (N'رومانيا', N'Romania'), (N'المجر', N'Hungary'), (N'التشيك', N'Czech Republic'),
    (N'بلغاريا', N'Bulgaria'), (N'اليونان', N'Greece'), (N'قبرص', N'Cyprus'),
    (N'الولايات المتحدة', N'United States'), (N'الولايات المتحدة الأمريكية', N'United States'),
    (N'أمريكا', N'United States'), (N'امريكا', N'United States'),
    (N'كندا', N'Canada'), (N'أستراليا', N'Australia'), (N'استراليا', N'Australia'),
    (N'نيوزيلندا', N'New Zealand'), (N'كوبا', N'Cuba'), (N'البرازيل', N'Brazil'),
    (N'جنوب أفريقيا', N'South Africa'), (N'جنوب افريقيا', N'South Africa'),
    (N'إثيوبيا', N'Ethiopia'), (N'اثيوبيا', N'Ethiopia'), (N'كينيا', N'Kenya'),
    (N'نيجيريا', N'Nigeria');

;WITH candidates AS (
    SELECT c.STUDY_COUNTRY_IN_ID, n.name_en,
           ROW_NUMBER() OVER (PARTITION BY n.name_en ORDER BY c.STUDY_COUNTRY_IN_ID) AS rn
    FROM MOHECORE.STUDY_COUNTRY_IN c
    JOIN @names n ON LTRIM(RTRIM(c.STUDY_COUNTRY_IN_NAME)) = n.name_ar
    WHERE NULLIF(LTRIM(RTRIM(c.STUDY_COUNTRY_IN_NAME_EN)), N'') IS NULL
)
UPDATE c
SET c.STUDY_COUNTRY_IN_NAME_EN = x.name_en
FROM MOHECORE.STUDY_COUNTRY_IN c
JOIN candidates x ON x.STUDY_COUNTRY_IN_ID = c.STUDY_COUNTRY_IN_ID
WHERE x.rn = 1
  AND NOT EXISTS (SELECT 1 FROM MOHECORE.STUDY_COUNTRY_IN o
                  WHERE o.STUDY_COUNTRY_IN_NAME_EN = x.name_en);

PRINT 'V112: filled STUDY_COUNTRY_IN_NAME_EN on ' + CAST(@@ROWCOUNT AS varchar(10)) + ' countries.';

PRINT '--- V112: active study countries still without an English name (complete by hand) ---';
SELECT STUDY_COUNTRY_IN_ID, STUDY_COUNTRY_IN_NAME
FROM MOHECORE.STUDY_COUNTRY_IN
WHERE NULLIF(LTRIM(RTRIM(STUDY_COUNTRY_IN_NAME_EN)), N'') IS NULL
  AND ISNULL(IS_ACTIVE, 1) = 1
ORDER BY STUDY_COUNTRY_IN_ID;
