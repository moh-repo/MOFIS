package com.dilaw.mohe.config;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * Registers {@link PdfPathRewriteFilter} ahead of everything else — in particular ahead of
 * the Spring Security chain, which must see the rewritten path.
 */
@Configuration
public class PdfPathRewriteFilterConfig {

    @Bean
    public FilterRegistrationBean<PdfPathRewriteFilter> pdfPathRewriteFilterRegistration() {
        FilterRegistrationBean<PdfPathRewriteFilter> reg = new FilterRegistrationBean<>(new PdfPathRewriteFilter());
        reg.setOrder(Ordered.HIGHEST_PRECEDENCE);
        reg.addUrlPatterns("/api/pdf/*");
        reg.setName("pdfPathRewriteFilter");
        return reg;
    }
}
