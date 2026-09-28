package com.dilaw.mohe.service;

import com.dilaw.mohe.model.MoheQuarter;
import com.dilaw.mohe.ums.service.CountryScopeService;
import com.dilaw.mohe.ums.service.CountryScopeService.CountryScope;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.i18n.LocaleContext;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.core.task.TaskExecutor;
import org.springframework.http.HttpStatus;
import org.springframework.security.concurrent.DelegatingSecurityContextRunnable;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Runs quarter creation in the background and reports its progress (review item Q4).
 *
 * <p>Creating a quarter archives every in-scope student with a full-profile snapshot,
 * and the request that waited for it outlived the proxy's timeout, so the operator saw
 * an error for a quarter that was in fact being created. Now the request validates,
 * starts the job and answers at once; the screen polls {@link #status} for progress and
 * the summary.
 *
 * <p>The work itself is unchanged and still one transaction
 * ({@link MoheQuarterCreateService#createWithSnapshot(MoheQuarter, CountryScope,
 * MoheQuarterCreateService.Progress)}): a failure part-way archives and advances
 * nothing. One creation runs at a time, since each must follow the last quarter on
 * record.
 *
 * <p>The job runs on the application task executor, whose decorator carries the
 * caller's tenant scope. The caller's security context and locale are carried here: the
 * profile snapshot masks fields by the caller's permissions and names lookups in the
 * caller's language, as it did when it ran on the request thread.
 *
 * <p>Jobs live in memory. A restart loses the record of a finished job (the quarter and
 * archives are in the database either way) and rolls back one still running.
 */
@Service
public class MoheQuarterCreateJobs {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(MoheQuarterCreateJobs.class);

    /** Finished jobs are kept this long for the screen to collect the result. */
    private static final Duration KEEP_FINISHED = Duration.ofHours(1);

    public enum State { RUNNING, DONE, FAILED }

    /** One creation, updated from the job thread and read from request threads. */
    public static final class Job implements MoheQuarterCreateService.Progress {
        private final String id;
        private final Long startedBy;
        private final Instant startedAt = Instant.now();
        private volatile State state = State.RUNNING;
        private volatile long total = -1;
        private volatile int done;
        private volatile Map<String, Object> summary;
        private volatile String error;
        private volatile Instant finishedAt;

        Job(String id, Long startedBy) {
            this.id = id;
            this.startedBy = startedBy;
        }

        @Override
        public void started(long totalStudents) {
            this.total = totalStudents;
        }

        @Override
        public void advanced(int done) {
            this.done = done;
        }

        void succeed(Map<String, Object> summary) {
            this.summary = summary;
            this.finishedAt = Instant.now();
            this.state = State.DONE;
        }

        void fail(String error) {
            this.error = error;
            this.finishedAt = Instant.now();
            this.state = State.FAILED;
        }

        public Map<String, Object> view() {
            Map<String, Object> v = new LinkedHashMap<>();
            v.put("jobId", id);
            v.put("state", state.name());
            v.put("total", total < 0 ? null : total);
            v.put("done", done);
            v.put("startedBy", startedBy);
            v.put("startedAt", startedAt);
            v.put("finishedAt", finishedAt);
            v.put("summary", summary);
            v.put("error", error);
            return v;
        }
    }

    private final Map<String, Job> jobs = new ConcurrentHashMap<>();
    /** Id of the job currently running, or null. */
    private final AtomicReference<String> running = new AtomicReference<>();

    @Autowired private MoheQuarterCreateService createService;
    @Autowired private CountryScopeService countryScopeService;
    @Autowired @Qualifier("applicationTaskExecutor") private TaskExecutor executor;

    /**
     * Validates on the caller's thread — a bad period or a country out of scope is
     * refused here, as before — then starts the creation and returns the job's view.
     *
     * @throws ResponseStatusException 409 while another creation is running
     */
    public Map<String, Object> start(MoheQuarter entity, Long userId) {
        CountryScope scope = countryScopeService.getScope();
        createService.validate(entity, scope);

        pruneFinished();
        Job job = new Job(UUID.randomUUID().toString(), userId);
        if (!running.compareAndSet(null, job.id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    text("quarter.create.running", "A quarter is already being created. Wait for it to finish, then try again."));
        }
        jobs.put(job.id, job);

        SecurityContext security = SecurityContextHolder.getContext();
        LocaleContext locale = LocaleContextHolder.getLocaleContext();
        Runnable work = () -> {
            LocaleContextHolder.setLocaleContext(locale);
            try {
                job.succeed(createService.createWithSnapshot(entity, scope, job));
                log.info("Quarter creation {} finished: {}", job.id, job.summary);
            } catch (ResponseStatusException e) {
                job.fail(e.getReason());
            } catch (Exception e) {
                log.error("Quarter creation {} failed; nothing was archived or advanced", job.id, e);
                // The reader's language, and not the exception's text: that is a
                // server detail (W3-08, review Q4). The log above keeps it.
                job.fail(text("quarter.create.failed", "Creating the quarter failed and nothing was changed."));
            } finally {
                running.compareAndSet(job.id, null);
                LocaleContextHolder.resetLocaleContext();
            }
        };
        try {
            executor.execute(new DelegatingSecurityContextRunnable(work, security));
        } catch (RejectedExecutionException e) {
            running.compareAndSet(job.id, null);
            jobs.remove(job.id);
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    text("quarter.create.busy", "The server is busy; try creating the quarter again in a moment."));
        }
        return job.view();
    }

    @Autowired(required = false) private org.springframework.context.MessageSource messages;

    /** A message in the locale of the thread — the job copies the requester's into its own. */
    private String text(String key, String english) {
        return messages == null ? english
                : messages.getMessage(key, null, english, LocaleContextHolder.getLocale());
    }

    /** The job's current view. */
    public Map<String, Object> status(String jobId) {
        Job job = jobs.get(jobId);
        if (job == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No such quarter-creation job");
        }
        return job.view();
    }

    /** The running job's view, so a screen opened mid-creation can show it; null when idle. */
    public Map<String, Object> current() {
        String id = running.get();
        Job job = id == null ? null : jobs.get(id);
        return job == null ? null : job.view();
    }

    private void pruneFinished() {
        Instant cutoff = Instant.now().minus(KEEP_FINISHED);
        jobs.values().removeIf(j -> j.finishedAt != null && j.finishedAt.isBefore(cutoff));
    }
}
