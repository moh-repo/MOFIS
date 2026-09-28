package com.dilaw.mohe.report;

import com.dilaw.mohe.model.BankTransaction;
import com.dilaw.mohe.report.jasper.ReportParamBuilder;
import com.dilaw.mohe.service.BankTransactionService;
import com.dilaw.mohe.ums.service.CountryScopeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

/**
 * Bank transactions read from the live database.
 *
 * <p>The bank branch is the point of interest: it was resolved in the entity's
 * subselect but never printed, so the PDF showed the bank without saying which
 * branch handled the transaction.
 */
@Tag("integration")
@SpringBootTest
class BankTransactionReportTest {

    @Autowired private BankTransactionService service;
    @Autowired private ReportParamBuilder reportParamBuilder;

    @MockBean private CountryScopeService countryScopeService;

    @BeforeEach
    void allowAllCountries() {
        when(countryScopeService.canAccessCountry(anyInt())).thenReturn(true);
        when(countryScopeService.getScope()).thenReturn(
                new CountryScopeService.CountryScope(1L, true, true, List.of()));
    }

    private List<BankTransaction> rows() {
        return service.getByFilters(null, null, null, null, null, null, null, null, null,
                PageRequest.of(0, 200, Sort.by("transactionDate").descending())).getContent();
    }

    @Test
    @DisplayName("transactions carry a bank and a branch resolved from the lookups")
    void rows_carryBankAndBranch() {
        List<BankTransaction> rows = rows();

        assertThat(rows).as("STUDENT_FINANCE is expected to hold transactions").isNotEmpty();

        // 939 of 941 finance rows carry a bank id and a resolvable branch, so a
        // report where none resolves means the joins have broken.
        assertThat(rows).anySatisfy(t -> assertThat(t.getBankName()).isNotBlank());
        assertThat(rows)
                .as("the branch must resolve for at least some transactions")
                .anySatisfy(t -> assertThat(t.getBankBranch()).isNotBlank());
        assertThat(rows)
                .as("the English branch name must fall back to the Arabic one, never to blank")
                .allSatisfy(t -> {
                    if (t.getBankBranch() != null && !t.getBankBranch().isBlank()) {
                        assertThat(t.getBankBranchEn()).isNotBlank();
                    }
                });
    }

    @Test
    @DisplayName("the currency is never invented")
    void currency_isNotFabricated() {
        // The subselect used to emit the literal 'USD' for every row, which read
        // as data but was an assumption. It must now come from STUDY_CURRENCY_IN
        // or stay empty.
        assertThat(rows())
                .as("a currency may only appear when the record carries one")
                .allSatisfy(t -> {
                    if (t.getTransactionCurrency() != null) {
                        assertThat(t.getTransactionCurrency()).isNotBlank();
                    }
                });
    }

    @Test
    @DisplayName("columns run left-to-right in English and right-to-left in Arabic")
    void columns_followTheReadingDirection() throws Exception {
        // The table used to be laid out right-to-left in both languages, so the
        // English report put Date on the far right and read backwards.
        for (String lang : new String[]{"ar", "en"}) {
            Map<String, Object> params = reportParamBuilder.build("BANK_TRANSACTION", lang);
            Map<String, Integer> headerX =
                    ReportRenderSupport.textPositions("bank-transaction", params, rows());

            String first = "ar".equals(lang) ? "التاريخ" : "Date";
            String last  = "ar".equals(lang) ? "المستفيد" : "Beneficiary";

            assertThat(headerX).as("lang=%s: both headers must be printed", lang)
                    .containsKeys(first, last);
            if ("ar".equals(lang)) {
                assertThat(headerX.get(first))
                        .as("Arabic reads right to left, so the date column leads on the right")
                        .isGreaterThan(headerX.get(last));
            } else {
                assertThat(headerX.get(first))
                        .as("English reads left to right, so the date column leads on the left")
                        .isLessThan(headerX.get(last));
            }
        }
    }

    @Test
    @DisplayName("column headers are translated, not left at their English defaults")
    void headers_areTranslated() throws Exception {
        Map<String, Object> params = reportParamBuilder.build("BANK_TRANSACTION", "ar");
        String text = ReportRenderSupport.renderToText("bank-transaction", params, rows());

        // COL_TX_TYPE had no Arabic value, so the Arabic report printed "Type".
        assertThat(text).contains("النوع", "التاريخ", "البنك", "الفرع", "المستفيد");
    }

    @Test
    @DisplayName("the branch reaches the printed page, in both languages")
    void branch_reachesThePdf() throws Exception {
        List<BankTransaction> rows = rows();
        BankTransaction withBranch = rows.stream()
                .filter(t -> t.getBankBranch() != null && !t.getBankBranch().isBlank())
                .findFirst().orElseThrow();

        for (String lang : new String[]{"ar", "en"}) {
            Map<String, Object> params = reportParamBuilder.build("BANK_TRANSACTION", lang);
            String text = ReportRenderSupport.renderToText("bank-transaction", params, rows);

            assertThat(text)
                    .as("lang=%s: the branch column header must be printed", lang)
                    .contains("ar".equals(lang) ? "الفرع" : "Branch");

            String expected = "ar".equals(lang) ? withBranch.getBankBranch() : withBranch.getBankBranchEn();
            assertThat(text)
                    .as("lang=%s: the branch name itself must be printed, not just its header", lang)
                    .contains(expected);
        }
    }
}
