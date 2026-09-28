package com.dilaw.mohe.config;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * The one place that decides what "the client's address" means.
 *
 * <p>Two things key on it: the login throttle (one bucket per address) and the
 * audit row written on every sign-in. Both used to read {@code X-Forwarded-For}
 * unconditionally, which is only right when a trusted proxy sets that header —
 * otherwise the client sets it, picks its own throttle bucket, and signs the
 * audit trail with whatever address it likes.
 *
 * <p>So the header is honoured only when {@code mohe.security.trust-forwarded-for}
 * says the process sits behind a proxy that overwrites it. Off by default: a
 * process reached directly must never believe the header. Behind nginx or a load
 * balancer, set it to {@code true} (see ENVIRONMENT.md). When it is on, the
 * first address in the list is the client — every hop appends its own, so the
 * first is the one the proxy saw connecting.
 */
@Component
public class ClientIpResolver {

    private final boolean trustForwardedFor;

    public ClientIpResolver(@Value("${mohe.security.trust-forwarded-for:false}") boolean trustForwardedFor) {
        this.trustForwardedFor = trustForwardedFor;
    }

    public String resolve(HttpServletRequest request) {
        if (trustForwardedFor) {
            String forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                String first = forwarded.split(",")[0].trim();
                if (!first.isEmpty()) {
                    return first;
                }
            }
        }
        return request.getRemoteAddr();
    }

    public boolean isTrustForwardedFor() {
        return trustForwardedFor;
    }
}
