package com.dilaw.mohe;

import com.dilaw.mohe.config.tenant.TenantContext;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * Gives every test the tenant scope a request would have had.
 *
 * <p>{@link TenantContext} is fail-closed: a thread that never sets a scope is
 * {@link TenantContext.Scope#DENY}, and with {@code tenant_country_policy} on
 * (V062) the row-level-security predicate then matches no row. In production
 * {@code TenantContextFilter} resolves a scope for each request; a JUnit thread
 * has no filter, so until the third revision pass (27 Aug 2026) every fixture
 * read made through a bare {@code JdbcTemplate} — "an Egyptian student",
 * "a document on that student", "the archive holds 941 students" — returned
 * nothing once the policy was on. Eighty-six tests failed or assumed
 * themselves away; the suite had only ever been green because its own
 * teardown switched the policy off.
 *
 * <p>This extension is registered globally (see
 * {@code META-INF/services/org.junit.jupiter.api.extension.Extension} and
 * {@code junit-platform.properties}) and stamps {@link TenantContext.Scope#SYSTEM}
 * before each test, clearing it afterwards. It runs before {@code SpringExtension},
 * so a {@code @Transactional} test borrows its connection under that scope.
 * Tests that are <em>about</em> scoping narrow it themselves: {@link
 * com.dilaw.mohe.scope.AsUser} resolves the real scope of the account it signs
 * in as, and {@code TenantContext.runAs(...)} is available for anything else.
 * A test that wants to prove that an unscoped thread sees nothing says so with
 * {@code runAs(Scope.DENY, ...)} — as {@code StudentCoreCountryScopeTest} does.
 */
public class TestTenantScopeExtension
        implements BeforeAllCallback, AfterAllCallback, BeforeEachCallback, AfterEachCallback {

    @Override
    public void beforeAll(ExtensionContext context) {
        TenantContext.set(TenantContext.Scope.SYSTEM);
    }

    @Override
    public void afterAll(ExtensionContext context) {
        TenantContext.clear();
    }

    @Override
    public void beforeEach(ExtensionContext context) {
        TenantContext.set(TenantContext.Scope.SYSTEM);
    }

    @Override
    public void afterEach(ExtensionContext context) {
        TenantContext.clear();
    }
}
