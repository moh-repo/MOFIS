package com.dilaw.mohe.config;

import org.springframework.boot.autoconfigure.security.SecurityProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

import java.util.ArrayList;
import java.util.List;

@Configuration
public class RateLimitFilterConfig {

    /**
     * Login throttling, per IP, before anything else runs. Covers both sign-in surfaces:
     * the UMS login and the student-portal login / OTP / password-reset endpoints, which
     * are equally anonymous and were unthrottled until August 2026.
     */
    @Bean
    public FilterRegistrationBean<LoginRateLimitFilter> loginRateLimitFilterRegistration(LoginRateLimitFilter filter) {
        FilterRegistrationBean<LoginRateLimitFilter> reg = new FilterRegistrationBean<>(filter);
        reg.setOrder(Ordered.HIGHEST_PRECEDENCE);
        reg.addUrlPatterns(
                "/api/v1/ums/auth/login",
                "/api/v1/ums/auth/captcha",
                "/api/v1/student-portal/auth/*"
        );
        return reg;
    }

    /**
     * Rate-limit expensive endpoints: reports, exports, imports.
     *
     * <p>Ordered just after the Spring Security filter chain so the request already carries
     * the JWT principal and the bucket can be keyed on the real user id. Registered ahead of
     * security it could only key on what the client sent.
     *
     * <p>The URL patterns come from the filter's own configured prefixes
     * ({@code mohe.security.rate-limit.report-prefixes} / {@code import-prefixes}), so
     * the container hands the filter exactly the requests it matches on. The patterns
     * used to be a second, hand-kept list here — and both lists named endpoints that
     * do not exist (revision item 1.10).
     */
    @Bean
    public FilterRegistrationBean<ApiRateLimitFilter> apiRateLimitFilterRegistration(ApiRateLimitFilter filter) {
        FilterRegistrationBean<ApiRateLimitFilter> reg = new FilterRegistrationBean<>(filter);
        reg.setOrder(SecurityProperties.DEFAULT_FILTER_ORDER + 1);
        List<String> patterns = new ArrayList<>(filter.urlPatterns());
        // The ad-blocker alias PdfPathRewriteFilter serves: the container matches these
        // patterns on the original URL, so the alias must be listed too. By the time this
        // filter runs the rewrite has happened and the filter sees the real path.
        patterns.add("/api/pdf/*");
        reg.addUrlPatterns(patterns.toArray(String[]::new));
        return reg;
    }
}
