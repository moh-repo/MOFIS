package com.dilaw.mohe.migration;

import com.dilaw.mohe.service.codelist.CodeListService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V122: English names the screens printed in Arabic or as raw codes (QA B3, B11, G7).
 */
@Tag("integration")
@SpringBootTest
class EnglishNamesV122Test {

    @Autowired private JdbcTemplate jdbc;
    @Autowired private CodeListService codeLists;

    @Test
    @DisplayName("every quarter has an English name that is not the Arabic one")
    void quartersHaveEnglishNames() {
        List<String> en = jdbc.queryForList(
                "SELECT QUARTER_NAME_EN FROM MOHECORE.MOHE_QUARTER WHERE QUARTER_NO BETWEEN 1 AND 4", String.class);

        assertThat(en).isNotEmpty().allSatisfy(name -> assertThat(name).startsWith("Quarter "));
    }

    @Test
    @DisplayName("approval entity types read in English, not as their code")
    void approvalEntityTypesInEnglish() {
        assertThat(codeLists.label("approval-entity-type", "STUDENT_FINANCE_WAFAR", "en")).isEqualTo("Student wafar");
        assertThat(codeLists.label("approval-entity-type", "STUDENT_FINANCE_WAFAR", "ar")).isNotEqualTo("Student wafar");
    }

    @Test
    @DisplayName("the renamed information-request permissions carry the new Arabic name")
    void permissionsRenamed() {
        List<String> ar = jdbc.queryForList(
                "SELECT PermissionNameAr FROM UMSCORE.Permissions WHERE PermissionCode LIKE 'STUDENT_INFO_REQUESTS.%'",
                String.class);

        assertThat(ar).hasSize(2).allSatisfy(name -> assertThat(name).contains("استيفاء"));
    }
}
