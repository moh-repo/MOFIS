package com.dilaw.mohe.config.tenant;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * The country scope the current thread's database work runs under.
 *
 * <p>Row-Level Security (V031) filters every tenanted table against
 * {@code SESSION_CONTEXT}. Something has to put the right value there for each
 * unit of work, and it cannot be the database: only the application knows who is
 * asking. This class is that "who", held per thread so
 * {@link TenantContextDataSource} can stamp it onto each connection it hands out.
 *
 * <p><b>Unset means denied, not allowed.</b> A thread that never sets a scope
 * gets {@link Scope#DENY}, under which the predicate matches nothing. That is
 * deliberate: the failure mode of forgetting to set a scope should be an
 * obviously empty result, not a silent leak of every country's data. Code that
 * legitimately needs breadth — startup, migrations, scheduled jobs — says so by
 * calling {@link #runAsSystem}.
 *
 * <p>The scope is resolved once per request by {@link TenantContextFilter} rather
 * than per connection. Resolving it per connection would recurse: working out a
 * user's countries reads UMSCORE.UserCountries, which needs a connection, which
 * would need the scope.
 */
public final class TenantContext {

    /**
     * A resolved country scope.
     *
     * @param all       true for ministry users and for system work: every country.
     * @param countryIds the specific countries, when {@code all} is false.
     */
    public record Scope(boolean all, Set<Integer> countryIds, boolean wafarWrite) {

        /** Nothing visible. The default for any thread that has not said otherwise. */
        public static final Scope DENY = new Scope(false, Collections.emptySet(), false);

        /**
         * Every country, Wafar writable. Startup, migrations and explicitly-elevated
         * jobs: every {@code runAsSystem} site carries a reason (S-02), and that
         * reason covers the write key too.
         */
        public static final Scope SYSTEM = new Scope(true, Collections.emptySet(), true);

        /** Every country, read-only on Wafar: the scope of a ministry-level account (N-04.d). */
        public static final Scope MINISTRY = new Scope(true, Collections.emptySet(), false);

        public Scope(boolean all, Set<Integer> countryIds) {
            this(all, countryIds, !all && !countryIds.isEmpty());
        }

        /**
         * The scope of an account.
         *
         * <p>A ministry user sees every country and may write no Wafar row:
         * the {@code TenantWafarWrite} key (V095) stays 0 on their connections, so
         * a code path that forgets {@code WafarPolicy} is stopped by the database.
         * A country account may write the Wafar of its own attachés.
         */
        public static Scope of(boolean ministryUser, List<Integer> ids) {
            if (ministryUser) {
                return MINISTRY;
            }
            if (ids == null || ids.isEmpty()) {
                return DENY;
            }
            return new Scope(false, new LinkedHashSet<>(ids), true);
        }

        /**
         * The comma-separated id list handed to {@code SESSION_CONTEXT}.
         *
         * <p>Empty for {@link #all} (the predicate short-circuits on the
         * TenantAll flag and never reads this) and for {@link #DENY} (an empty
         * list matches no row).
         */
        public String idsAsCsv() {
            return countryIds.stream().map(String::valueOf).collect(Collectors.joining(","));
        }
    }

    private static final ThreadLocal<Scope> CURRENT = ThreadLocal.withInitial(() -> Scope.DENY);

    private TenantContext() {
    }

    public static Scope current() {
        return CURRENT.get();
    }

    public static void set(Scope scope) {
        CURRENT.set(scope == null ? Scope.DENY : scope);
    }

    /**
     * Clears the scope back to {@link Scope#DENY}.
     *
     * <p>Must be called in a finally block by whoever set it. Threads are pooled,
     * so a scope left behind would be inherited by whatever request the container
     * hands that thread next.
     */
    public static void clear() {
        CURRENT.remove();
    }

    /**
     * Runs {@code work} with full access, restoring the previous scope afterwards.
     *
     * <p>For work that is not on behalf of one country: resolving who the caller
     * is in the first place, schema migrations, and scheduled jobs that have
     * established their own authority by other means.
     */
    public static <T> T runAsSystem(Supplier<T> work) {
        Scope previous = CURRENT.get();
        CURRENT.set(Scope.SYSTEM);
        try {
            return work.get();
        } finally {
            CURRENT.set(previous);
        }
    }

    public static void runAsSystem(Runnable work) {
        runAsSystem(() -> {
            work.run();
            return null;
        });
    }

    /** Runs {@code work} scoped to specific countries, restoring the previous scope after. */
    public static <T> T runAs(Scope scope, Supplier<T> work) {
        Scope previous = CURRENT.get();
        CURRENT.set(scope == null ? Scope.DENY : scope);
        try {
            return work.get();
        } finally {
            CURRENT.set(previous);
        }
    }
}
