package com.dilaw.mohe.report;

import com.dilaw.mohe.model.FinancialAudit;
import com.dilaw.mohe.report.jasper.ReportParamBuilder;
import com.dilaw.mohe.service.FinancialAuditService;
import com.dilaw.mohe.ums.service.CountryScopeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

/**
 * The financial audit listing must not manufacture the things that make it an
 * audit.
 *
 * <p>The query used to substitute literals wherever a lookup failed to resolve.
 * Because none of those lookups resolve in this database, virtually every row
 * carried an auditor who had not audited it and a note saying it had been
 * reviewed.
 */
@Tag("integration")
@SpringBootTest
class FinancialAuditReportTest {

    @Autowired private FinancialAuditService service;
    @Autowired private ReportParamBuilder reportParamBuilder;

    @MockBean private CountryScopeService countryScopeService;

    @BeforeEach
    void allowAllCountries() {
        when(countryScopeService.canAccessCountry(anyInt())).thenReturn(true);
        when(countryScopeService.getScope()).thenReturn(
                new CountryScopeService.CountryScope(1L, true, true, List.of()));
    }

    private List<FinancialAudit> rows() {
        return service.getAll(null);
    }

    @Test
    @DisplayName("rows are read from the finance tables")
    void rows_comeFromTheDatabase() {
        List<FinancialAudit> rows = rows();

        assertThat(rows).as("STUDENT_FINANCE is expected to hold records").isNotEmpty();
        assertThat(rows).allSatisfy(r -> {
            assertThat(r.getAuditId()).isNotNull();
            assertThat(r.getAuditAmount()).isNotNull();
        });
        // Amounts must be real figures, not a column of zeros.
        assertThat(rows).anySatisfy(r ->
                assertThat(r.getAuditAmount().doubleValue()).isGreaterThan(0));
        assertThat(rows).anySatisfy(r -> assertThat(r.getStudentName()).isNotBlank());
    }

    @Test
    @DisplayName("no auditor, note or currency is invented")
    void nothing_isFabricated() {
        List<FinancialAudit> rows = rows();

        assertThat(rows).allSatisfy(r -> {
            assertThat(r.getAuditorName())
                    .as("an auditor may only be named when STUDY_FINANCE_DOF resolves")
                    .isNotEqualTo("Finance Department");
            assertThat(r.getAuditNotes())
                    .as("the report must not claim a review that never happened")
                    .isNotEqualTo("Reviewed automatically");
            assertThat(r.getAuditCurrency())
                    .as("the currency must come from STUDY_CURRENCY_IN, not a literal")
                    .isNotEqualTo("USD");
            assertThat(r.getAuditStatus())
                    .as("an unresolved status must be empty, not the word Unknown")
                    .isNotEqualTo("Unknown");
            assertThat(r.getAuditCategory())
                    .as("an unresolved category must be empty, not the word Other")
                    .isNotEqualTo("Other");
        });

        // A blank string would slip past the checks above while still filling the
        // column with something that is not data.
        assertThat(rows).allSatisfy(r -> {
            if (r.getAuditorName() != null) assertThat(r.getAuditorName()).isNotBlank();
            if (r.getAuditNotes() != null) assertThat(r.getAuditNotes()).isNotBlank();
            if (r.getAuditCurrency() != null) assertThat(r.getAuditCurrency()).isNotBlank();
        });
    }

    @Test
    @DisplayName("the PDF prints no invented values either")
    void pdf_printsNoFabricatedValues() throws Exception {
        List<FinancialAudit> rows = rows();

        for (String lang : new String[]{"ar", "en"}) {
            Map<String, Object> params = reportParamBuilder.build("FINANCIAL_AUDIT", lang);
            String text = ReportRenderSupport.renderToText("financial-audit", params, rows);

            assertThat(text)
                    .as("lang=%s: the page must not carry a manufactured audit trail", lang)
                    .doesNotContain("Reviewed automatically")
                    .doesNotContain("Finance Department");
        }
    }
}
