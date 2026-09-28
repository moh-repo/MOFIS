package com.dilaw.mohe.config;

import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * SpEL helper for method security. Used by lookup-table read endpoints that must be
 * reachable without a UMS JWT (student portal, login page) while writes stay admin-only.
 */
@Component("httpMethodSecurity")
public class HttpMethodSecurity {

    public boolean isGetRequest() {
        var attrs = RequestContextHolder.getRequestAttributes();
        if (attrs instanceof ServletRequestAttributes servletAttrs) {
            return "GET".equalsIgnoreCase(servletAttrs.getRequest().getMethod());
        }
        return false;
    }
}
