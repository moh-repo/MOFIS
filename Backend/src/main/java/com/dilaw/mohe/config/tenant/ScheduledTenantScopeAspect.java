package com.dilaw.mohe.config.tenant;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Runs every {@code @Scheduled} method under the system scope.
 *
 * <p>A scheduled job has no HTTP request, so {@link TenantContextFilter} never
 * runs for it and {@link TenantContext} stays at its default of
 * {@link TenantContext.Scope#DENY}. With the V031 policies enabled that makes
 * each job a silent no-op: {@code DocumentExpiryScheduler} would find no
 * expiring documents, {@code AlertEventScheduler} would detect no cases, and
 * both would log a clean run. Nothing would look broken.
 *
 * <p>{@link TenantContext} always intended this — its own documentation names
 * scheduled jobs as callers of {@code runAsSystem} — but nothing was calling it.
 * Doing it here rather than in each job means a job added later is covered
 * without its author having to know the rule, which is the same argument that
 * put the tenant predicate in the database instead of in each query.
 *
 * <p><b>System scope is the entry point, not the whole job.</b> A job that acts
 * on behalf of one user must narrow to that user's countries itself — see
 * {@code ScheduledReportExecutionService.executeReport}, which resolves the
 * schedule owner's scope and runs the report inside it. Elevation here only
 * grants the breadth needed to find the work; it does not authorise the work.
 *
 * <p>Ordered ahead of Spring's transaction advice so the scope is in place
 * before a transaction borrows its connection — {@link TenantContextDataSource}
 * stamps the scope at borrow time, and a connection taken before this ran would
 * carry DENY for the whole transaction.
 */
@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ScheduledTenantScopeAspect {

    private static final Logger log = LoggerFactory.getLogger(ScheduledTenantScopeAspect.class);

    @Around("@annotation(org.springframework.scheduling.annotation.Scheduled)")
    public Object runScheduledAsSystem(ProceedingJoinPoint joinPoint) throws Throwable {
        if (log.isDebugEnabled()) {
            log.debug("Elevating scheduled job {} to system tenant scope",
                    joinPoint.getSignature().toShortString());
        }
        try {
            // tenant-bypass: a @Scheduled job has no request and therefore no
            // caller; it works across every attaché by design (alerts, cleanup).
            return TenantContext.runAsSystem(() -> {
                try {
                    return joinPoint.proceed();
                } catch (Throwable t) {
                    // Supplier cannot throw checked exceptions; unwrapped below so
                    // the job's own error handling sees the original exception.
                    throw new ProceedFailure(t);
                }
            });
        } catch (ProceedFailure wrapped) {
            throw wrapped.getCause();
        }
    }

    /** Carries a checked exception out of the {@code Supplier} passed to {@code runAsSystem}. */
    private static final class ProceedFailure extends RuntimeException {
        private ProceedFailure(Throwable cause) {
            super(cause);
        }
    }
}
