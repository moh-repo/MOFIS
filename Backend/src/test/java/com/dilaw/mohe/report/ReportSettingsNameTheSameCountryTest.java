package com.dilaw.mohe.report;

import com.dilaw.mohe.systemsettings.model.ReportTypeSettings;
import com.dilaw.mohe.systemsettings.repository.ReportTypeSettingsRepository;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A report must not name one country in Arabic and another in English.
 *
 * <p>`REPORT_TYPE_SETTINGS.TITLE_01` is the republic line at the top of every printed
 * page. On the payment authorisation voucher — نموذج رقم ١١ حسابات — the Arabic said
 * الجمهورية اليمنية and the English said <b>State of Libya</b>, so the same statutory
 * form named a different country depending on which language it was printed in.
 *
 * <p>It survived because nothing rendered it where a person would see it: the screen
 * drew an HTML replica of the form and printed that, and a replica cannot show a
 * masthead that lives in a settings table. It surfaced the day the page started
 * showing the server's PDF. V018 had even carried a repair for it since an earlier
 * revision, but that repair matched {@code TITLE_01_EN = 'State of Yemen'} — the value
 * it was setting rather than the one it was replacing — so it never fired.
 *
 * <p>Asserted against the database rather than the script, because the wording is
 * editable from /settings/report-settings and a screen is exactly where the two
 * languages can be edited apart again.
 */
@Tag("integration")
@SpringBootTest
class ReportSettingsNameTheSameCountryTest {

    /** The Arabic republic line the ministry's reports carry. */
    private static final String YEMEN_AR = "الجمهورية اليمنية";

    /**
     * Its English counterpart. V057 seeds this spelling, and
     * {@code FinancialDataFormV2RenderTest} and {@code HeaderFooterSignatureRenderTest}
     * both assert it, so it is the house spelling rather than one of several.
     */
    private static final String YEMEN_EN = "Republic of Yemen";

    @Autowired private ReportTypeSettingsRepository repository;

    @Test
    @DisplayName("no report names الجمهورية اليمنية in Arabic and some other country in English")
    void theTwoLanguagesNameTheSameCountry() {
        List<ReportTypeSettings> rows = repository.findAll();
        assertThat(rows).as("no report settings at all").isNotEmpty();

        SoftAssertions softly = new SoftAssertions();
        for (ReportTypeSettings row : rows) {
            String ar = trimmed(row.getTitle01Ar());
            String en = trimmed(row.getTitle01En());
            if (!YEMEN_AR.equals(ar) || en.isEmpty()) {
                // A blank English line falls back to the Arabic in the templates, and a
                // row whose Arabic head is something else entirely is not this rule's
                // business — several are the ministry or an embassy rather than the state.
                continue;
            }
            softly.assertThat(en)
                    .as("%s prints '%s' in English over '%s' in Arabic", row.getReportTypeCode(), en, ar)
                    .isEqualTo(YEMEN_EN);
        }
        softly.assertAll();
    }

    @Test
    @DisplayName("the payment voucher's masthead is the Yemeni republic in both languages")
    void theVoucherNamesYemen() {
        // The row this was found on, pinned by name so the regression is unmistakable.
        ReportTypeSettings voucher = repository.findByReportTypeCode("PAYMENT_AUTH_FORM")
                .orElseThrow(() -> new AssertionError(
                        "REPORT_TYPE_SETTINGS has no PAYMENT_AUTH_FORM row; seed V018"));

        assertThat(trimmed(voucher.getTitle01Ar())).isEqualTo(YEMEN_AR);
        assertThat(trimmed(voucher.getTitle01En()))
                .as("the English republic line, which read 'State of Libya' until 1 Sep 2026")
                .isEqualTo(YEMEN_EN);
    }

    private static String trimmed(String s) {
        return s == null ? "" : s.trim();
    }
}
