package com.dilaw.mohe.config.tenant;

import org.springframework.core.task.TaskDecorator;
import org.springframework.stereotype.Component;

/**
 * Carries the submitting thread's country scope into {@code @Async} work.
 *
 * <p>{@link TenantContext} holds its scope in a plain {@link ThreadLocal}, which
 * a pooled executor thread does not inherit. Without this decorator every
 * {@code @Async} method would run under {@link TenantContext.Scope#DENY} — so
 * once the V031 policies are enabled, audit writes and notification sends would
 * see none of the rows they were called about, and would log success having done
 * nothing.
 *
 * <p>An {@code InheritableThreadLocal} would not fix this. Pool threads are
 * created once, on first use, and then reused: they would inherit whichever
 * scope happened to be active when the pool grew, and keep it for every task
 * afterwards. The scope has to be captured at submit time and applied at run
 * time, which is what a decorator is for.
 *
 * <p>Spring Boot applies a single {@code TaskDecorator} bean to the
 * auto-configured {@code applicationTaskExecutor}, so declaring it is enough —
 * there is no executor to define here.
 */
@Component
public class TenantContextTaskDecorator implements TaskDecorator {

    @Override
    public Runnable decorate(Runnable task) {
        // Captured on the submitting thread, at submit time.
        TenantContext.Scope captured = TenantContext.current();

        return () -> {
            // The worker thread is pooled and may carry a scope from an earlier
            // task, so restore rather than clear: whatever it had is put back.
            TenantContext.Scope previous = TenantContext.current();
            TenantContext.set(captured);
            try {
                task.run();
            } finally {
                TenantContext.set(previous);
            }
        };
    }
}
