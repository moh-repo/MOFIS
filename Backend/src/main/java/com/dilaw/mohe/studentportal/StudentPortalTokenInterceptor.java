package com.dilaw.mohe.studentportal;

import com.dilaw.mohe.config.tenant.TenantContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Validates X-Student-Portal-Token on every student-portal protected request.
 * Resolves token against STUDENTCORE.student_sessions (StudentUserAuthService).
 *
 * <p>PRT-10: there is no fallback to the legacy MOHECORE.StudentPortalSession any
 * more. Those tokens were checked against their own table only, so they survived a
 * logout, a password change and the account being deactivated or deleted, and they
 * carried no student-user id (the change-request endpoints refused them, everything
 * else served them). Their sessions were issued before the STUDENTCORE migration and
 * are at most seven days long; a client still holding one signs in again.
 *
 * <p>Also publishes the request's tenant scope. TenantContextFilter runs before
 * the DispatcherServlet and so before this interceptor; finding no UMS
 * principal, it sets DENY. Only once the token is resolved here is there a
 * student id to derive a country from, so the scope is set at that point and
 * restored in afterCompletion. See {@link StudentPortalTenantScope}.
 */
@Component
public class StudentPortalTokenInterceptor implements HandlerInterceptor {

    public static final String ATTR_STUDENT_ID      = "studentPortalStudentId";
    public static final String ATTR_STUDENT_USER_ID = "studentPortalStudentUserId";
    public static final String HEADER_TOKEN          = "X-Student-Portal-Token";
    /** The 403 code of a session held to the password change (SEC-001). */
    public static final String PASSWORD_CHANGE_REQUIRED = "PORTAL_PASSWORD_CHANGE_REQUIRED";

    @Value("${cors.allowed-origins:http://localhost:4200}")
    private String allowedOrigins;

    @Autowired
    private StudentUserAuthService studentUserAuthService;

    @Autowired
    private StudentPortalTenantScope tenantScope;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String token = request.getHeader(HEADER_TOKEN);

        // Resolving a token reads STUDENTCORE.student_sessions, which V037 puts
        // under the tenant policy. This runs before any scope exists — working
        // out who is asking is what produces the scope — so the read has to be
        // elevated or every logged-in student gets a 401 on every request.
        //
        // Safe to elevate at the call site because neither resolve method is
        // @Transactional: the connection is borrowed inside the block, so it is
        // stamped SYSTEM. (In a @Transactional method it would be borrowed first
        // and the elevation would do nothing — see StudentUserAuthService.login.)
        var resolved = TenantContext.runAsSystem(() -> studentUserAuthService.resolveFromToken(token));
        if (resolved.isPresent()) {
            if (resolved.get().mustChangePassword() && !allowedBeforePasswordChange(request)) {
                refuseUntilPasswordChanged(request, response);
                return false;
            }
            request.setAttribute(ATTR_STUDENT_ID,      resolved.get().studentId());
            request.setAttribute(ATTR_STUDENT_USER_ID, resolved.get().studentUserId());
            // Pinned on the session at login (V036) — no lookup, no elevation.
            TenantContext.set(tenantScope.of(resolved.get().studyCountryInId()));
            return true;
        }

        addCorsHeaders(response, request.getHeader("Origin"));
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.getWriter().write("{\"error\":\"Unauthorized\",\"message\":\"Invalid or expired token\"}");
        return false;
    }

    /**
     * Clears the scope this interceptor set.
     *
     * <p>TenantContextFilter clears it again in its own finally block, which is
     * belt and braces rather than duplication: afterCompletion does not run when
     * preHandle returned false, and the filter's finally always does.
     */
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        TenantContext.clear();
    }

    /**
     * What an account that has never chosen its own password may still call (QA SEC-001,
     * D9-a): the change itself, the security status the password form shows, and the
     * student's own record, which the portal reads to know the session is alive. Seeded
     * accounts share one short password, and an administrator's reset is the
     * administrator's password; the staff side holds a temporary password the same way
     * ({@code MustChangePasswordInterceptor}).
     */
    static boolean allowedBeforePasswordChange(HttpServletRequest request) {
        String path = request.getRequestURI();
        while (path.endsWith("/")) path = path.substring(0, path.length() - 1);
        if (path.endsWith("/student-portal/me/change-password")) return true;
        boolean get = "GET".equalsIgnoreCase(request.getMethod());
        return get && (path.endsWith("/student-portal/me") || path.endsWith("/student-portal/me/security"));
    }

    private void refuseUntilPasswordChanged(HttpServletRequest request, HttpServletResponse response) throws java.io.IOException {
        addCorsHeaders(response, request.getHeader("Origin"));
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(JSON.writeValueAsString(java.util.Map.of(
                "code", PASSWORD_CHANGE_REQUIRED,
                "message", passwordChangeMessage(request))));
    }

    private static final com.fasterxml.jackson.databind.ObjectMapper JSON = new com.fasterxml.jackson.databind.ObjectMapper();

    @Autowired(required = false)
    private org.springframework.context.MessageSource messageSource;

    /**
     * The refusal in the student's language (round-5 QA R5-17: it stayed English under
     * Arabic). The portal sends Accept-Language with the language it shows. This runs
     * before any controller, so the language is read here, as GlobalExceptionHandler does.
     */
    String passwordChangeMessage(HttpServletRequest request) {
        String english = "Choose your own password before continuing.";
        if (messageSource == null) return english;
        String lang = request.getHeader("Accept-Language");
        java.util.Locale locale = lang != null && lang.toLowerCase().startsWith("ar")
                ? java.util.Locale.forLanguageTag("ar") : java.util.Locale.ENGLISH;
        try {
            return messageSource.getMessage("portal.password.changeRequired", null, english, locale);
        } catch (RuntimeException e) {
            return english;
        }
    }

    private void addCorsHeaders(HttpServletResponse response, String requestOrigin) {
        if (response.getHeader("Access-Control-Allow-Origin") != null) return;
        response.setHeader("Access-Control-Allow-Origin", resolveAllowedOrigin(allowedOrigins, requestOrigin));
        response.setHeader("Access-Control-Allow-Credentials", "true");
        response.setHeader("Access-Control-Allow-Headers", "Content-Type, X-Student-Portal-Token");
        response.setHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
    }

    /**
     * The origin to echo on a 401: the request's own if it is one of the
     * configured origins, otherwise the first configured one.
     *
     * <p>Exact match against the comma-separated list, the same split
     * {@code StudentPortalWebConfig.addCorsMappings} uses. Until the 27 Aug 2026
     * pass this was {@code allowedOrigins.contains(origin)} — a substring test —
     * so with {@code https://portal.mohe.gov.ye} configured, an Origin of
     * {@code https://portal.mohe.gov} or {@code mohe.gov.ye} was echoed back with
     * {@code Allow-Credentials: true} (plan item 1.13).
     */
    static String resolveAllowedOrigin(String allowedOrigins, String requestOrigin) {
        String[] configured = allowedOrigins.split(",");
        String fallback = configured[0].trim();
        if (requestOrigin == null || requestOrigin.isBlank()) {
            return fallback;
        }
        String wanted = requestOrigin.trim();
        for (String candidate : configured) {
            if (candidate.trim().equals(wanted)) {
                return wanted;
            }
        }
        return fallback;
    }
}
