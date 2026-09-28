package com.dilaw.mohe.report;

import com.dilaw.mohe.security.policy.AccessSubject;
import com.dilaw.mohe.security.policy.AccessSubjectResolver;
import com.dilaw.mohe.service.CountryNameService;
import com.dilaw.mohe.ums.model.AdminTier;
import com.dilaw.mohe.ums.repository.UmsUserRepository;
import com.dilaw.mohe.ums.service.UmsAuditLogService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Export controls (security plan S-07): a row cap per tier and the export
 * line every file carries.
 *
 * <p>The cap is the largest export one account may take in one file. A
 * country clerk gets a few thousand rows - enough for any real list at one
 * attaché, not enough to empty the table from a stolen session; the ministry
 * administrators are uncapped (0). Over the cap the export is refused with
 * 403, audited as {@code EXPORT_CAPPED}, and the user is told to narrow the
 * filter. A run with no signed-in user (a scheduled report) is not capped
 * and is marked as such on the file.
 */
@Component
public class ExportGuard {

    private static final Logger log = LoggerFactory.getLogger(ExportGuard.class);
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final AccessSubjectResolver subjects;
    private final UmsUserRepository users;
    private final CountryNameService countryNames;
    private final UmsAuditLogService auditLog;
    private final Map<AdminTier, Integer> caps;

    public ExportGuard(AccessSubjectResolver subjects, UmsUserRepository users, CountryNameService countryNames,
                       UmsAuditLogService auditLog,
                       @Value("${mohe.export.row-cap.country-staff:5000}") int countryStaff,
                       @Value("${mohe.export.row-cap.country-admin:20000}") int countryAdmin,
                       @Value("${mohe.export.row-cap.ministry-staff:50000}") int ministryStaff,
                       @Value("${mohe.export.row-cap.ministry-admin:0}") int ministryAdmin,
                       @Value("${mohe.export.row-cap.super-admin:0}") int superAdmin) {
        this.subjects = subjects;
        this.users = users;
        this.countryNames = countryNames;
        this.auditLog = auditLog;
        this.caps = Map.of(
                AdminTier.COUNTRY_STAFF, countryStaff,
                AdminTier.COUNTRY_ADMIN, countryAdmin,
                AdminTier.MINISTRY_STAFF, ministryStaff,
                AdminTier.MINISTRY_ADMIN, ministryAdmin,
                AdminTier.SUPER_ADMIN, superAdmin);
    }

    /** The cap for a tier; 0 means unlimited. */
    public int capFor(AdminTier tier) {
        return tier == null ? 0 : caps.getOrDefault(tier, 0);
    }

    /** The cap for the signed-in account; 0 when unlimited or when nobody is signed in. */
    public int currentCap() {
        AccessSubject subject = currentSubject();
        return subject == null || !subject.isAuthenticated() ? 0 : capFor(subject.tier());
    }

    /**
     * Refuses an export of {@code rows} rows above the caller's cap. Called
     * before the rows are rendered, so a refused export costs nothing.
     */
    public void enforce(int rows) {
        AccessSubject subject = currentSubject();
        if (subject == null || !subject.isAuthenticated()) return;
        int cap = capFor(subject.tier());
        if (cap <= 0 || rows <= cap) return;
        String detail = "rows=" + rows + ",cap=" + cap + ",tier=" + subject.tier() + "," + requestSummary();
        try {
            auditLog.log(subject.userId(), "EXPORT_CAPPED", "Export", null, null, detail);
        } catch (RuntimeException e) {
            log.warn("EXPORT_CAPPED audit failed: {}", e.getMessage());
        }
        // 422 with a code, not 403: this is not a permission refusal (the same account
        // may export the same report with a narrower filter), and the staff app reacts
        // to a 403 by reloading the profile. See ReportException (review item P7).
        throw new ReportException(ReportException.Code.REPORT_EXPORT_ROW_CAP,
                "This export would contain " + rows + " rows; your account may export at most " + cap
                        + " rows in one file. Narrow the filter (a period, a country, a status) and try again.",
                rows, cap);
    }

    /** "Exported by jo_admin on 2026-09-06 21:05 for Jordan" - or the scheduled-run form when nobody is signed in. */
    public String watermark() {
        String when = LocalDateTime.now().format(STAMP);
        AccessSubject subject = currentSubject();
        if (subject == null || !subject.isAuthenticated()) {
            return "Generated by MOHE on " + when + " (scheduled run)";
        }
        String who = users.findById(subject.userId()).map(u -> u.getUserName()).orElse("user " + subject.userId());
        return "Exported by " + who + " on " + when + " for " + scopeLabel(subject);
    }

    private String scopeLabel(AccessSubject subject) {
        if (subject.isMinistryLevel()) return "the Ministry";
        List<Integer> ids = subject.countryIds();
        if (ids == null || ids.isEmpty()) return "no country";
        Map<Integer, String> names = countryNames.nameMap(false);
        if (ids.size() > 3) return ids.size() + " countries";
        return ids.stream().map(id -> names.getOrDefault(id, String.valueOf(id))).collect(Collectors.joining(", "));
    }

    private AccessSubject currentSubject() {
        try {
            return subjects.current();
        } catch (RuntimeException e) {
            log.debug("Export guard could not resolve the caller: {}", e.getMessage());
            return null;
        }
    }

    private static String requestSummary() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            String uri = attrs.getRequest().getRequestURI();
            return "path=" + (uri == null ? "" : uri);
        }
        return "path=";
    }
}
