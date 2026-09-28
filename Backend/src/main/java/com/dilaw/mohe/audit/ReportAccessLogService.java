package com.dilaw.mohe.audit;

import com.dilaw.mohe.ums.service.CountryScopeService;
import com.dilaw.mohe.ums.service.CountryScopeService.CountryScope;
import com.dilaw.mohe.util.SecurityContextHelper;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;

/**
 * Records who exported which report.
 *
 * <p>The row is <em>built</em> on the request thread and <em>written</em> on the
 * async pool by {@link ReportAccessLogWriter}. The whole method used to be
 * {@code @Async}, which moved the read of the security context to a pool thread
 * that has none: every row in {@code REPORT_ACCESS_LOG} says {@code system}, and
 * with the tenant column added here the country would have been unknowable there
 * too. Username, client address and country scope are all facts of the request,
 * so they are read where the request is.
 */
@Service
public class ReportAccessLogService {

    private static final Logger log = LoggerFactory.getLogger(ReportAccessLogService.class);

    private final ReportAccessLogWriter writer;
    private final CountryScopeService countryScopeService;

    public ReportAccessLogService(ReportAccessLogWriter writer, CountryScopeService countryScopeService) {
        this.writer = writer;
        this.countryScopeService = countryScopeService;
    }

    /**
     * Persists a report access record without blocking the response.
     * Never throws — logging failures must not affect the HTTP response.
     *
     * @param reportCode   e.g. "QUARTERLY_FINANCIAL", "CUSTOM_FILTER", "STUDENT_STATUS"
     * @param exportFormat "PDF" or "EXCEL"
     * @param filterSummary compact description of active filters (free-form string)
     */
    public void logExport(String reportCode, String exportFormat, String filterSummary) {
        logExport(reportCode, exportFormat, filterSummary, null, null);
    }

    /**
     * As above, plus what the export turned out to contain.
     *
     * <p>{@code rowCount} and {@code sizeBytes} are what the Export Manager shows
     * as "records" and "size"; before V106 both were columns the view selected as
     * literal NULL, and this is the only moment either is known. Passing null for
     * them is legitimate — an access that produced no file has no size.
     */
    public void logExport(String reportCode, String exportFormat, String filterSummary,
                          Integer rowCount, Long sizeBytes) {
        try {
            ReportAccessLog entry = prepare(reportCode, exportFormat, filterSummary);
            entry.setRowCount(rowCount);
            entry.setSizeBytes(sizeBytes);
            writer.persist(entry);
        } catch (Exception ex) {
            log.warn("Failed to log report access for {}: {}", reportCode, ex.getMessage());
        }
    }

    /**
     * The row as it will be written, with everything that has to come from the
     * request thread already on it: the user, their address and their country.
     *
     * <p>The tenant stamp follows the rule the rest of the tenanted tables use: a
     * user whose scope is one study country writes that id; a ministry user, or one
     * holding several countries, writes NULL — global, visible to whoever may see
     * the whole table.
     */
    public ReportAccessLog prepare(String reportCode, String exportFormat, String filterSummary) {
        Integer portalUser = portalStudentUserId();
        String who = portalUser != null ? "portal:student-user:" + portalUser : SecurityContextHelper.currentUsername();
        ReportAccessLog entry = new ReportAccessLog(reportCode, exportFormat, who, filterSummary, resolveClientIp());
        if (portalUser != null) {
            // S-11: a student's own download, under the country pinned on the session.
            java.util.Set<Integer> ids = com.dilaw.mohe.config.tenant.TenantContext.current().countryIds();
            entry.setStudyCountryInId(ids != null && ids.size() == 1 ? ids.iterator().next() : null);
        } else {
            entry.setStudyCountryInId(tenantOf(countryScopeService.getScope()));
        }
        return entry;
    }

    /** The portal account behind the request, when the portal interceptor resolved one. */
    private static Integer portalStudentUserId() {
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs == null) return null;
            Object id = attrs.getRequest().getAttribute(
                    com.dilaw.mohe.studentportal.StudentPortalTokenInterceptor.ATTR_STUDENT_USER_ID);
            return id instanceof Integer i ? i : null;
        } catch (Exception e) {
            return null;
        }
    }

    static Integer tenantOf(CountryScope scope) {
        if (scope == null || scope.isMinistryUser()) {
            return null;
        }
        List<Integer> allowed = scope.getAllowedCountryIds();
        return allowed.size() == 1 ? allowed.get(0) : null;
    }

    private static String resolveClientIp() {
        try {
            ServletRequestAttributes attrs =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs == null) return null;
            HttpServletRequest req = attrs.getRequest();
            String forwarded = req.getHeader("X-Forwarded-For");
            return (forwarded != null && !forwarded.isBlank())
                    ? forwarded.split(",")[0].trim()
                    : req.getRemoteAddr();
        } catch (Exception e) {
            return null;
        }
    }
}
