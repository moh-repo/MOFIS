package com.dilaw.mohe.codelist;

import com.dilaw.mohe.service.StudentFinancialDataFormV2Service;
import com.dilaw.mohe.service.codelist.CodeListService;
import com.dilaw.mohe.ums.model.UmsReport;
import com.dilaw.mohe.ums.service.UmsReportService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Names that used to be Java switches now come from the tables: the quarter
 * names, the ordinal words of the statement head, and the report hub catalogue.
 */
@Tag("integration")
@SpringBootTest
class CatalogueFromTablesTest {

    @Autowired private CodeListService codeLists;
    @Autowired private StudentFinancialDataFormV2Service formV2;
    @Autowired private UmsReportService reports;
    @Autowired private JdbcTemplate jdbc;

    @Test
    @DisplayName("quarter names and ordinals are the table's rows, in both languages")
    void quarterNamesComeFromTheTables() {
        String q3Ar = jdbc.queryForObject("SELECT NAME_AR FROM MOHECORE.LU_QUARTER_NUMBER WHERE CODE = '3'", String.class);
        String q3En = jdbc.queryForObject("SELECT NAME_EN FROM MOHECORE.LU_QUARTER_NUMBER WHERE CODE = '3'", String.class);
        assertThat(codeLists.label("quarter-number", "3", "ar")).isEqualTo(q3Ar);
        assertThat(codeLists.label("quarter-number", "3", "en")).isEqualTo(q3En);

        String ordinalAr = jdbc.queryForObject(
                "SELECT NAME_AR FROM MOHECORE.LU_REPORT_LABEL WHERE CODE = 'LBL_QUARTER_ORDINAL_3'", String.class);
        assertThat(formV2.quarterLabel(3, true)).isEqualTo(ordinalAr);
        assertThat(formV2.quarterLabel(3, false)).isEqualTo("Third");
        assertThat(formV2.quarterLabel(7, false)).isEqualTo("7");
        assertThat(codeLists.label("report-label", "LBL_UNSPECIFIED", "en")).isEqualTo("Unspecified");
    }

    @Test
    @DisplayName("the report catalogue is a tree of hubs and cards, every card under a hub that exists, every row guarded")
    void reportCatalogueIsATree() {
        List<UmsReport> rows = reports.catalogue();
        assertThat(rows).hasSizeGreaterThanOrEqualTo(60);
        Set<String> codes = rows.stream().map(UmsReport::getReportCode).collect(Collectors.toSet());
        List<UmsReport> hubs = rows.stream().filter(r -> r.getParentCode() == null).toList();
        List<UmsReport> cards = rows.stream().filter(r -> r.getParentCode() != null).toList();
        assertThat(hubs).extracting(UmsReport::getReportCode)
                .contains("HUB_STUDENT", "HUB_FINANCIAL", "HUB_STATISTICAL", "HUB_PERIODIC",
                        "HUB_UNIVERSITY", "HUB_OPERATIONS", "HUB_CUSTOM", "HUB_GRAPHS");
        assertThat(cards).allSatisfy(c -> assertThat(codes).as(c.getReportCode()).contains(c.getParentCode()));
        assertThat(rows).allSatisfy(r -> {
            assertThat(r.getRoute()).as(r.getReportCode()).isNotBlank();
            assertThat(r.getIcon()).as(r.getReportCode()).startsWith("pi-");
            assertThat(r.getReportNameAr()).as(r.getReportCode()).isNotBlank();
            assertThat(r.getPageCode() != null || r.getPermissionCodes() != null)
                    .as("%s carries a page code or permission codes", r.getReportCode()).isTrue();
        });
        // Ordered for display: a hub's cards in their own sort order.
        assertThat(rows).isSortedAccordingTo((a, b) -> Integer.compare(a.getSortOrder(), b.getSortOrder()));
    }
}
