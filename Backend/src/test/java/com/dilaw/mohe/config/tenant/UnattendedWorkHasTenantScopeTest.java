package com.dilaw.mohe.config.tenant;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.core.task.TaskDecorator;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Work that runs without a user must still declare a tenant scope.
 *
 * <p>{@link TenantContext} is fail-closed: a thread that never sets a scope gets
 * {@link TenantContext.Scope#DENY}, under which the V031 predicate matches no
 * row. For an HTTP request that is right — an unidentified caller should see
 * nothing. For a scheduled job or an {@code @Async} method it is a trap: the job
 * runs, finds nothing, and logs a clean result.
 *
 * <p>Three {@code @Scheduled} jobs and two {@code @Async} methods were in exactly
 * that state, so enabling the policies would have quietly stopped document
 * expiry warnings, alert detection and scheduled report delivery. These tests
 * fail if that arrangement comes back.
 */
@Tag("integration")
@SpringBootTest
class UnattendedWorkHasTenantScopeTest {

    @Autowired
    private ApplicationContext context;

    @Autowired
    private ScheduledTenantScopeAspect aspect;

    @Autowired
    private TaskDecorator taskDecorator;

    @AfterEach
    void resetScope() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("every @Scheduled method runs under a scope that can see data")
    void scheduledMethods_areElevated() throws Throwable {
        // The aspect advises the annotation, so proving it once proves it for
        // every job — including ones added after this test was written, which is
        // the point of advising the annotation rather than editing each job.
        AtomicReference<TenantContext.Scope> observed = new AtomicReference<>();

        TenantContext.set(TenantContext.Scope.DENY);
        aspect.runScheduledAsSystem(new StubJoinPoint(() -> observed.set(TenantContext.current())));

        assertThat(observed.get())
                .as("a scheduled job left at DENY sees no rows and reports success having done nothing")
                .isNotNull();
        assertThat(observed.get().all())
                .as("scheduled jobs must run with ministry-wide breadth to find their work")
                .isTrue();
    }

    @Test
    @DisplayName("the scope is restored after a scheduled job, not left elevated")
    void scheduledMethods_doNotLeakElevation() throws Throwable {
        TenantContext.Scope before = TenantContext.Scope.of(false, List.of(1004));
        TenantContext.set(before);

        aspect.runScheduledAsSystem(new StubJoinPoint(() -> { }));

        assertThat(TenantContext.current().all())
                .as("scheduler threads are pooled; a scope left elevated would widen the next job")
                .isFalse();
        assertThat(TenantContext.current().countryIds()).containsExactly(1004);
    }

    @Test
    @DisplayName("a scheduled job's exception reaches its own error handling unwrapped")
    void scheduledMethods_propagateTheOriginalException() {
        IllegalStateException original = new IllegalStateException("owner has no country scope");

        Throwable thrown = org.assertj.core.api.Assertions.catchThrowable(() ->
                aspect.runScheduledAsSystem(new StubJoinPoint(() -> {
                    throw original;
                })));

        assertThat(thrown)
                .as("jobs catch specific exceptions; a wrapper would slip past their catch blocks")
                .isSameAs(original);
    }

    @Test
    @DisplayName("@Async work inherits the scope of the thread that submitted it")
    void asyncWork_carriesTheSubmittersScope() throws Exception {
        TenantContext.Scope submitter = TenantContext.Scope.of(false, List.of(1004, 1005));
        TenantContext.set(submitter);

        AtomicReference<TenantContext.Scope> onWorker = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);

        Runnable decorated = taskDecorator.decorate(() -> {
            onWorker.set(TenantContext.current());
            done.countDown();
        });

        // Run on a genuinely different thread: a plain ThreadLocal does not cross
        // one, which is the whole reason the decorator exists.
        Thread worker = new Thread(decorated, "async-worker-under-test");
        worker.start();
        assertThat(done.await(5, TimeUnit.SECONDS)).isTrue();
        worker.join();

        assertThat(onWorker.get().countryIds())
                .as("audit and notification writes must run under the scope they were called about")
                .containsExactlyInAnyOrder(1004, 1005);
    }

    @Test
    @DisplayName("an async worker thread is left as it was found")
    void asyncWork_restoresTheWorkerThread() throws Exception {
        TenantContext.set(TenantContext.Scope.of(false, List.of(1004)));
        Runnable decorated = taskDecorator.decorate(() -> { });

        AtomicReference<TenantContext.Scope> after = new AtomicReference<>();
        Thread worker = new Thread(() -> {
            TenantContext.set(TenantContext.Scope.of(false, List.of(9999)));
            decorated.run();
            after.set(TenantContext.current());
        });
        worker.start();
        worker.join();

        assertThat(after.get().countryIds())
                .as("executor threads are pooled; one task must not change the next task's scope")
                .containsExactly(9999);
    }

    @Test
    @DisplayName("no @Scheduled method sits outside the aspect's reach")
    void everyScheduledMethod_isCoveredByTheAspect() {
        List<String> unadvised = new ArrayList<>();
        List<String> examined = new ArrayList<>();

        for (String name : context.getBeanDefinitionNames()) {
            Class<?> type = context.getType(name);
            if (type == null || !type.getName().startsWith("com.dilaw.mohe")) {
                continue;
            }
            Class<?> target = org.springframework.aop.support.AopUtils.getTargetClass(
                    context.getBean(name));
            for (Method m : ReflectionUtils.getAllDeclaredMethods(target)) {
                if (m.getAnnotation(Scheduled.class) == null) {
                    continue;
                }
                examined.add(target.getSimpleName() + "." + m.getName());
                // The aspect advises @Scheduled directly, so a job is covered as
                // long as Spring proxied its bean at all.
                if (!org.springframework.aop.support.AopUtils.isAopProxy(context.getBean(name))) {
                    unadvised.add(target.getSimpleName() + "." + m.getName());
                }
            }
        }

        // Without this the loop above passes by finding nothing, which is exactly
        // the state it is meant to detect. Three jobs exist today; the assertion
        // is a floor, not a count, so adding a fourth does not break it.
        assertThat(examined)
                .as("no @Scheduled methods were found at all — the scan is not looking where the jobs are")
                .hasSizeGreaterThanOrEqualTo(3);
        assertThat(unadvised)
                .as("a @Scheduled method on an unproxied bean silently runs at DENY")
                .isEmpty();
    }

    /** Minimal ProceedingJoinPoint: the aspect only calls proceed() and the signature. */
    private static final class StubJoinPoint implements org.aspectj.lang.ProceedingJoinPoint {
        private final Runnable body;

        private StubJoinPoint(Runnable body) {
            this.body = body;
        }

        @Override
        public Object proceed() {
            body.run();
            return null;
        }

        @Override
        public Object proceed(Object[] args) {
            return proceed();
        }

        @Override
        public org.aspectj.lang.Signature getSignature() {
            return new org.aspectj.lang.reflect.MethodSignature() {
                @Override public Class<?> getReturnType() { return void.class; }
                @Override public java.lang.reflect.Method getMethod() { return null; }
                @Override public Class<?>[] getParameterTypes() { return new Class<?>[0]; }
                @Override public String[] getParameterNames() { return new String[0]; }
                @Override public Class<?>[] getExceptionTypes() { return new Class<?>[0]; }
                @Override public String toShortString() { return "StubJob.run()"; }
                @Override public String toLongString() { return "StubJob.run()"; }
                @Override public String getName() { return "run"; }
                @Override public int getModifiers() { return 0; }
                @Override public Class<?> getDeclaringType() { return StubJoinPoint.class; }
                @Override public String getDeclaringTypeName() { return "StubJob"; }
            };
        }

        @Override public void set$AroundClosure(org.aspectj.runtime.internal.AroundClosure arc) { }
        @Override public String toShortString() { return "StubJob.run()"; }
        @Override public String toLongString() { return "StubJob.run()"; }
        @Override public Object getThis() { return this; }
        @Override public Object getTarget() { return this; }
        @Override public Object[] getArgs() { return new Object[0]; }
        @Override public String getKind() { return "method-execution"; }
        @Override public org.aspectj.lang.JoinPoint.StaticPart getStaticPart() { return null; }
        @Override public org.aspectj.lang.reflect.SourceLocation getSourceLocation() { return null; }
    }
}
