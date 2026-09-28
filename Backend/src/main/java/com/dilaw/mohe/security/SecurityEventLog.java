package com.dilaw.mohe.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * The security events an operator's SIEM should see (security plan O-34).
 *
 * <p>Every audited action is written to {@code UMSCORE.UserActions}; the subset
 * that means something to a monitoring desk is also written, at WARN, to the
 * logger {@value #LOGGER} with the event's fields in the MDC, so that the
 * structured (ECS JSON) log file carries them as top-level fields and an alert
 * rule can match on {@code security.event} without parsing a message. The set
 * is a list of action prefixes ({@code mohe.security-events.actions}); the
 * default covers failed logins and lockouts, refusals, every audited bypass or
 * elevation, privileged administration, sessions and MFA. Forwarding is the
 * operator's agent tailing the file; see {@code docs/SECURITY_LOGGING.md}.
 */
@Component
public class SecurityEventLog {

    public static final String LOGGER = "com.dilaw.mohe.security.events";

    /** MDC keys; dotted so ECS output nests them under {@code security}, {@code user}, ... */
    public static final String KEY_EVENT = "security.event";
    public static final String KEY_USER_ID = "user.id";
    public static final String KEY_USER_NAME = "user.name";
    public static final String KEY_RESOURCE_TYPE = "security.resource.type";
    public static final String KEY_RESOURCE_ID = "security.resource.id";
    public static final String KEY_CLIENT_IP = "client.ip";

    private static final Logger log = LoggerFactory.getLogger(LOGGER);

    private final List<String> prefixes;

    public SecurityEventLog(@Value("${mohe.security-events.actions:"
            + "LOGIN_FAILED,LOGIN_DENIED,LOGIN_OUTSIDE,ACCOUNT_,ACCESS_DENIED,APPROVAL_ROLE_BYPASS,APPROVAL_SELF_APPROVAL_BYPASS,"
            + "WAFAR_SUPERADMIN,WAFAR_WRITE_ELEVATED,WAFAR_CORRECT,SYSTEM_RESTORE,PRIVILEGE_CHANGE,"
            + "CREATE_USER,DELETE_USER,UNLOCK_USER,PASSWORD_RESET,ASSIGN_USER_ROLE,GRANT_USER_PERMISSION,"
            + "SET_ROLE_PERMISSIONS,SESSION_,MFA_,ROLE_DELEGATED,ACCESS_REVIEW,AUDIT_RETENTION,DATA_RETENTION,EXPORT_CAPPED,PORTAL_,DIRECT_GRANT}")
            List<String> prefixes) {
        this.prefixes = prefixes.stream().map(String::trim).filter(p -> !p.isEmpty()).toList();
    }

    public boolean isSecurityEvent(String action) {
        if (action == null) return false;
        for (String p : prefixes) {
            if (action.startsWith(p)) return true;
        }
        return false;
    }

    /** Writes the event if its action is in the set; a no-op otherwise. */
    public void emitIfSecurity(String action, Long userId, String userName, String resourceType,
                               Long resourceId, String detail, String ipAddress) {
        if (!isSecurityEvent(action)) return;
        emit(action, userId, userName, resourceType, resourceId, detail, ipAddress);
    }

    public void emit(String action, Long userId, String userName, String resourceType,
                     Long resourceId, String detail, String ipAddress) {
        try (MDC.MDCCloseable e = MDC.putCloseable(KEY_EVENT, action);
             MDC.MDCCloseable u = MDC.putCloseable(KEY_USER_ID, userId == null ? "" : String.valueOf(userId));
             MDC.MDCCloseable n = MDC.putCloseable(KEY_USER_NAME, userName == null ? "" : userName);
             MDC.MDCCloseable rt = MDC.putCloseable(KEY_RESOURCE_TYPE, resourceType == null ? "" : resourceType);
             MDC.MDCCloseable ri = MDC.putCloseable(KEY_RESOURCE_ID, resourceId == null ? "" : String.valueOf(resourceId));
             MDC.MDCCloseable ip = MDC.putCloseable(KEY_CLIENT_IP, ipAddress == null ? "" : ipAddress)) {
            log.warn("security event {} user={}{} resource={}{} ip={} detail={}",
                    action,
                    userId == null ? "-" : userId,
                    userName == null ? "" : " (" + userName + ")",
                    resourceType == null ? "-" : resourceType,
                    resourceId == null ? "" : "#" + resourceId,
                    ipAddress == null ? "-" : ipAddress,
                    detail == null ? "" : abbreviate(detail));
        }
    }

    private static String abbreviate(String s) {
        return s.length() <= 300 ? s : s.substring(0, 297) + "...";
    }
}
