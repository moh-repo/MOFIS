package com.dilaw.mohe.config;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

@Aspect
@Component
public class SessionContextAspect {

    private static final Logger log = LoggerFactory.getLogger(SessionContextAspect.class);

    @PersistenceContext
    private EntityManager entityManager;

    @Before("@annotation(org.springframework.transaction.annotation.Transactional)")
    public void setSessionContext() {
        String userId = resolveUserIdForSessionContext();

        // Set SESSION_CONTEXT for SQL Server
        // Note: This requires the connection to be open. EntityManager might not have
        // one yet.
        // However, inside a Transactional method, we can force it.
        // But native query might be safer.

        try {
            entityManager.createNativeQuery("EXEC sp_set_session_context 'UserId', :userId")
                    .setParameter("userId", userId)
                    .executeUpdate();
        } catch (Exception e) {
            log.warn("Failed to set session context: {}", e.getMessage());
        }
    }

    /**
     * Authoritative user id for SQL Server session context comes only from Spring Security
     * (JWT principal). Never trust {@code X-User-Id} — it is client-controlled.
     */
    private static String resolveUserIdForSessionContext() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null
                && auth.isAuthenticated()
                && !(auth instanceof AnonymousAuthenticationToken)
                && auth.getPrincipal() != null) {
            String name = auth.getName();
            if (name != null && !name.isEmpty()) {
                return name;
            }
        }
        return "System";
    }
}
