package com.dilaw.mohe.util;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Provides the currently authenticated username for audit fields (createdBy, modifiedBy).
 * Falls back to "system" only when there is genuinely no security context
 * (e.g., background jobs, startup hooks).
 */
public final class SecurityContextHelper {

    private SecurityContextHelper() {}

    public static String currentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())) {
            return auth.getName();
        }
        return "system";
    }
}
