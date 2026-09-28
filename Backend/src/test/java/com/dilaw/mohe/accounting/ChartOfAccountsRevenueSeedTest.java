package com.dilaw.mohe.accounting;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * The revenue chart of accounts as V064 loads it from the ministry's form.
 *
 * <p>Read-only. It states the shape the manual has — five chapters, four
 * levels, 343 accounts per open fiscal year, 260 leaves — so that a re-run of
 * the generator on a corrected sheet, or a hand edit to the chart, shows up as
 * a number changing here rather than as a report silently losing a row.
 * Skipped (not failed) on a database that has not had V064.
 */
@Tag("integration")
@SpringBootTest
class ChartOfAccountsRevenueSeedTest {

    @Autowired private JdbcTemplate jdbc;

    private Integer economicSegment() {
        return jdbc.queryForObject("SELECT SEGMENT_ID FROM FINCORE.COA_SEGMENT WHERE SEGMENT_CODE = 'ECONOMIC'", Integer.class);
    }

    private List<Integer> openYears() {
        return jdbc.queryForList("SELECT FISCAL_YEAR_ID FROM FINCORE.FISCAL_YEAR WHERE STATUS = 'OPEN' ORDER BY YEAR_NO", Integer.class);
    }

    private void assumeLoaded(int seg) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM FINCORE.COA_ACCOUNT WHERE SEGMENT_ID = ?", Integer.class, seg);
        assumeTrue(n != null && n > 0, "V064 not applied here — the chart is empty");
    }

    @Test
    @DisplayName("every open fiscal year carries the whole revenue chart: 5 / 22 / 63 / 253, 260 postable leaves")
    void shapePerYear() {
        int seg = economicSegment();
        assumeLoaded(seg);
        for (Integer year : openYears()) {
            List<Map<String, Object>> byLevel = jdbc.queryForList("""
                    SELECT LEVEL_NO, COUNT(*) AS n, SUM(CASE WHEN IS_POSTABLE = 1 THEN 1 ELSE 0 END) AS postable
                      FROM FINCORE.COA_ACCOUNT WHERE SEGMENT_ID = ? AND FISCAL_YEAR_ID = ?
                     GROUP BY LEVEL_NO ORDER BY LEVEL_NO""", seg, year);
            assertThat(byLevel).as("levels in year " + year).hasSize(4);
            assertThat(byLevel.stream().map(r -> ((Number) r.get("n")).intValue()).toList())
                    .as("accounts per level in year " + year).containsExactly(5, 22, 63, 253);
            int postable = byLevel.stream().mapToInt(r -> ((Number) r.get("postable")).intValue()).sum();
            assertThat(postable).as("postable leaves in year " + year).isEqualTo(260);
        }
    }

    @Test
    @DisplayName("the five chapters are the form's five, all revenue, credit-normal, budgetary, national")
    void chapters() {
        int seg = economicSegment();
        assumeLoaded(seg);
        Integer year = openYears().get(openYears().size() - 1);
        List<String> chapters = jdbc.queryForList("""
                SELECT NAME_AR FROM FINCORE.COA_ACCOUNT
                 WHERE SEGMENT_ID = ? AND FISCAL_YEAR_ID = ? AND LEVEL_NO = 1 ORDER BY CODE""", String.class, seg, year);
        assertThat(chapters).containsExactly(
                "الإيرادات الضريبية",
                "المنح",
                "إيردات دخل الملكية ومبيعات السلع والخدمات والتحويلات والمتنوعة",
                "التصرف في الأصول غير المالية",
                "التصرف في الأصول المالية وتحمل الخصوم");
        Integer offNature = jdbc.queryForObject("""
                SELECT COUNT(*) FROM FINCORE.COA_ACCOUNT WHERE SEGMENT_ID = ?
                   AND (ACCOUNT_NATURE <> 'REVENUE' OR NORMAL_BALANCE <> 'C' OR IS_BUDGETARY <> 1 OR STUDY_COUNTRY_IN_ID IS NOT NULL)""",
                Integer.class, seg);
        assertThat(offNature).isZero();
    }

    @Test
    @DisplayName("levels are named as the form names them and every code has its level's width")
    void levelsAndWidths() {
        int seg = economicSegment();
        assumeLoaded(seg);
        List<Map<String, Object>> levels = jdbc.queryForList(
                "SELECT LEVEL_NO, NAME_AR, CODE_LENGTH FROM FINCORE.COA_LEVEL WHERE SEGMENT_ID = ? AND LEVEL_NO <= 4 ORDER BY LEVEL_NO", seg);
        assertThat(levels.stream().map(r -> (String) r.get("NAME_AR")).toList()).containsExactly("باب", "فصل", "بند", "نوع");
        assertThat(levels.stream().map(r -> ((Number) r.get("CODE_LENGTH")).intValue()).toList()).containsExactly(1, 1, 1, 2);
        Integer wrongWidth = jdbc.queryForObject("""
                SELECT COUNT(*) FROM FINCORE.COA_ACCOUNT a JOIN FINCORE.COA_LEVEL l ON l.SEGMENT_ID = a.SEGMENT_ID AND l.LEVEL_NO = a.LEVEL_NO
                 WHERE a.SEGMENT_ID = ? AND LEN(a.CODE) <> l.CODE_LENGTH""", Integer.class, seg);
        assertThat(wrongWidth).isZero();
    }

    @Test
    @DisplayName("a type sits under an item, whose full code is its own prefix; a postable account has no children")
    void treeIntegrity() {
        int seg = economicSegment();
        assumeLoaded(seg);
        Integer badParent = jdbc.queryForObject("""
                SELECT COUNT(*) FROM FINCORE.COA_ACCOUNT c JOIN FINCORE.COA_ACCOUNT p ON p.ACCOUNT_ID = c.PARENT_ACCOUNT_ID
                 WHERE c.SEGMENT_ID = ? AND (p.LEVEL_NO <> c.LEVEL_NO - 1 OR p.FISCAL_YEAR_ID <> c.FISCAL_YEAR_ID
                    OR c.FULL_CODE <> p.FULL_CODE + c.CODE)""", Integer.class, seg);
        assertThat(badParent).isZero();
        Integer postableParents = jdbc.queryForObject("""
                SELECT COUNT(*) FROM FINCORE.COA_ACCOUNT a WHERE a.SEGMENT_ID = ? AND a.IS_POSTABLE = 1
                   AND EXISTS (SELECT 1 FROM FINCORE.COA_ACCOUNT c WHERE c.PARENT_ACCOUNT_ID = a.ACCOUNT_ID)""", Integer.class, seg);
        assertThat(postableParents).isZero();
        Integer childlessNonPostable = jdbc.queryForObject("""
                SELECT COUNT(*) FROM FINCORE.COA_ACCOUNT a WHERE a.SEGMENT_ID = ? AND a.IS_POSTABLE = 0
                   AND NOT EXISTS (SELECT 1 FROM FINCORE.COA_ACCOUNT c WHERE c.PARENT_ACCOUNT_ID = a.ACCOUNT_ID)""", Integer.class, seg);
        assertThat(childlessNonPostable).as("a leaf that cannot be posted to is a dead end").isZero();
    }
}
