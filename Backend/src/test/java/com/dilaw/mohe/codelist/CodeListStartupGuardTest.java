package com.dilaw.mohe.codelist;

import com.dilaw.mohe.config.CodeListStartupGuard;
import com.dilaw.mohe.service.codelist.CodeListRegistry;
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
 * The startup guard: the database owns the catalogue, Java owns the keys, and
 * the two must agree before the application serves a request.
 */
@Tag("integration")
@SpringBootTest
class CodeListStartupGuardTest {

    @Autowired private CodeListStartupGuard guard;
    @Autowired private CodeListService codeLists;
    @Autowired private JdbcTemplate jdbc;

    @Test
    @DisplayName("on the development database every list has its columns and every constant its row")
    void schemaAndConstantsAgree() {
        assertThat(guard.checkSchema()).isEmpty();
        assertThat(guard.checkConstants()).isEmpty();
        assertThat(CodeListStartupGuard.constantClasses()).hasSizeGreaterThanOrEqualTo(30);
        assertThat(CodeListRegistry.names()).hasSizeGreaterThanOrEqualTo(60);
    }

    @Test
    @DisplayName("a system row soft-deleted behind the application's back is named, list and code")
    void aMissingSystemRowIsNamed() {
        jdbc.update("UPDATE MOHECORE.LU_ALERT_STATUS SET IS_DELETED = 1 WHERE CODE = 'DISMISSED'");
        codeLists.evictAll();
        try {
            List<String> problems = guard.checkConstants();
            assertThat(problems).anySatisfy(p -> assertThat(p).contains("alert-status").contains("'DISMISSED'"));
        } finally {
            jdbc.update("UPDATE MOHECORE.LU_ALERT_STATUS SET IS_DELETED = 0 WHERE CODE = 'DISMISSED'");
            codeLists.evictAll();
        }
    }

    @Test
    @DisplayName("a row flagged IS_SYSTEM that no constant names is named too — the flag is not decoration")
    void aSystemFlagWithoutAConstantIsNamed() {
        jdbc.update("UPDATE MOHECORE.LU_CHANGE_REQUEST_STATUS SET IS_SYSTEM = 1 WHERE CODE = 'MODIFIED'");
        codeLists.evictAll();
        try {
            List<String> problems = guard.checkConstants();
            assertThat(problems).anySatisfy(p -> assertThat(p).contains("change-request-status").contains("'MODIFIED'"));
        } finally {
            jdbc.update("UPDATE MOHECORE.LU_CHANGE_REQUEST_STATUS SET IS_SYSTEM = 0 WHERE CODE = 'MODIFIED'");
            codeLists.evictAll();
        }
    }

    @Test
    @DisplayName("every registered list loads, and every row of a status/type list has both names")
    void everyListLoadsWithBothNames() {
        for (String name : CodeListRegistry.names()) {
            assertThat(codeLists.rows(name)).as(name).isNotEmpty();
            assertThat(codeLists.rows(name)).as(name).allSatisfy(r -> assertThat(r.code()).isNotBlank());
            if (name.equals("report-label")) {
                // Template parameters whose rendered text is empty are seeded as "" on purpose (V077).
                continue;
            }
            assertThat(codeLists.rows(name)).as(name).allSatisfy(r -> {
                assertThat(r.nameAr()).as("%s.%s NAME_AR", name, r.code()).isNotBlank();
                assertThat(r.nameEn()).as("%s.%s NAME_EN", name, r.code()).isNotBlank();
            });
        }
    }
}
