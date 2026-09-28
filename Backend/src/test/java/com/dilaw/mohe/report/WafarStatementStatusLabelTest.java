package com.dilaw.mohe.report;

import com.dilaw.mohe.dto.WafarStatementRowDto;
import com.dilaw.mohe.service.CountryNameService;
import com.dilaw.mohe.service.WafarStatementReportService;
import com.dilaw.mohe.service.codelist.CodeListService;
import com.dilaw.mohe.ums.service.CountryScopeService;
import com.dilaw.mohe.ums.service.ReportCountryScopeHelper;
import com.dilaw.mohe.wafar.dto.WafarBalanceSummaryDto;
import com.dilaw.mohe.wafar.dto.WafarSpendingCategoryDto;
import com.dilaw.mohe.wafar.dto.WafarSpendingDto;
import com.dilaw.mohe.wafar.service.WafarBalanceService;
import com.dilaw.mohe.wafar.service.WafarSpendingService;
import net.sf.jasperreports.engine.JRExpression;
import net.sf.jasperreports.engine.design.JRDesignTextField;
import net.sf.jasperreports.engine.design.JasperDesign;
import net.sf.jasperreports.engine.xml.JRXmlLoader;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

/**
 * Live QA M14(a) (25 Sep 2026): the Wafar statement printed the raw status codes
 * APPROVED / PENDING / REJECTED / CANCELLED, in the Arabic PDF too. Each row now carries
 * the status named in the requested language beside the code, which the totals still
 * use, and the template prints the name.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WafarStatementStatusLabelTest {

    @Mock private WafarSpendingService spendingService;
    @Mock private WafarBalanceService balanceService;
    @Mock private CountryNameService countryNameService;
    @Mock private ReportCountryScopeHelper countryScopeHelper;
    @Mock private CountryScopeService countryScopeService;
    @Mock private CodeListService codeLists;
    @InjectMocks private WafarStatementReportService service;

    private static WafarSpendingDto spend(int id, String status, String amount) {
        WafarSpendingDto s = new WafarSpendingDto();
        s.setId(id);
        s.setStatus(status);
        s.setAmount(new BigDecimal(amount));
        s.setSpendingDate(LocalDate.of(2026, 9, id));
        WafarSpendingCategoryDto c = new WafarSpendingCategoryDto();
        c.setId(1);
        c.setCategoryNameAr("سفر");
        c.setCategoryNameEn("Travel");
        s.setCategory(c);
        return s;
    }

    private void given() {
        when(countryScopeHelper.resolveCountryIds(isNull(), any())).thenReturn(null);
        when(countryNameService.all()).thenReturn(List.of());
        when(spendingService.getAll(any(), any(), any(), any(), any()))
                .thenReturn(List.of(spend(1, "APPROVED", "10.00"), spend(2, "PENDING", "5.00")));
        when(codeLists.label(eq("wafar-spending-status"), eq("APPROVED"), eq("ar"))).thenReturn("معتمد");
        when(codeLists.label(eq("wafar-spending-status"), eq("PENDING"), eq("ar"))).thenReturn("قيد الانتظار");
        when(codeLists.label(eq("wafar-spending-status"), eq("APPROVED"), eq("en"))).thenReturn("Approved");
        when(codeLists.label(eq("wafar-spending-status"), eq("PENDING"), eq("en"))).thenReturn("Pending");
        when(balanceService.getSummary()).thenReturn(new WafarBalanceSummaryDto());
    }

    @Test
    @DisplayName("each row names its status in the requested language and keeps the code")
    void labelled() {
        given();
        List<WafarStatementRowDto> ar = service.rows(null, null, null, null, null, null, "ar");
        assertThat(ar).extracting(WafarStatementRowDto::getStatusLabel).containsExactlyInAnyOrder("معتمد", "قيد الانتظار");
        assertThat(ar).extracting(WafarStatementRowDto::getStatus).containsExactlyInAnyOrder("APPROVED", "PENDING");

        List<WafarStatementRowDto> en = service.rows(null, null, null, null, null, null, "en");
        assertThat(en).extracting(WafarStatementRowDto::getStatusLabel).containsExactlyInAnyOrder("Approved", "Pending");
    }

    @Test
    @DisplayName("the totals are still summed on the code")
    void totalsOnTheCode() {
        given();
        var total = service.summary(null, null, null, null, null, null, "ar").getCategoryTotals().get(0);
        assertThat(total.getApprovedAmount()).isEqualByComparingTo("10.00");
        assertThat(total.getPendingAmount()).isEqualByComparingTo("5.00");
    }

    @Test
    @DisplayName("the template prints the status name in both of its status cells")
    void templatePrintsTheName() throws Exception {
        JasperDesign design;
        try (InputStream is = getClass().getResourceAsStream("/reports/wafar-statement.jrxml")) {
            design = JRXmlLoader.load(is);
        }
        assertThat(design.getFieldsMap()).containsKey("statusLabel");
        long cells = Arrays.stream(design.getDetailSection().getBands())
                .flatMap(b -> b.getChildren().stream())
                .filter(JRDesignTextField.class::isInstance)
                .map(e -> ((JRDesignTextField) e).getExpression())
                .map(JRExpression::getText)
                .filter(t -> t.contains("$F{status}"))
                .peek(t -> assertThat(t).contains("$F{statusLabel}"))
                .count();
        assertThat(cells).isEqualTo(2);
    }
}
