package com.dilaw.mohe.architecture;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Security plan O-04 (6 Sep 2026): every request handler outside the documented
 * exceptions carries an authorization rule.
 *
 * <p>The API chain in {@code SecurityConfig} requires authentication for
 * {@code /api/**}, so an unannotated handler is not open to the world — but it
 * is open to every signed-in user, which for a write is the "VIEW-only role can
 * post" gap the 27 Aug 2026 pass closed by hand across 154 controllers. This
 * test keeps it closed: a new controller, or a new method on an old one, fails
 * the build unless it is gated or explicitly listed here with its reason.
 */
class ControllerAuthorizationArchTest {

    /** Controllers whose protection is not {@code @PreAuthorize}, with the reason. */
    private static final Set<String> EXEMPT_CLASSES = Set.of(
            // Public reference data, GET only; permitted in SecurityConfig.
            "com.dilaw.mohe.controler.EnumOptionsController",
            // Student portal: authenticated by StudentPortalTokenInterceptor (opaque
            // portal token), not by a UMS JWT, so @PreAuthorize does not apply.
            "com.dilaw.mohe.studentportal.StudentChangeRequestController",
            "com.dilaw.mohe.studentportal.StudentPortalAuthController",
            "com.dilaw.mohe.studentportal.StudentPortalMeController",
            "com.dilaw.mohe.studentportal.StudentUserAuthController",
            // Login, refresh, logout, captcha: the endpoints that establish a session.
            "com.dilaw.mohe.ums.controler.UmsAuthController"
    );

    /** Individual public handlers, each permitted by name in {@code SecurityConfig}. */
    private static final Set<String> EXEMPT_METHODS = Set.of(
            // Code lists: reference data (names, colours, icons); GET permitted for the portal.
            "CodeListController.list", "CodeListController.names", "CodeListController.all",
            // Branding for the login page; <img> cannot send an Authorization header.
            "SysSettingsController.getLogo", "SysSettingsController.getSettings",
            // The root banner, outside /api; the default chain permits "/" alone.
            "MoheApplication.home"
    );

    @Test
    @DisplayName("every handler is gated at class or method level, or is a listed exception")
    void everyHandlerIsGated() {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
        scanner.addIncludeFilter(new AnnotationTypeFilter(Controller.class));

        List<String> ungated = new ArrayList<>();
        int controllers = 0;
        for (var bd : scanner.findCandidateComponents("com.dilaw.mohe")) {
            Class<?> type;
            try {
                type = Class.forName(bd.getBeanClassName());
            } catch (ClassNotFoundException e) {
                continue;
            }
            controllers++;
            if (EXEMPT_CLASSES.contains(type.getName())) continue;
            boolean classGated = type.isAnnotationPresent(PreAuthorize.class);
            for (Method m : type.getDeclaredMethods()) {
                if (!isHandler(m)) continue;
                if (classGated || m.isAnnotationPresent(PreAuthorize.class)) continue;
                String name = type.getSimpleName() + "." + m.getName();
                if (EXEMPT_METHODS.contains(name)) continue;
                ungated.add(name);
            }
        }
        assertThat(controllers).as("the scan found the controllers").isGreaterThan(100);
        assertThat(ungated)
                .as("handlers with no @PreAuthorize on the method or its class (add the rule, or list the class above with a reason)")
                .isEmpty();
    }

    private static boolean isHandler(Method m) {
        return m.isAnnotationPresent(GetMapping.class) || m.isAnnotationPresent(PostMapping.class)
                || m.isAnnotationPresent(PutMapping.class) || m.isAnnotationPresent(DeleteMapping.class)
                || m.isAnnotationPresent(PatchMapping.class) || m.isAnnotationPresent(RequestMapping.class);
    }
}
