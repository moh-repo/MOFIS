package com.dilaw.mohe.controler;

import com.dilaw.mohe.model.PaymentAuth;
import com.dilaw.mohe.model.StudyCurrencyIn;
import com.dilaw.mohe.repository.StudyCurrencyInRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * The two parameters the payment voucher gets that no other report does: the currency
 * it is denominated in, and its total written out.
 *
 * <p>{@code PAYMENT_AUTH} has no currency column. A disbursement on نموذج رقم ١١ is
 * paid in the ministry's own currency, so the voucher reads
 * {@code STUDY_CURRENCY_IN.IS_FUNCTIONAL} — one row, kept to one by a filtered unique
 * index — rather than carrying a currency per record. Until now the word "ريال" was
 * typed into the column head's label text, so the form could not have said anything
 * else even if the ministry's books had.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PaymentAuthCurrencyParamsTest {

    @Mock private StudyCurrencyInRepository currencyRepository;

    @InjectMocks private PaymentAuthController controller;

    private Map<String, Object> params;
    private PaymentAuth record;

    @BeforeEach
    void setUp() {
        params = new HashMap<>();
        record = new PaymentAuth();
        record.setOriginalAmount(new BigDecimal("1500.00"));
        record.setDeductions(new BigDecimal("100.00"));
        record.setNetAmount(new BigDecimal("1400.00"));
        when(currencyRepository.findFirstByIsFunctionalTrue()).thenReturn(Optional.of(yer()));
    }

    private static StudyCurrencyIn yer() {
        StudyCurrencyIn c = new StudyCurrencyIn();
        c.setId(1012);
        c.setIsoCode("YER");
        c.setStudyCurrencyInName("الريال اليمني");
        c.setStudyCurrencyInNameEn("Yemeni Rial");
        c.setMinorUnits(2);
        c.setIsFunctional(true);
        return c;
    }

    @Test
    @DisplayName("the Arabic voucher names the ministry's currency and writes the total out")
    void arabicParameters() {
        controller.addCurrencyAndWords(params, record, true);

        // The column head keeps the stored display name, article and all; the words
        // line drops it, because a counted noun does not carry one.
        assertThat(params.get("CURRENCY_NAME")).isEqualTo("الريال اليمني");
        assertThat(params.get("AMOUNT_IN_WORDS"))
                .isEqualTo("فقط ألف وأربعمائة ريال يمني لا غير");
    }

    @Test
    @DisplayName("the English voucher uses the same row's English name")
    void englishParameters() {
        controller.addCurrencyAndWords(params, record, false);

        assertThat(params.get("CURRENCY_NAME")).isEqualTo("Yemeni Rial");
        assertThat(params.get("AMOUNT_IN_WORDS"))
                .isEqualTo("One thousand four hundred Yemeni Rials only");
    }

    @Test
    @DisplayName("the words are of the net amount — what is actually to be paid")
    void theWordsFollowTheNetAmount() {
        // The figure printed beside them on the total row is the net, so the words
        // must be the net too, or the pair contradict each other on the page.
        controller.addCurrencyAndWords(params, record, false);

        assertThat((String) params.get("AMOUNT_IN_WORDS"))
                .contains("One thousand four hundred")
                .doesNotContain("fifteen hundred");
    }

    @Test
    @DisplayName("no functional currency: both come out blank rather than half-named")
    void noFunctionalCurrency() {
        when(currencyRepository.findFirstByIsFunctionalTrue()).thenReturn(Optional.empty());

        controller.addCurrencyAndWords(params, record, true);

        // The template omits both when they are empty, which is better than a total
        // written out in a currency nobody named.
        assertThat(params.get("CURRENCY_NAME")).isEqualTo("");
        assertThat(params.get("AMOUNT_IN_WORDS")).isEqualTo("");
    }

    @Test
    @DisplayName("a currency row with no minor-unit count still renders")
    void missingMinorUnitsFallsBack() {
        StudyCurrencyIn legacy = yer();
        legacy.setMinorUnits(null);   // the rows that predate V039 have none
        when(currencyRepository.findFirstByIsFunctionalTrue()).thenReturn(Optional.of(legacy));

        controller.addCurrencyAndWords(params, record, false);

        assertThat((String) params.get("AMOUNT_IN_WORDS")).isNotEmpty().contains("only");
    }

    @Test
    @DisplayName("a record with no net amount reads as zero rather than failing the render")
    void missingAmount() {
        record.setNetAmount(null);

        controller.addCurrencyAndWords(params, record, true);

        assertThat((String) params.get("AMOUNT_IN_WORDS")).contains("صفر");
    }
}
