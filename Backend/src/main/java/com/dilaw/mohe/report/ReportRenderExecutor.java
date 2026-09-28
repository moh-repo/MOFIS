package com.dilaw.mohe.report;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Where report rendering actually runs, and the two limits on it.
 *
 * <p>Filling a Jasper report is the most expensive thing this application does:
 * every row of the report is held in memory as a filled page element until the
 * export is written. Until this class existed, that work ran on the servlet
 * container's own request threads with no limit of any kind — as many concurrent
 * renders as there were requests, each for as long as it took, with nothing to
 * stop a single large report from running until the heap gave out.
 *
 * <p>That is the mechanism behind the ministry's note G49: "the report engine
 * fails under load, and if one report fails every report after it fails too".
 * The cascade is what an unbounded renderer does under memory pressure. One
 * oversized fill drives the heap to its limit; every other request in flight
 * then fails as well, because they are all allocating from the same exhausted
 * heap; and the container's threads stay occupied by renders that will never
 * finish, so requests that arrive afterwards queue behind them and time out at
 * the browser. Nothing is corrupted, and nothing needs restarting — which is
 * why it looked so much like the engine itself refusing to work.
 *
 * <p>Two limits replace that. <b>Concurrency</b>: at most
 * {@code mohe.report.render.threads} renders run at once, so peak memory is
 * bounded by that many reports rather than by how many people pressed Preview.
 * Requests over the limit wait in a bounded queue and are refused with 503 once
 * even the queue is full — a clear "try again in a moment" instead of a slow
 * collapse. <b>Time</b>: a render that exceeds
 * {@code mohe.report.render.timeout-seconds} is cancelled and answered with 504.
 * Cancelling frees the worker for the next report, which is the property that
 * actually stops the cascade: one pathological report can no longer take the
 * engine down with it.
 *
 * <p>The work handed here must be self-contained — data already materialised,
 * no database access, no reliance on request-scoped state. It runs on a pool
 * thread, so the security context, the tenant scope and
 * {@code RequestContextHolder} are not available on it. {@link ExportGuard}'s
 * row cap and export line are read on the request thread before and after the
 * render for exactly this reason.
 */
@Component
public class ReportRenderExecutor {

    private static final Logger log = LoggerFactory.getLogger(ReportRenderExecutor.class);

    private final ThreadPoolExecutor pool;
    private final long timeoutSeconds;

    public ReportRenderExecutor(
            @Value("${mohe.report.render.threads:0}") int threads,
            @Value("${mohe.report.render.queue:32}") int queueCapacity,
            @Value("${mohe.report.render.timeout-seconds:90}") long timeoutSeconds) {

        // 0 means "size it from the machine". Two per core is wrong for this work:
        // a fill is CPU-bound with a large live set, so more threads buy no
        // throughput and multiply peak memory. Capped at 4 because the ceiling
        // that matters is heap, not cores.
        int size = threads > 0 ? threads : Math.max(2, Math.min(4, Runtime.getRuntime().availableProcessors()));
        this.timeoutSeconds = timeoutSeconds;

        AtomicInteger seq = new AtomicInteger();
        this.pool = new ThreadPoolExecutor(
                size, size, 60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(Math.max(1, queueCapacity)),
                runnable -> {
                    Thread t = new Thread(runnable, "jasper-render-" + seq.incrementAndGet());
                    // Daemon: a hung render must never keep the JVM from shutting down.
                    t.setDaemon(true);
                    return t;
                });
        this.pool.allowCoreThreadTimeOut(true);

        log.info("Report renderer bounded to {} threads, queue {}, timeout {}s", size, queueCapacity, timeoutSeconds);
    }

    /**
     * Runs one render under both limits.
     *
     * @param templateName only for the log line and the error message — a reader
     *                     who hits the timeout should be told which report it was
     * @param work         the fill and export, which must not touch the database
     *                     or any request-scoped state
     */
    public <T> T render(String templateName, Callable<T> work) {
        Future<T> future;
        try {
            future = pool.submit(work);
        } catch (RejectedExecutionException e) {
            // Queue full: every worker is busy and the backlog is already at its
            // limit. Refusing now is the honest answer; accepting would only add
            // to a queue nobody is draining fast enough.
            log.warn("Report renderer saturated, refused {}", templateName);
            throw new ReportException(ReportException.Code.REPORT_RENDER_BUSY,
                    "The report service is busy. Please try again in a moment.");
        }

        try {
            return future.get(timeoutSeconds, TimeUnit.SECONDS);

        } catch (TimeoutException e) {
            // Interrupt the worker: JasperReports checks the interrupt flag between
            // bands, so a fill usually stops within one band of here. Even where it
            // does not, the request thread is released and the pool slot returns as
            // soon as the fill notices — which is what keeps one bad report from
            // being the last report the engine serves.
            future.cancel(true);
            log.warn("Report {} exceeded {}s and was cancelled", templateName, timeoutSeconds);
            throw new ReportException(ReportException.Code.REPORT_RENDER_TIMEOUT,
                    "The report '" + templateName + "' took too long to build. Narrow the filters and try again.",
                    templateName);

        } catch (InterruptedException e) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            throw new ReportException(ReportException.Code.REPORT_RENDER_BUSY,
                    "The report request was interrupted.");

        } catch (ExecutionException e) {
            // The render's own failure, not the executor's. Unwrap so callers and
            // the exception handler see what actually went wrong rather than a
            // wrapper — an out-of-memory in particular must not be swallowed.
            Throwable cause = e.getCause();
            if (cause instanceof Error error) {
                throw error;
            }
            if (cause instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw new IllegalStateException("Failed to render report: " + templateName, cause);
        }
    }

    @PreDestroy
    void shutdown() {
        pool.shutdownNow();
    }
}
