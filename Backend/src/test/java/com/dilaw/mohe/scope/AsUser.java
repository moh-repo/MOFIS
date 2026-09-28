package com.dilaw.mohe.scope;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.dilaw.mohe.config.tenant.TenantContext;

import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Runs a block as one of the real UMS accounts.
 *
 * <p>Deliberately not a mocked {@code CountryScopeService}. The whole question
 * these tests answer is what a given account can see, and a stubbed scope would
 * test the stub — including the part most likely to be wrong, which is how
 * {@code getScopeForUser} turns a row in {@code UMSCORE.Users} and its
 * {@code UserCountries} into a scope. So the real service resolves a real user.
 *
 * <p>{@code SecurityRequestUtils.currentUserId()} parses the authentication name
 * as a long, so the principal here is the user id as a string — the same shape the
 * JWT filter produces.
 *
 * <p>The ids are those of the seeded accounts. If the seed changes, these tests
 * fail loudly on the assertion rather than silently passing against a user that
 * happens to have no data.
 */
public final class AsUser {

    private AsUser() {}

    /** Egypt + Unspecified, and IsSuperAdmin — so ministry-wide since the Phase 0 change. */
    public static final long SUPERADMIN = 1L;

    /** All 18 countries, enumerated in UserCountries. Not a super admin. */
    public static final long ADMIN = 2L;

    /** IsSuperAdmin with no UserCountries rows at all — the account that saw nothing. */
    public static final long SA_SUPERADMIN = 9L;

    /** Saudi Arabia (1005) only. */
    public static final long SA_ADMIN = 10L;

    /** Jordan (1004) only. */
    public static final long JO_ADMIN = 13L;

    public static <T> T call(long userId, Supplier<T> body) {
        return callWithAuthorities(userId, List.of(), body);
    }

    /**
     * As {@link #call} but with granted authorities, for tests that go through a
     * controller and so hit its {@code @PreAuthorize}.
     *
     * <p>Permission and country scope are separate gates and these tests are about
     * the second: granting the permission is what lets the country check be the
     * thing under test rather than the thing blocking it.
     */
    public static <T> T callWithAuthorities(long userId, List<String> authorities, Supplier<T> body) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(String.valueOf(userId), null,
                        authorities.stream().map(SimpleGrantedAuthority::new).map(a -> (GrantedAuthority) a).toList()));
        try {
            // The database predicate too, not only the Java-side checks: the same
            // scope TenantContextFilter would have resolved for this account.
            return TenantContext.runAs(scopeOf(userId), body);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    /**
     * Resolves a user's tenant scope, installed by {@link AsUserScopeBridge} once
     * a Spring context is up. Without it (a plain unit test) the thread's current
     * scope is kept — {@code SYSTEM} under {@code TestTenantScopeExtension}.
     *
     * <p>One resolver per live Spring context, keyed by that context's
     * {@code EntityManagerFactory}. A full run keeps several cached contexts alive
     * in one JVM, and a {@code @Transactional} test must resolve through its own
     * context: another context's repository reads on a connection of its own pool
     * and blocks on the rows the test's transaction has just written (the
     * RoleWindowTest self-block of 6 Sep 2026). The resolver whose factory is bound
     * to the current transaction wins; outside a transaction any live one will do.
     */
    private static final java.util.Map<Object, Function<Long, TenantContext.Scope>> RESOLVERS =
            new java.util.LinkedHashMap<>();

    static void scopeResolver(Object transactionKey, Function<Long, TenantContext.Scope> resolver) {
        synchronized (RESOLVERS) {
            if (resolver == null) {
                RESOLVERS.remove(transactionKey);
            } else {
                RESOLVERS.put(transactionKey, resolver);
            }
        }
    }

    private static TenantContext.Scope scopeOf(long userId) {
        Function<Long, TenantContext.Scope> resolver = null;
        synchronized (RESOLVERS) {
            for (var entry : RESOLVERS.entrySet()) {
                if (TransactionSynchronizationManager.hasResource(entry.getKey())) {
                    resolver = entry.getValue();
                    break;
                }
            }
            if (resolver == null) {
                for (Function<Long, TenantContext.Scope> candidate : RESOLVERS.values()) {
                    resolver = candidate;
                }
            }
        }
        return resolver == null ? TenantContext.current() : resolver.apply(userId);
    }

    /** The scope in force before {@link #signIn}, put back by {@link #signOut}. */
    private static final ThreadLocal<TenantContext.Scope> BEFORE_SIGN_IN = new ThreadLocal<>();

    public static void run(long userId, Runnable body) {
        call(userId, () -> { body.run(); return null; });
    }

    /**
     * Signs in for the whole test method, for suites that are about something else
     * and merely need a caller.
     *
     * <p>Services in the accounting and approval modules resolve the country scope
     * from the security context, and an absent context is {@code noAccess} — the
     * strictest scope there is. That is the right default for a real request with
     * no token; it is the wrong setting for a test of the ledger identity, which
     * would otherwise be refused before it could measure anything.
     *
     * <p>Pair with {@link #signOut()} in an {@code @AfterEach}.
     */
    public static void signIn(long userId) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(String.valueOf(userId), null, List.of()));
        BEFORE_SIGN_IN.set(TenantContext.current());
        TenantContext.set(scopeOf(userId));
    }

    public static void signOut() {
        SecurityContextHolder.clearContext();
        TenantContext.Scope before = BEFORE_SIGN_IN.get();
        BEFORE_SIGN_IN.remove();
        if (before != null) {
            TenantContext.set(before);
        }
    }
}
