package com.dilaw.mohe.report.jasper;

import com.dilaw.mohe.service.codelist.CodeListService;
import com.dilaw.mohe.service.codelist.CodeRow;
import com.dilaw.mohe.systemsettings.dto.SysSettingsDto;
import com.dilaw.mohe.systemsettings.model.ReportTypeSettings;
import com.dilaw.mohe.systemsettings.repository.ReportTypeSettingsRepository;
import com.dilaw.mohe.systemsettings.service.SysSettingsService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Builds the shared parameter map injected into every Jasper report.
 *
 * <p>Everything printed around the data comes from the database:
 * <ul>
 *   <li>the masthead titles, the report title, the footer note and the
 *       signatories from {@code MOHECORE.REPORT_TYPE_SETTINGS} — every report
 *       code has a row (V077 proves it); a code without one is an error, not
 *       a silently blank or invented head;</li>
 *   <li>the ministry name from {@code SYS_SETTINGS} when a title line is blank;</li>
 *   <li>every {@code COL_*} / {@code LBL_*} template parameter — column heads,
 *       box captions, statutory notes, the voucher wording — from
 *       {@code MOHECORE.LU_REPORT_LABEL} (the {@code report-label} code list,
 *       cached by {@link CodeListService}). A row named
 *       {@code CODE@REPORT_TYPE_CODE} overrides the base wording for that one
 *       report, which is how a form keeps its own reading of a shared parameter
 *       name ("الحالة" on the approvals queue, "الحالة الدراسية" on a student
 *       list).</li>
 * </ul>
 * The templates declare their parameters with an empty default, so a label
 * missing from the table prints as blank — {@code ReportLabelsAreSeededTest}
 * asserts that every parameter of every template has an active row.
 */
@Service
@RequiredArgsConstructor
public class ReportParamBuilder {

    private static final Logger log = LoggerFactory.getLogger(ReportParamBuilder.class);
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final String REPORT_LABELS = "report-label";

    @org.springframework.beans.factory.annotation.Value("${mohe.report.timezone:Asia/Riyadh}")
    private String reportTimezone;

    private final ReportTypeSettingsRepository settingsRepo;
    private final SysSettingsService sysSettingsService;
    private final CodeListService codeLists;

    /**
     * Build the base parameter map for a given report code and language.
     * Additional report-specific params (YEAR, QUARTER, etc.) should be added by the caller.
     *
     * @param reportCode unique code matching REPORT_TYPE_CODE in DB (e.g. "FINANCIAL_DATA_FORM")
     * @param lang       "ar" for Arabic (RTL) or "en" for English (LTR)
     * @throws IllegalStateException when REPORT_TYPE_SETTINGS has no row for the code
     */
    public Map<String, Object> build(String reportCode, String lang) {
        boolean rtl = !"en".equalsIgnoreCase(lang);
        Map<String, Object> p = new HashMap<>();

        p.put("IS_RTL", rtl);
        p.put("LANG", rtl ? "ar" : "en");
        p.put("REPORT_DATE", LocalDate.now(ZoneId.of(reportTimezone)).format(DATE_FMT));

        // System logo
        try {
            byte[] logoBytes = sysSettingsService.getLogoBytes();
            if (logoBytes != null && logoBytes.length > 0) {
                p.put("LOGO_IMAGE", new ByteArrayInputStream(logoBytes));
            }
        } catch (Exception e) {
            log.debug("No logo available for report {}: {}", reportCode, e.getMessage());
        }

        // Header titles and signatories from DB
        String ministryName = resolveMinistryName(rtl);
        String ministryNameAr = resolveMinistryName(true);
        String ministryNameEn = resolveMinistryName(false);
        ReportTypeSettings s = settingsRepo.findByReportTypeCode(reportCode)
                .orElseThrow(() -> new IllegalStateException(
                        "MOHECORE.REPORT_TYPE_SETTINGS has no row for report code '" + reportCode
                                + "'. The masthead, title, footer and signatories of every report come from that "
                                + "table and there is no Java default; seed the row (see Backend/docs/MIGRATION_RUNBOOK.md, V077)."));

        p.put("TITLE_01", firstNonBlank(rtl ? s.getTitle01Ar() : s.getTitle01En(), ministryName));
        p.put("TITLE_02", nvl(rtl ? s.getTitle02Ar() : s.getTitle02En()));
        p.put("TITLE_03", nvl(rtl ? s.getTitle03Ar() : s.getTitle03En()));
        p.put("TITLE_04", nvl(rtl ? s.getTitle04Ar() : s.getTitle04En()));
        p.put("TITLE_05", nvl(rtl ? s.getTitle05Ar() : s.getTitle05En()));
        p.put("FOOTER_TEXT", nvl(rtl ? s.getFooterTextAr() : s.getFooterTextEn()));

        // Both-language title pairs for the bilingual official masthead
        // (Arabic stack right, English stack left, logo center).
        p.put("TITLE_01_AR", firstNonBlank(s.getTitle01Ar(), ministryNameAr));
        p.put("TITLE_02_AR", nvl(s.getTitle02Ar()));
        p.put("TITLE_03_AR", nvl(s.getTitle03Ar()));
        p.put("TITLE_01_EN", firstNonBlank(s.getTitle01En(), ministryNameEn));
        p.put("TITLE_02_EN", nvl(s.getTitle02En()));
        p.put("TITLE_03_EN", nvl(s.getTitle03En()));
        p.put("REPORT_TITLE", firstNonBlank(rtl ? s.getReportTypeNameAr() : s.getReportTypeNameEn(), ""));

        int sigCount = s.getSignatoryCount() != null ? s.getSignatoryCount() : 0;
        p.put("SIG_COUNT", sigCount);
        for (int i = 1; i <= 5; i++) {
            p.put("SIG_NAME_" + i, nvl(getSigName(s, i, rtl)));
            p.put("SIG_POS_" + i,  nvl(getSigPos(s, i, rtl)));
        }

        p.put("PRINTED_BY", resolvePrintedBy());

        addReportLabels(reportCode, p, rtl);

        return p;
    }

    /** Username of the authenticated caller, or "" outside a request context. */
    private static String resolvePrintedBy() {
        try {
            var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated() && auth.getName() != null
                    && !"anonymousUser".equals(auth.getName())) {
                return auth.getName();
            }
        } catch (Exception ignored) {
            // reports can be generated outside a request (scheduled runs)
        }
        return "";
    }

    /**
     * Puts every active {@code COL_*} / {@code LBL_*} row of LU_REPORT_LABEL into
     * the map in the requested language, then the report's own override rows
     * ({@code CODE@REPORT_TYPE_CODE}) on top. Every template parameter is
     * supplied whatever the report code, so a template never falls back to its
     * (blank) default; the text is the row's own — an empty English text stays
     * empty rather than borrowing the Arabic, because some captions
     * (LBL_YEAR_SUFFIX "م") exist in one language only.
     */
    public void addReportLabels(String reportCode, Map<String, Object> p, boolean rtl) {
        String suffix = reportCode == null ? null : "@" + reportCode.toUpperCase(Locale.ROOT);
        Map<String, String> overrides = new HashMap<>();
        for (CodeRow row : codeLists.rows(REPORT_LABELS)) {
            String code = row.code();
            if (!row.active() || code == null || !isTemplateLabel(code)) {
                continue;
            }
            String text = rtl ? nvl(row.nameAr()) : nvl(row.nameEn());
            int at = code.indexOf('@');
            if (at < 0) {
                p.put(code, text);
            } else if (suffix != null && code.substring(at).equalsIgnoreCase(suffix)) {
                overrides.put(code.substring(0, at), text);
            }
        }
        p.putAll(overrides);
    }

    /** The codes the templates declare as parameters: column heads and captions. */
    public static boolean isTemplateLabel(String code) {
        return code.startsWith("COL_") || code.startsWith("LBL_");
    }

    // ── Signatory helpers ─────────────────────────────────────────────────────

    private String getSigName(ReportTypeSettings s, int idx, boolean ar) {
        return switch (idx) {
            case 1 -> ar ? s.getSignatoryName01Ar() : s.getSignatoryName01En();
            case 2 -> ar ? s.getSignatoryName02Ar() : s.getSignatoryName02En();
            case 3 -> ar ? s.getSignatoryName03Ar() : s.getSignatoryName03En();
            case 4 -> ar ? s.getSignatoryName04Ar() : s.getSignatoryName04En();
            case 5 -> ar ? s.getSignatoryName05Ar() : s.getSignatoryName05En();
            default -> "";
        };
    }

    private String getSigPos(ReportTypeSettings s, int idx, boolean ar) {
        return switch (idx) {
            case 1 -> ar ? s.getSignatoryPos01Ar() : s.getSignatoryPos01En();
            case 2 -> ar ? s.getSignatoryPos02Ar() : s.getSignatoryPos02En();
            case 3 -> ar ? s.getSignatoryPos03Ar() : s.getSignatoryPos03En();
            case 4 -> ar ? s.getSignatoryPos04Ar() : s.getSignatoryPos04En();
            case 5 -> ar ? s.getSignatoryPos05Ar() : s.getSignatoryPos05En();
            default -> "";
        };
    }

    private static String nvl(String s) {
        return s != null ? s : "";
    }

    private String resolveMinistryName(boolean rtl) {
        SysSettingsDto settings = sysSettingsService.getSettings();
        if (settings == null) {
            return "";
        }
        if (rtl) {
            return firstNonBlank(settings.getSysNameAr(), settings.getSysNameEn());
        }
        return firstNonBlank(settings.getSysNameEn(), settings.getSysNameAr());
    }

    private static String firstNonBlank(String primary, String fallback) {
        if (primary != null && !primary.isBlank()) {
            return primary;
        }
        return fallback != null ? fallback : "";
    }
}
