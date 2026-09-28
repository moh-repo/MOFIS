package com.dilaw.mohe.scope;

import com.dilaw.mohe.config.tenant.TenantContext;
import com.dilaw.mohe.ums.service.CountryScopeService;
import jakarta.annotation.PostConstruct;
import jakarta.persistence.EntityManagerFactory;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;

/**
 * Lets {@link AsUser} resolve the tenant scope of the account it signs in as.
 *
 * <p>{@code AsUser} is a static helper with no Spring context; this component,
 * picked up by every {@code @SpringBootTest} because it lives under the
 * application's base package, hands it the real {@link CountryScopeService} so
 * that "run as the Jordan admin" also means "with Jordan's SESSION_CONTEXT" —
 * the same resolution {@code TenantContextFilter} performs for a request. Before
 * the third revision pass (27 Aug 2026) {@code AsUser} set only the Spring
 * Security principal, so the Java-side country checks were exercised while the
 * database predicate never was.
 */
@Component
public class AsUserScopeBridge {

    private final CountryScopeService countryScopeService;
    private final EntityManagerFactory entityManagerFactory;

    public AsUserScopeBridge(CountryScopeService countryScopeService, EntityManagerFactory entityManagerFactory) {
        this.countryScopeService = countryScopeService;
        this.entityManagerFactory = entityManagerFactory;
    }

    @PostConstruct
    void register() {
        // Keyed by the factory JpaTransactionManager binds, so a transactional test
        // resolves through its own context (see AsUser.scopeOf).
        AsUser.scopeResolver(entityManagerFactory, userId -> TenantContext.runAsSystem(() -> {
            CountryScopeService.CountryScope scope = countryScopeService.getScopeForUser(userId);
            // A test that @MockBeans the service and stubs only getScope() hands back
            // null here; its scoping is Java-side by design, so the database stays
            // wide open for it, exactly as it was before the policy was enforced.
            return scope == null
                    ? TenantContext.Scope.SYSTEM
                    : TenantContext.Scope.of(scope.isMinistryUser(), scope.getAllowedCountryIds());
        }));
    }

    @PreDestroy
    void unregister() {
        AsUser.scopeResolver(entityManagerFactory, null);
    }
}
