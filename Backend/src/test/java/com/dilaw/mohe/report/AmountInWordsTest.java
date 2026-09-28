package com.dilaw.mohe.report;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * التفقيط — the amount written out, which is what makes a tampered figure visible on a
 * disbursement voucher.
 *
 * <p>Worth testing closely because Arabic numerals are not a digit-by-digit reading:
 * the units come before the tens, two is a dual rather than a counted noun, and the
 * scale words change form with the number in front of them (ثلاثة آلاف but أحد عشر
 * ألفًا but مائة ألف). Getting any of those wrong produces something that still looks
 * like Arabic to a reader who does not speak it.
 */
class AmountInWordsTest {

    private static final AmountInWords.Currency YER =
            new AmountInWords.Currency("YER", "ريال يمني", "Yemeni Rial", 2);
    private static final AmountInWords.Currency JOD =
            new AmountInWords.Currency("JOD", "دينار أردني", "Jordanian Dinar", 3);

    private static String ar(String amount) {
        return AmountInWords.of(new BigDecimal(amount), YER, true);
    }

    private static String en(String amount) {
        return AmountInWords.of(new BigDecimal(amount), YER, false);
    }

    @Nested
    @DisplayName("Arabic")
    class Arabic {

        @Test
        @DisplayName("the amount is fenced by فقط and لا غير so nothing can be added to either end")
        void theAmountIsFenced() {
            assertThat(ar("500")).startsWith("فقط ").endsWith(" لا غير");
        }

        @ParameterizedTest(name = "{0} → {1}")
        @CsvSource({
                "1,        فقط واحد ريال يمني لا غير",
                "2,        فقط اثنان ريال يمني لا غير",
                "10,       فقط عشرة ريال يمني لا غير",
                "11,       فقط أحد عشر ريال يمني لا غير",
                "20,       فقط عشرون ريال يمني لا غير",
                "100,      فقط مائة ريال يمني لا غير",
                "200,      فقط مئتان ريال يمني لا غير",
                "300,      فقط ثلاثمائة ريال يمني لا غير",
                "500,      فقط خمسمائة ريال يمني لا غير",
                "900,      فقط تسعمائة ريال يمني لا غير"
        })
        @DisplayName("units, teens, tens and hundreds")
        void smallNumbers(String amount, String expected) {
            assertThat(ar(amount)).isEqualTo(expected);
        }

        @Test
        @DisplayName("the units are said before the tens, joined by و")
        void unitsComeBeforeTens() {
            // خمسة وعشرون, not عشرون وخمسة — the opposite order to the digits.
            assertThat(ar("25")).isEqualTo("فقط خمسة وعشرون ريال يمني لا غير");
            assertThat(ar("99")).isEqualTo("فقط تسعة وتسعون ريال يمني لا غير");
        }

        @Test
        @DisplayName("hundreds join their remainder with و")
        void hundredsJoinTheRemainder() {
            assertThat(ar("125")).isEqualTo("فقط مائة وخمسة وعشرون ريال يمني لا غير");
            assertThat(ar("101")).isEqualTo("فقط مائة وواحد ريال يمني لا غير");
        }

        @ParameterizedTest(name = "{0} → {1}")
        @CsvSource({
                "1000,     فقط ألف ريال يمني لا غير",
                "2000,     فقط ألفان ريال يمني لا غير",
                "3000,     فقط ثلاثة آلاف ريال يمني لا غير",
                "10000,    فقط عشرة آلاف ريال يمني لا غير",
                "11000,    فقط أحد عشر ألفًا ريال يمني لا غير",
                "100000,   فقط مائة ألف ريال يمني لا غير",
                "1000000,  فقط مليون ريال يمني لا غير",
                "2000000,  فقط مليونان ريال يمني لا غير",
                "3000000,  فقط ثلاثة ملايين ريال يمني لا غير"
        })
        @DisplayName("the scale word takes the form the number in front of it demands")
        void scaleWordsAgree(String amount, String expected) {
            assertThat(ar(amount)).isEqualTo(expected);
        }

        @Test
        @DisplayName("a realistic voucher amount")
        void aRealisticAmount() {
            assertThat(ar("1500")).isEqualTo("فقط ألف وخمسمائة ريال يمني لا غير");
            assertThat(ar("2450000")).isEqualTo("فقط مليونان وأربعمائة وخمسون ألفًا ريال يمني لا غير");
        }

        @Test
        @DisplayName("an empty group is skipped rather than read as zero")
        void emptyGroupsAreSkipped() {
            // 1,000,500 has no thousands-of-thousands to announce.
            assertThat(ar("1000500")).isEqualTo("فقط مليون وخمسمائة ريال يمني لا غير");
        }

        @Test
        @DisplayName("fils are named and joined with و")
        void fractionIsRead() {
            assertThat(ar("500.50")).isEqualTo("فقط خمسمائة ريال يمني وخمسون فلس لا غير");
        }

        @Test
        @DisplayName("a whole amount says nothing about fils at all")
        void wholeAmountsSayNothingAboutTheFraction() {
            assertThat(ar("500.00")).doesNotContain("فلس");
            assertThat(ar("500")).doesNotContain("فلس");
        }

        @Test
        @DisplayName("zero, null and a negative all read as zero rather than throwing")
        void degenerateAmounts() {
            assertThat(ar("0")).isEqualTo("فقط صفر ريال يمني لا غير");
            assertThat(AmountInWords.of(null, YER, true)).isEqualTo("فقط صفر ريال يمني لا غير");
            assertThat(ar("-5")).isEqualTo("فقط صفر ريال يمني لا غير");
        }
    }

    @Nested
    @DisplayName("English")
    class English {

        @Test
        @DisplayName("the amount is closed with only, and starts with a capital")
        void theAmountIsFenced() {
            assertThat(en("500")).isEqualTo("Five hundred Yemeni Rials only");
        }

        @ParameterizedTest(name = "{0} → {1}")
        @CsvSource({
                "1,        One Yemeni Rial only",
                "21,       Twenty-one Yemeni Rials only",
                "115,      One hundred and fifteen Yemeni Rials only",
                "1500,     One thousand five hundred Yemeni Rials only",
                "1000000,  One million Yemeni Rials only"
        })
        @DisplayName("reads the way an English cheque reads")
        void englishReadings(String amount, String expected) {
            assertThat(en(amount)).isEqualTo(expected);
        }

        @Test
        @DisplayName("one rial is singular, everything else is plural")
        void singularAndPlural() {
            assertThat(en("1")).contains("One Yemeni Rial only");
            assertThat(en("2")).contains("Yemeni Rials");
            assertThat(en("0")).contains("Yemeni Rials");
        }

        @Test
        @DisplayName("the fraction is named")
        void fractionIsRead() {
            assertThat(en("500.50")).isEqualTo("Five hundred Yemeni Rials and fifty fils only");
        }
    }

    @Nested
    @DisplayName("other currencies")
    class OtherCurrencies {

        @Test
        @DisplayName("a three-decimal currency reads its own fraction")
        void threeDecimalCurrency() {
            // The dinar has 1000 fils to the dinar, not 100.
            assertThat(AmountInWords.of(new BigDecimal("5.250"), JOD, true))
                    .isEqualTo("فقط خمسة دينار أردني ومئتان وخمسون فلس لا غير");
        }

        @Test
        @DisplayName("an unknown currency names no fractional unit rather than inventing one")
        void unknownMinorUnit() {
            AmountInWords.Currency odd =
                    new AmountInWords.Currency("XXX", "عملة", "Currency", 2);

            assertThat(AmountInWords.of(new BigDecimal("5.25"), odd, true))
                    .isEqualTo("فقط خمسة عملة وخمسة وعشرون لا غير");
        }

        @Test
        @DisplayName("the definite article is dropped: a counted noun does not carry one")
        void theArticleIsDropped() {
            // The lookup stores display names — الريال اليمني — which is right for a
            // dropdown and wrong after a number. "ألف الريال اليمني" is not how a
            // voucher reads.
            AmountInWords.Currency stored =
                    new AmountInWords.Currency("YER", "الريال اليمني", "Yemeni Rial", 2);

            assertThat(AmountInWords.of(new BigDecimal("1000"), stored, true))
                    .isEqualTo("فقط ألف ريال يمني لا غير");
        }

        @Test
        @DisplayName("a name with no article is left alone")
        void nameWithoutArticle() {
            AmountInWords.Currency euro =
                    new AmountInWords.Currency("EUR", "يورو", "Euro", 2);

            assertThat(AmountInWords.of(new BigDecimal("5"), euro, true))
                    .isEqualTo("فقط خمسة يورو لا غير");
        }

        @Test
        @DisplayName("no currency at all still writes the number out")
        void noCurrency() {
            assertThat(AmountInWords.of(new BigDecimal("42"), null, true))
                    .isEqualTo("فقط اثنان وأربعون لا غير");
            assertThat(AmountInWords.of(new BigDecimal("42"), null, false))
                    .isEqualTo("Forty-two only");
        }
    }
}
