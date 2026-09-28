package com.dilaw.mohe.config;

import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

/**
 * Resolves the signed-in user id from Spring Security (JWT principal). Do not trust {@code X-User-Id}.
 */
public final class SecurityRequestUtils {

    /** SEC-10: request attribute {@code JwtAuthFilter} sets to the jti of the session the request rides on. */
    public static final String JTI_ATTRIBUTE = SecurityRequestUtils.class.getName() + ".jti";

    private SecurityRequestUtils() {
    }

    /**
     * The jti of the session the current request authenticated with, when there is
     * a request and it carried a valid token. Empty outside a request.
     */
    public static Optional<String> currentJti() {
        if (!(org.springframework.web.context.request.RequestContextHolder.getRequestAttributes()
                instanceof org.springframework.web.context.request.ServletRequestAttributes attrs)) {
            return Optional.empty();
        }
        Object jti = attrs.getRequest().getAttribute(JTI_ATTRIBUTE);
        return jti instanceof String s && !s.isBlank() ? Optional.of(s) : Optional.empty();
    }

    public static Optional<Long> currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null
                || !auth.isAuthenticated()
                || auth instanceof AnonymousAuthenticationToken
                || auth.getPrincipal() == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(Long.parseLong(auth.getName()));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    public static long currentUserIdOrDefault(long defaultValue) {
        return currentUserId().orElse(defaultValue);
    }

    public static long requireUserId() {
        return currentUserId().orElseThrow(
                () -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing or invalid authenticated user"));
    }

    /**
     * Whether the signed-in caller holds {@code authority}. For the rare rule that
     * depends on the request body and so cannot be a {@code @PreAuthorize}.
     */
    public static boolean hasAuthority(String authority) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> authority.equals(a.getAuthority()));
    }
}
