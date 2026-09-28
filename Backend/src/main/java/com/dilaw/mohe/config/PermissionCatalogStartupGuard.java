package com.dilaw.mohe.config;

import com.dilaw.mohe.config.tenant.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Refuses to serve when the database lacks a permission code the code enforces.
 *
 * <p>Security plan R-11 (6 Sep 2026). A {@code @PreAuthorize} naming a code that
 * no row carries is not an error anywhere: the expression simply never matches,
 * and the endpoint is reachable by administrators alone — which looks, from the
 * outside, like a permission nobody was granted rather than one nobody
 * <em>could</em> be granted. Five quarter-management endpoints ran that way for
 * months. Same philosophy as {@link CodeListStartupGuard}: a deployment missing
 * a security setting should fail where someone will see it, not serve.
 *
 * <p>Under the {@code dev} profile the mismatch is logged as an error and the
 * application comes up, so a developer who has not yet applied the latest seed
 * script is told what to run rather than locked out.
 */
@Component
public class PermissionCatalogStartupGuard {

    private static final Logger log = LoggerFactory.getLogger(PermissionCatalogStartupGuard.class);

    private final JdbcTemplate jdbc;
    private final Environment environment;

    public PermissionCatalogStartupGuard(JdbcTemplate jdbc, Environment environment) {
        this.jdbc = jdbc;
        this.environment = environment;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void verify() {
        Set<String> missing = missingCodes();
        if (missing.isEmpty()) {
            log.info("Permission catalogue: all {} enforced codes present in UMSCORE.Permissions", PermissionCatalog.codes().size());
            return;
        }
        String message = "Permission catalogue mismatch: " + missing.size()
                + " code(s) enforced by the application are missing or inactive in UMSCORE.Permissions: "
                + missing + ". Apply the latest V0xx__permission_catalog_seed.sql (Backend/docs/scripts).";
        if (environment.matchesProfiles("dev")) {
            log.error(message);
            return;
        }
        throw new IllegalStateException(message);
    }

    /** Enforced codes with no active row. Package-private for the test. */
    Set<String> missingCodes() {
        // tenant-bypass: startup read of the permission table, which is not
        // tenanted; there is no request and so no scope to carry.
        List<String> present = TenantContext.runAsSystem(() ->
                jdbc.queryForList("SELECT PermissionCode FROM UMSCORE.Permissions WHERE IsActive = 1", String.class));
        Set<String> have = new HashSet<>(present);
        Set<String> missing = new TreeSet<>();
        for (String code : PermissionCatalog.codes()) {
            if (!have.contains(code)) missing.add(code);
        }
        return missing;
    }
}
