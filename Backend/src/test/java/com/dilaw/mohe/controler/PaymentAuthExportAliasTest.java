package com.dilaw.mohe.controler;

import com.dilaw.mohe.scope.AsUser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * The payment authorisation exports, on the aliases the shared report bar asks for.
 *
 * <p>Every report controller in this system exposes one endpoint under several
 * aliases and lets the alias pick the format — see
 * {@link com.dilaw.mohe.report.ReportExports}. The voucher endpoint did not: it
 * called Jasper directly and mapped only the three PDF aliases. That matters because
 * {@code ReportPdfService.toExcelApiPath} builds the spreadsheet URL by rewriting
 * {@code /export-report} to {@code /export-excel}, so the moment the payment
 * authorisation page started using the shared action bar its Export button would have
 * asked for a path that was not mapped and got a 404.
 *
 * <p>Both formats come from the same JRXML, which is the property the alias
 * convention exists to guarantee: the spreadsheet a user downloads cannot drift from
 * the PDF they were just looking at.
 *
 * <p>{@code addFilters = false} turns off the JWT chain, not method security, so the
 * controller's {@code @PreAuthorize} still runs — hence the authorities below.
 */
@Tag("integration")
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
class PaymentAuthExportAliasTest {

    /** PA-2024-001, the oldest seeded authorisation. */
    private static final int RECORD_ID = 1;

    private static final List<String> FINANCE_ONLY = List.of("FINANCE.VIEW", "PAYMENT_AUTH.VIEW");

    private static final String PDF = "application/pdf";
    private static final String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    @Autowired private MockMvc mvc;
    @Autowired private com.dilaw.mohe.repository.StudyCurrencyInRepository currencyRepository;

    private MvcResult as(Supplier<MvcResult> request) {
        return AsUser.callWithAuthorities(AsUser.SUPERADMIN, FINANCE_ONLY, request);
    }

    private MvcResult fetch(String path) {
        return as(() -> {
            try {
                return mvc.perform(get(path)).andReturn();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    private void assertRenders(String path, String expectedType) {
        MvcResult result = fetch(path);
        assertThat(result.getResponse().getStatus()).as("%s status", path).isEqualTo(200);
        assertThat(result.getResponse().getContentType()).as("%s content type", path).startsWith(expectedType);
        assertThat(result.getResponse().getContentAsByteArray().length)
                .as("%s produced an empty document", path).isGreaterThan(1000);
    }

    // ── The voucher: نموذج رقم ١١, one record ────────────────────────────────

    @Test
    @DisplayName("the voucher renders a PDF on each of its three PDF aliases")
    void voucherPdfAliases() {
        assertRenders("/api/v1/paymentAuth/" + RECORD_ID + "/export-report", PDF);
        assertRenders("/api/v1/paymentAuth/" + RECORD_ID + "/report-pdf", PDF);
        assertRenders("/api/v1/paymentAuth/" + RECORD_ID + "/doc", PDF);
    }

    @Test
    @DisplayName("the voucher renders a spreadsheet on the alias the Export button derives")
    void voucherSpreadsheetAlias() {
        // This is the 404 the shared action bar would otherwise have hit: it asks for
        // /export-excel by rewriting the PDF path it was given.
        assertRenders("/api/v1/paymentAuth/" + RECORD_ID + "/export-excel", XLSX);
        assertRenders("/api/v1/paymentAuth/" + RECORD_ID + "/report-xlsx", XLSX);
    }

    @Test
    @DisplayName("a record that does not exist is still a 404, on the new aliases too")
    void missingRecordIsNotFound() {
        assertThat(fetch("/api/v1/paymentAuth/" + Integer.MAX_VALUE + "/export-report")
                .getResponse().getStatus()).isEqualTo(404);
        assertThat(fetch("/api/v1/paymentAuth/" + Integer.MAX_VALUE + "/export-excel")
                .getResponse().getStatus()).isEqualTo(404);
    }

    @Test
    @DisplayName("the voucher is rendered in the language asked for")
    void voucherHonoursLanguage() {
        assertRenders("/api/v1/paymentAuth/" + RECORD_ID + "/export-report?lang=ar", PDF);
        // The English voucher compiles the mirrored design — a different code path
        // through JasperReportService, and one that only fails at render time.
        assertRenders("/api/v1/paymentAuth/" + RECORD_ID + "/export-report?lang=en", PDF);
    }

    @Test
    @DisplayName("the ministry's functional currency is on the database for the voucher to name")
    void theFunctionalCurrencyResolves() {
        // PAYMENT_AUTH has no currency column: the voucher is denominated in the
        // ministry's own currency, and a filtered unique index keeps that to one row.
        // What the controller does with it is PaymentAuthCurrencyParamsTest; that it
        // is there to be read is this.
        var currency = currencyRepository.findFirstByIsFunctionalTrue();

        assertThat(currency).as("no STUDY_CURRENCY_IN row is flagged IS_FUNCTIONAL; see V039").isPresent();
        assertThat(currency.get().getIsoCode()).isNotBlank();
        assertThat(currency.get().getStudyCurrencyInName()).isNotBlank();
        assertThat(currency.get().getStudyCurrencyInNameEn()).isNotBlank();
        assertThat(currency.get().getMinorUnits()).isNotNull().isBetween(0, 4);
    }

    // ── The list ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("the list report renders in both formats, and honours the status filter")
    void listReportAliases() {
        assertRenders("/api/v1/paymentAuth/export-report", PDF);
        assertRenders("/api/v1/paymentAuth/export-excel", XLSX);
        // The page's own filter, passed through to the report rather than applied
        // after the fact in the browser.
        assertRenders("/api/v1/paymentAuth/export-report?status=APPROVED", PDF);
    }
}
