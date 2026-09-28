package com.dilaw.mohe.config.tenant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The tenant scope must reach the database session, and must not survive the
 * connection going back to the pool.
 *
 * <p>These are the two failure modes that would make Row-Level Security either
 * useless or dangerous. If the scope never arrives, every query returns nothing
 * once the policy is enabled. If it is not cleared, the next request to borrow
 * that pooled connection inherits the previous user's countries — a cross-tenant
 * read, and a far worse bug than the one RLS exists to prevent.
 *
 * <p>These assertions hold whether or not the policy is enabled: they inspect
 * SESSION_CONTEXT directly rather than relying on rows being filtered.
 */
@Tag("integration")
@SpringBootTest
class TenantContextDataSourceTest {

    @Autowired
    @Qualifier("dataSource")
    private DataSource dataSource;

    private String sessionValue(Connection c, String key) throws Exception {
        try (Statement s = c.createStatement();
             ResultSet rs = s.executeQuery(
                     "SELECT CAST(SESSION_CONTEXT(N'" + key + "') AS NVARCHAR(4000))")) {
            return rs.next() ? rs.getString(1) : null;
        }
    }

    @Test
    @DisplayName("a scoped thread's countries reach the database session")
    void scope_reachesTheSession() throws Exception {
        TenantContext.runAs(TenantContext.Scope.of(false, List.of(1004, 1005)), () -> {
            try (Connection c = dataSource.getConnection()) {
                assertThat(sessionValue(c, "TenantAll"))
                        .as("a country user is not ministry-wide")
                        .isEqualTo("0");
                assertThat(sessionValue(c, "TenantCountryIds"))
                        .as("the predicate splits this on commas")
                        .isEqualTo("1004,1005");
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
            return null;
        });
    }

    @Test
    @DisplayName("system scope is flagged so the predicate short-circuits")
    void systemScope_setsTheAllFlag() throws Exception {
        TenantContext.runAsSystem(() -> {
            try (Connection c = dataSource.getConnection()) {
                assertThat(sessionValue(c, "TenantAll")).isEqualTo("1");
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
            return null;
        });
    }

    @Test
    @DisplayName("an unscoped thread is denied, not given everything")
    void unsetScope_deniesRatherThanAllows() throws Exception {
        TenantContext.clear();
        try (Connection c = dataSource.getConnection()) {
            assertThat(sessionValue(c, "TenantAll"))
                    .as("forgetting to set a scope must not grant ministry access")
                    .isEqualTo("0");
            assertThat(sessionValue(c, "TenantCountryIds"))
                    .as("and an empty id list matches no row")
                    .isEmpty();
        }
    }

    @Test
    @DisplayName("the scope does not survive the connection returning to the pool")
    void scope_isClearedOnClose() throws Exception {
        // Borrow, scope, return.
        TenantContext.runAs(TenantContext.Scope.of(false, List.of(1004)), () -> {
            try (Connection c = dataSource.getConnection()) {
                assertThat(sessionValue(c, "TenantCountryIds")).isEqualTo("1004");
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
            return null;
        });

        // Re-borrow with no scope. Hikari will usually hand back the very same
        // physical connection, which is the case that matters.
        TenantContext.clear();
        try (Connection c = dataSource.getConnection()) {
            assertThat(sessionValue(c, "TenantCountryIds"))
                    .as("a pooled connection must not carry the previous caller's countries")
                    .isNullOrEmpty();
            assertThat(sessionValue(c, "TenantAll")).isEqualTo("0");
        }
    }

    @Test
    @DisplayName("one thread's scope does not leak into another's")
    void scope_isPerThread() throws Exception {
        TenantContext.set(TenantContext.Scope.of(false, List.of(1)));

        Set<String> seenByOtherThread = new java.util.HashSet<>();
        Thread other = new Thread(() -> {
            try (Connection c = dataSource.getConnection()) {
                seenByOtherThread.add(String.valueOf(sessionValue(c, "TenantCountryIds")));
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
        other.start();
        other.join();

        assertThat(seenByOtherThread)
                .as("a thread that set no scope must not inherit one")
                .allSatisfy(v -> assertThat(v).isIn("", "null"));

        TenantContext.clear();
    }
}
