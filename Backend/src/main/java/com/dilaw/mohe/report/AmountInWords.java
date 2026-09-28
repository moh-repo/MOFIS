package com.dilaw.mohe.report;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * التفقيط — an amount written out in words, in Arabic or English.
 *
 * <p>On a disbursement voucher the figure and the words are a pair: the words are what
 * makes a tampered digit visible, which is why نموذج رقم ١١ has a line for them. There
 * was no number-to-words anywhere in this system, so this is it.
 *
 * <p>The Arabic follows the conventions a ministry cheque uses rather than a literal
 * digit-by-digit reading:
 * <ul>
 *   <li>opened with <b>فقط</b> and closed with <b>لا غير</b>, the pair that fences the
 *       amount so nothing can be added at either end;</li>
 *   <li>units before tens, joined by و — خمسة وعشرون, not عشرون وخمسة;</li>
 *   <li>the dual for exactly two — مئتان, ألفان, مليونان — never اثنان مائة;</li>
 *   <li>the counted-noun rules for the scale words: ثلاثة آلاف (3–10 take the plural),
 *       أحد عشر ألفًا (11–99 take the singular accusative), مائة ألف (100+ take the
 *       singular);</li>
 *   <li>مائة spelled as the ministry's forms spell it, and ثلاثمائة written solid.</li>
 * </ul>
 *
 * <p>The currency is passed in rather than assumed: the major unit's name comes from
 * {@code STUDY_CURRENCY_IN}, and the minor unit's from {@link #MINOR_UNIT_NAMES} keyed
 * by ISO code. An amount with no fractional part says nothing about the minor unit at
 * all, which is the common case and the right reading.
 */
public final class AmountInWords {

    private AmountInWords() {
    }

    /** What a currency is called on the page, in both languages and both numbers. */
    public record Currency(String isoCode, String nameAr, String nameEn, int minorUnits) {
    }

    // ── Arabic ───────────────────────────────────────────────────────────────

    private static final String[] AR_ONES = {
            "", "واحد", "اثنان", "ثلاثة", "أربعة", "خمسة", "ستة", "سبعة", "ثمانية", "تسعة",
            "عشرة", "أحد عشر", "اثنا عشر", "ثلاثة عشر", "أربعة عشر", "خمسة عشر",
            "ستة عشر", "سبعة عشر", "ثمانية عشر", "تسعة عشر"
    };

    private static final String[] AR_TENS = {
            "", "", "عشرون", "ثلاثون", "أربعون", "خمسون", "ستون", "سبعون", "ثمانون", "تسعون"
    };

    private static final String[] AR_HUNDREDS = {
            "", "مائة", "مئتان", "ثلاثمائة", "أربعمائة", "خمسمائة",
            "ستمائة", "سبعمائة", "ثمانمائة", "تسعمائة"
    };

    /** singular / dual / plural (3–10) / singular-after-11-99, per scale. */
    private static final String[][] AR_SCALES = {
            {"", "", "", ""},
            {"ألف", "ألفان", "آلاف", "ألفًا"},
            {"مليون", "مليونان", "ملايين", "مليونًا"},
            {"مليار", "ملياران", "مليارات", "مليارًا"}
    };

    // ── English ──────────────────────────────────────────────────────────────

    private static final String[] EN_ONES = {
            "", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine",
            "ten", "eleven", "twelve", "thirteen", "fourteen", "fifteen",
            "sixteen", "seventeen", "eighteen", "nineteen"
    };

    private static final String[] EN_TENS = {
            "", "", "twenty", "thirty", "forty", "fifty", "sixty", "seventy", "eighty", "ninety"
    };

    private static final String[] EN_SCALES = {"", "thousand", "million", "billion"};

    /**
     * The fractional unit, which {@code STUDY_CURRENCY_IN} does not record — it stores
     * how many digits the currency has, not what they are called. Keyed by ISO code;
     * an unlisted currency simply has its fraction read as a number of parts.
     */
    private static final Map<String, String[]> MINOR_UNIT_NAMES = Map.of(
            "YER", new String[]{"فلس", "fils"},
            "SAR", new String[]{"هللة", "halalas"},
            "AED", new String[]{"فلس", "fils"},
            "JOD", new String[]{"فلس", "fils"},
            "OMR", new String[]{"بيسة", "baisa"},
            "USD", new String[]{"سنت", "cents"},
            "EUR", new String[]{"سنت", "cents"},
            "GBP", new String[]{"بنس", "pence"},
            "EGP", new String[]{"قرش", "piastres"},
            "SDG", new String[]{"قرش", "piastres"}
    );

    /**
     * The amount in words, fenced and ready to print.
     *
     * @param amount   the figure on the voucher; null or negative reads as zero
     * @param currency the currency it is denominated in; may be null, in which case
     *                 the number is written out with no unit named
     * @param arabic   true for التفقيط, false for the English reading
     */
    public static String of(BigDecimal amount, Currency currency, boolean arabic) {
        // Two digits unless the currency says otherwise — the dinar has three. Clamped
        // because a stray value here would only surface as an arithmetic exception.
        int minorDigits = currency == null ? 2 : Math.max(0, Math.min(4, currency.minorUnits()));
        BigDecimal value = (amount == null || amount.signum() < 0 ? BigDecimal.ZERO : amount)
                .setScale(minorDigits, RoundingMode.HALF_UP);

        BigDecimal majorPart = value.setScale(0, RoundingMode.DOWN);
        long major = majorPart.longValueExact();
        long minor = value.subtract(majorPart).movePointRight(minorDigits).longValueExact();

        return arabic ? arabic(major, minor, currency, minorDigits)
                      : english(major, minor, currency);
    }

    // ── Arabic assembly ──────────────────────────────────────────────────────

    private static String arabic(long major, long minor, Currency currency, int minorDigits) {
        StringBuilder sb = new StringBuilder("فقط ");
        sb.append(major == 0 ? "صفر" : arabicNumber(major));
        if (currency != null && notBlank(currency.nameAr())) {
            sb.append(' ').append(indefinite(currency.nameAr()));
        }
        if (minor > 0) {
            sb.append(" و").append(arabicNumber(minor));
            String unit = minorUnit(currency, true);
            if (unit != null) {
                sb.append(' ').append(unit);
            }
        }
        return sb.append(" لا غير").toString();
    }

    /** Groups of three, most significant first, each with its scale word. */
    private static String arabicNumber(long n) {
        if (n < 1000) {
            return arabicUnderThousand(n);
        }
        List<String> parts = new java.util.ArrayList<>();
        java.util.Deque<Long> groups = new java.util.ArrayDeque<>();
        for (long rest = n; rest > 0; rest /= 1000) {
            groups.push(rest % 1000);
        }
        int total = groups.size();
        for (int i = 0; i < total; i++) {
            long group = groups.pop();
            int scale = total - 1 - i;
            if (group == 0) {
                continue;
            }
            parts.add(scale == 0 ? arabicUnderThousand(group) : arabicScaled(group, scale));
        }
        return String.join(" و", parts);
    }

    /**
     * A group with its scale word, in the form the number in front of it demands.
     *
     * <p>ألف alone for one and ألفان for two — the dual, not a counted noun. Above that
     * the form follows the <em>last two digits</em>, because that is the part the
     * counted noun agrees with: 3–10 take the plural (ثلاثة آلاف, مائة وثلاثة آلاف), a
     * round hundred takes the plain singular (مائة ألف, ثلاثمائة ألف), and anything
     * else takes the singular accusative (أحد عشر ألفًا, أربعمائة وخمسون ألفًا).
     */
    private static String arabicScaled(long group, int scale) {
        String[] forms = AR_SCALES[Math.min(scale, AR_SCALES.length - 1)];
        if (group == 1) {
            return forms[0];
        }
        if (group == 2) {
            return forms[1];
        }
        long tail = group % 100;
        String form;
        if (tail == 0) {
            form = forms[0];
        } else if (tail >= 3 && tail <= 10) {
            form = forms[2];
        } else {
            form = forms[3];
        }
        return arabicUnderThousand(group) + " " + form;
    }

    private static String arabicUnderThousand(long n) {
        List<String> parts = new java.util.ArrayList<>();
        long rest = n;
        if (rest >= 100) {
            parts.add(AR_HUNDREDS[(int) (rest / 100)]);
            rest %= 100;
        }
        if (rest > 0) {
            if (rest < 20) {
                parts.add(AR_ONES[(int) rest]);
            } else {
                long ones = rest % 10;
                // Units before tens, joined by و — the Arabic order, not the digits'.
                parts.add(ones > 0 ? AR_ONES[(int) ones] + " و" + AR_TENS[(int) (rest / 10)]
                                   : AR_TENS[(int) (rest / 10)]);
            }
        }
        return String.join(" و", parts);
    }

    // ── English assembly ─────────────────────────────────────────────────────

    private static String english(long major, long minor, Currency currency) {
        StringBuilder sb = new StringBuilder();
        sb.append(major == 0 ? "zero" : englishNumber(major));
        if (currency != null && notBlank(currency.nameEn())) {
            sb.append(' ').append(currency.nameEn());
            if (major != 1) {
                sb.append('s');
            }
        }
        if (minor > 0) {
            sb.append(" and ").append(englishNumber(minor));
            String unit = minorUnit(currency, false);
            if (unit != null) {
                sb.append(' ').append(unit);
            }
        }
        String out = sb.append(" only").toString();
        return Character.toUpperCase(out.charAt(0)) + out.substring(1);
    }

    private static String englishNumber(long n) {
        if (n < 1000) {
            return englishUnderThousand(n);
        }
        List<String> parts = new java.util.ArrayList<>();
        java.util.Deque<Long> groups = new java.util.ArrayDeque<>();
        for (long rest = n; rest > 0; rest /= 1000) {
            groups.push(rest % 1000);
        }
        int total = groups.size();
        for (int i = 0; i < total; i++) {
            long group = groups.pop();
            int scale = total - 1 - i;
            if (group == 0) {
                continue;
            }
            parts.add(scale == 0 ? englishUnderThousand(group)
                                 : englishUnderThousand(group) + " " + EN_SCALES[Math.min(scale, 3)]);
        }
        return String.join(" ", parts);
    }

    private static String englishUnderThousand(long n) {
        StringBuilder sb = new StringBuilder();
        long rest = n;
        if (rest >= 100) {
            sb.append(EN_ONES[(int) (rest / 100)]).append(" hundred");
            rest %= 100;
            if (rest > 0) {
                sb.append(" and ");
            }
        }
        if (rest >= 20) {
            sb.append(EN_TENS[(int) (rest / 10)]);
            if (rest % 10 > 0) {
                sb.append('-').append(EN_ONES[(int) (rest % 10)]);
            }
        } else if (rest > 0) {
            sb.append(EN_ONES[(int) rest]);
        }
        return sb.toString();
    }

    // ── Shared ───────────────────────────────────────────────────────────────

    private static String minorUnit(Currency currency, boolean arabic) {
        if (currency == null || currency.isoCode() == null) {
            return null;
        }
        String[] names = MINOR_UNIT_NAMES.get(currency.isoCode().toUpperCase(Locale.ROOT));
        return names == null ? null : names[arabic ? 0 : 1];
    }

    /**
     * Drops the definite article from each word of a currency's name.
     *
     * <p>The lookup stores display names — الريال اليمني, الجنيه المصري — which is what
     * belongs in a dropdown and at the head of a column. A counted noun does not carry
     * the article, so writing the amount out with the stored name gives "ألف وأربعمائة
     * الريال اليمني", which is not how a voucher reads. The words line uses the
     * indefinite form: "ألف وأربعمائة ريال يمني".
     *
     * <p>Applied only here. The stored name is untouched, and the column head still
     * shows it exactly as the ministry entered it.
     */
    private static String indefinite(String arabicName) {
        StringBuilder out = new StringBuilder();
        for (String word : arabicName.trim().split("\\s+")) {
            if (!out.isEmpty()) {
                out.append(' ');
            }
            // Only when something is left: a currency actually named "ال" is not a
            // thing, but a two-letter word should not become empty either.
            out.append(word.startsWith("ال") && word.length() > 2 ? word.substring(2) : word);
        }
        return out.toString();
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
