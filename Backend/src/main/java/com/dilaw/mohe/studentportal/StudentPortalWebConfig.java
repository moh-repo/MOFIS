package com.dilaw.mohe.studentportal;

import com.dilaw.mohe.config.WebSecurityConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class StudentPortalWebConfig implements WebMvcConfigurer {

    @Value("${cors.allowed-origins:http://localhost:4200}")
    private String allowedOrigins;

    @Autowired
    private StudentPortalTokenInterceptor studentPortalTokenInterceptor;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        String[] origins = allowedOrigins.split(",\\s*");
        // This more-specific mapping wins over WebSecurityConfig's /** mapping for
        // portal paths, so it must apply the same header allowlist. It previously
        // used allowedHeaders("*") + exposedHeaders("*") with credentials — the
        // exact wildcard-reflection problem WebSecurityConfig removed.
        registry.addMapping("/api/v1/student-portal/**")
                .allowedOrigins(origins)
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders(WebSecurityConfig.ALLOWED_HEADERS.toArray(String[]::new))
                // X-Captcha-Id carries the sign-in challenge id. The controller sets an
                // Access-Control-Expose-Headers of its own, but this mapping is the more
                // specific one and Spring writes that header itself, so without the name
                // here the browser is handed the image and cannot read the id that goes
                // with it — every sign-in then fails on a missing security code.
                .exposedHeaders("X-Student-Portal-Token", StudentUserAuthController.CAPTCHA_ID_HEADER)
                .allowCredentials(true)
                .maxAge(3600);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(studentPortalTokenInterceptor)
                .addPathPatterns(
                        "/api/v1/student-portal/me",
                        "/api/v1/student-portal/me/**",
                        "/api/v1/student-portal/change-requests",
                        "/api/v1/student-portal/change-requests/**"
                )
                .excludePathPatterns("/api/v1/student-portal/auth/**");
    }
}
