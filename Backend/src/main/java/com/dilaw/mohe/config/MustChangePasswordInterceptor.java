package com.dilaw.mohe.config;

import com.dilaw.mohe.ums.service.MustChangePasswordGate;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.charset.StandardCharsets;

/**
 * Holds an account with a temporary password to the password change (V121, DEC-04).
 *
 * <p>While {@code UMSCORE.Users.MustChangePassword} is set, every API request is
 * refused with 403 {@code PASSWORD_CHANGE_REQUIRED} except the ones the change
 * itself needs: sign-in, {@code /auth/me}, the password change, sign-out, token
 * refresh and MFA (all under {@code /ums/auth}), the account's own record and
 * permissions, and the settings and code lists the application shell reads before
 * it can show anything. The frontend sends the user to the profile page on that
 * code; this is the server's half, so the rule does not depend on the browser.
 */
@Component
public class MustChangePasswordInterceptor implements HandlerInterceptor, WebMvcConfigurer {

    public static final String CODE = "PASSWORD_CHANGE_REQUIRED";

    static final String[] ALLOWED = {
            "/api/v1/ums/auth/**",
            "/api/v1/ums/users/me",
            "/api/v1/ums/effective-permissions/me",
            "/api/v1/sys-settings",
            "/api/v1/sys-settings/**",
            "/api/v1/code-lists",
            "/api/v1/code-lists/**",
            "/api/v1/student-portal/**"
    };

    private final MustChangePasswordGate gate;

    public MustChangePasswordInterceptor(MustChangePasswordGate gate) {
        this.gate = gate;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(this).addPathPatterns("/api/**").excludePathPatterns(ALLOWED);
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        Long userId = SecurityRequestUtils.currentUserId().orElse(null);
        if (userId == null || !gate.mustChange(userId)) {
            return true;
        }
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("{\"code\":\"" + CODE + "\",\"message\":"
                + "\"The password was set by an administrator. Change it before continuing.\"}");
        return false;
    }
}
