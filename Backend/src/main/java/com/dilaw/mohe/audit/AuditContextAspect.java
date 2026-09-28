package com.dilaw.mohe.audit;

import com.dilaw.mohe.config.SecurityRequestUtils;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.hibernate.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.orm.jpa.EntityManagerFactoryUtils;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * Sets CONTEXT_INFO on the JDBC connection used by the current JPA transaction so database
 * triggers (AUDITLOG.AuditTrail) can populate CHANGED_BY, IP_ADDRESS, SESSION_ID.
 * Format: "userId|ip|session" (max 64 chars total). Uses the EntityManager's connection so
 * the same connection that runs the DML (and trigger) sees CONTEXT_INFO.
 */
@Aspect
@Component
public class AuditContextAspect {

    private static final Logger log = LoggerFactory.getLogger(AuditContextAspect.class);

    private static final String SEP = "|";
    private static final int MAX_USER = 38;
    private static final int MAX_IP = 15;
    private static final int MAX_SESSION = 9;  // 38+15+9 + 2 separators = 64
    private static final String SET_CONTEXT_INFO =
            "DECLARE @ctx VARBINARY(128) = CAST(? AS VARBINARY(128)); SET CONTEXT_INFO @ctx";

    private final DataSource dataSource;
    private final EntityManagerFactory entityManagerFactory;

    public AuditContextAspect(
            @Qualifier("dataSource") DataSource dataSource,
            @Qualifier("entityManagerFactory") EntityManagerFactory entityManagerFactory) {
        this.dataSource = dataSource;
        this.entityManagerFactory = entityManagerFactory;
    }

    @Around("execution(* com.dilaw.mohe.repository.*.save(..)) || " +
            "execution(* com.dilaw.mohe.repository.*.delete(..)) || " +
            "execution(* com.dilaw.mohe.repository.*.deleteById(..)) || " +
            "execution(* com.dilaw.mohe.ums.repository.*.save(..)) || " +
            "execution(* com.dilaw.mohe.ums.repository.*.delete(..)) || " +
            "execution(* com.dilaw.mohe.ums.repository.*.deleteById(..)) || " +
            "execution(* com.dilaw.mohe.approval.repository.*.save(..)) || " +
            "execution(* com.dilaw.mohe.approval.repository.*.delete(..)) || " +
            "execution(* com.dilaw.mohe.approval.repository.*.deleteById(..))")
    public Object setAuditContext(ProceedingJoinPoint joinPoint) throws Throwable {
        setContextInfo();
        return joinPoint.proceed();
    }

    private void setContextInfo() {
        String userId = getCurrentUserId();
        String ip = getClientIp();
        String session = getSessionId();
        String packed = packContext(userId, ip, session);
        if (packed == null || packed.isEmpty()) {
            return;
        }
        Connection conn = getTransactionConnection();
        if (conn == null) {
            return;
        }
        // SET CONTEXT_INFO takes a constant or a variable, so the value is bound
        // into a variable first; CONTEXT_INFO is session state and outlives the
        // batch, so the trigger sees it (O-11: no SQL built from request data).
        try (PreparedStatement st = conn.prepareStatement(SET_CONTEXT_INFO)) {
            st.setNString(1, packed);
            st.execute();
        } catch (SQLException ignored) {
            // Don't fail the request
        } finally {
            // A no-op for the transaction-bound connection; returns a fallback one to the pool.
            DataSourceUtils.releaseConnection(conn, dataSource);
        }
    }

    /**
     * The connection of the current transaction — the one that will run the DML and so
     * the trigger — or null when there is no transaction to stamp.
     *
     * <p>Prefers the JPA transaction's connection. Outside any transaction this returns
     * null rather than borrowing a connection: the repository call that follows opens its
     * own transaction on a <em>different</em> pooled connection, so stamping a borrowed
     * one sets CONTEXT_INFO where no trigger will read it. That fallback borrowed via
     * {@code DataSourceUtils.getConnection} and never released it, so every save or delete
     * issued from a request thread outside a transaction leaked one pooled connection —
     * ten such calls exhausted the pool.
     */
    private Connection getTransactionConnection() {
        EntityManager em = EntityManagerFactoryUtils.getTransactionalEntityManager(entityManagerFactory);
        if (em != null) {
            try {
                Session session = em.unwrap(Session.class);
                return session.doReturningWork(connection -> connection);
            } catch (Exception ignored) {
                // Fall through to DataSource fallback
            }
        }
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            return null;
        }
        try {
            // Bound to the active (non-JPA) transaction; released by it, not by us.
            return DataSourceUtils.getConnection(dataSource);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String packContext(String userId, String ip, String session) {
        String u = truncate(userId != null ? userId : "", MAX_USER);
        String i = truncate(ip != null ? ip : "", MAX_IP);
        String s = truncate(session != null ? session : "", MAX_SESSION);
        if (u.isEmpty() && i.isEmpty() && s.isEmpty()) {
            return null;
        }
        return u + SEP + i + SEP + s;
    }

    private static String truncate(String s, int max) {
        if (s == null || s.length() <= max) {
            return s != null ? s : "";
        }
        return s.substring(0, max);
    }

    private static final String ATTR_AUDIT_USER_ID = "AUDIT_USER_ID";
    private static final String ATTR_AUDIT_IP = "AUDIT_IP";

    private static String getCurrentUserId() {
        var jwtUser = SecurityRequestUtils.currentUserId();
        if (jwtUser.isPresent()) {
            return jwtUser.get().toString();
        }
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null && attrs.getRequest() != null) {
                var req = attrs.getRequest();
                Object attr = req.getAttribute(ATTR_AUDIT_USER_ID);
                if (attr != null) return attr.toString();
                if (req.getUserPrincipal() != null) return req.getUserPrincipal().getName();
            }
        } catch (Exception e) {
            // Audit context stays best-effort, but a failure should not vanish.
            log.debug("Audit context resolution failed: {}", e.toString());
        }
        return null;
    }

    private static String getClientIp() {
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null && attrs.getRequest() != null) {
                var req = attrs.getRequest();
                Object attr = req.getAttribute(ATTR_AUDIT_IP);
                if (attr != null && !attr.toString().isEmpty()) return attr.toString();
                String x = req.getHeader("X-Forwarded-For");
                if (x != null && !x.isEmpty()) return x.split(",")[0].trim();
                return req.getRemoteAddr();
            }
        } catch (Exception e) {
            // Audit context stays best-effort, but a failure should not vanish.
            log.debug("Audit context resolution failed: {}", e.toString());
        }
        return null;
    }

    private static String getSessionId() {
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null && attrs.getRequest() != null && attrs.getRequest().getSession(false) != null) {
                return attrs.getRequest().getSession(false).getId();
            }
        } catch (Exception e) {
            // Audit context stays best-effort, but a failure should not vanish.
            log.debug("Audit context resolution failed: {}", e.toString());
        }
        return null;
    }
}
