package com.dilaw.mohe.studentportal;

import com.dilaw.mohe.config.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Country scope across STUDENTCORE (V037).
 *
 * <p>The portal's realm is separate from UMSCORE and held no country at all. The
 * exposure was not on the portal side — a student only ever reads their own row —
 * but on the ministry side: the staff screens that list and edit portal accounts
 * had nothing to scope on, so a country officer could manage every country's
 * students.
 *
 * <p>Putting the auth tables behind a predicate re-creates the circular
 * dependency V032 sidestepped: reading a session is how a caller's scope is
 * established, so that read cannot be filtered by the scope it produces.
 * {@link #tokenResolution_needsElevation_andHasIt} is the test that pins that
 * down.
 *
 * <p>Not {@code @Transactional}: several tests enable the security policy, and a
 * test transaction borrows its connection — and its scope — before the test body
 * runs. See {@link StudentPortalLoginCountryTest} for the same reasoning.
 */
@Tag("integration")
@SpringBootTest
class StudentCoreCountryScopeTest {

    /** Every table in the schema, so a new one added later fails this suite. */
    private static final int STUDENTCORE_TABLES = 10;

    @Autowired private JdbcTemplate jdbc;
    @Autowired private com.dilaw.mohe.scope.DdlJdbc ddl;
    @Autowired private StudentUserAuthService authService;
    @Autowired private TransactionTemplate transactionTemplate;

    /** Policy state before the test; restored afterwards — see {@code AccountingCountryScopeTest}. */
    private boolean policyWasEnabled;

    @org.junit.jupiter.api.BeforeEach
    void rememberPolicyState() {
        Integer enabled = jdbc.queryForObject(
                "SELECT CAST(is_enabled AS INT) FROM sys.security_policies WHERE name = 'tenant_country_policy'",
                Integer.class);
        policyWasEnabled = enabled != null && enabled == 1;
    }

    @AfterEach
    void leaveNothingBehind() {
        TenantContext.clear();
        setPolicy(policyWasEnabled);
    }

    @Test
    @DisplayName("every STUDENTCORE table carries a country column and is under the policy")
    void everyTable_isScoped() {
        List<String> withoutColumn = jdbc.queryForList("""
                SELECT t.name FROM sys.tables t
                WHERE SCHEMA_NAME(t.schema_id) = 'STUDENTCORE'
                  AND COL_LENGTH('STUDENTCORE.' + t.name, 'STUDY_COUNTRY_IN_ID') IS NULL
                ORDER BY t.name
                """, String.class);
        assertThat(withoutColumn)
                .as("a STUDENTCORE table with no country column cannot be scoped at all")
                .isEmpty();

        List<String> outsidePolicy = jdbc.queryForList("""
                SELECT t.name FROM sys.tables t
                WHERE SCHEMA_NAME(t.schema_id) = 'STUDENTCORE'
                  AND NOT EXISTS (SELECT 1 FROM sys.security_predicates sp
                                  JOIN sys.security_policies p ON p.object_id = sp.object_id
                                  WHERE p.name = 'tenant_country_policy'
                                    AND sp.target_object_id = t.object_id)
                ORDER BY t.name
                """, String.class);
        assertThat(outsidePolicy)
                .as("a column without a predicate is documentation, not enforcement")
                .isEmpty();

        assertThat(tableCount())
                .as("a table was added to STUDENTCORE since V037 — give it a country column "
                        + "and a predicate, then update this count")
                .isEqualTo(STUDENTCORE_TABLES);
    }

    @Test
    @DisplayName("a scoped caller sees only their own country's portal accounts")
    void portalAccounts_areFilteredByCountry() {
        int realCountry = jdbc.queryForObject(
                "SELECT TOP 1 STUDY_COUNTRY_IN_ID FROM STUDENTCORE.student_users WHERE STUDY_COUNTRY_IN_ID IS NOT NULL",
                Integer.class);
        int total = countUsers(TenantContext.Scope.SYSTEM);
        assertThat(total).as("no portal accounts to scope").isGreaterThan(0);

        setPolicy(true);

        assertThat(countUsers(TenantContext.Scope.of(false, List.of(realCountry))))
                .as("a country officer must still see the accounts that are theirs")
                .isEqualTo(total);
        assertThat(countUsers(TenantContext.Scope.of(false, List.of(realCountry + 5000))))
                .as("and none of the accounts that are not — this is what the staff screens "
                        + "had no way to enforce before V037")
                .isZero();
        assertThat(countUsers(TenantContext.Scope.SYSTEM))
                .as("a ministry-wide user still sees everything")
                .isEqualTo(total);
    }

    @Test
    @DisplayName("resolving a token needs elevation, and the interceptor gives it")
    void tokenResolution_needsElevation_andHasIt() {
        // A live session, created for this test. The eleven already in the table
        // are all expired, and resolveFromToken quite correctly ignores those —
        // an earlier version of this test used one and failed for that reason,
        // which looked like the elevation not working.
        String token = "scope-test-" + java.util.UUID.randomUUID().toString().replace("-", "");
        try {
            jdbc.update("""
                    INSERT INTO STUDENTCORE.student_sessions
                        (student_user_id, student_id, token, expires_at, created_at, study_country_in_id)
                    SELECT TOP 1 su.student_user_id, su.student_id, ?,
                           DATEADD(DAY, 1, SYSUTCDATETIME()), SYSUTCDATETIME(), su.STUDY_COUNTRY_IN_ID
                    FROM STUDENTCORE.student_users su
                    WHERE su.STUDY_COUNTRY_IN_ID IS NOT NULL
                    ORDER BY su.student_user_id
                    """, com.dilaw.mohe.util.PortalTokens.hash(token)); // O-06: the row holds the hash

            setPolicy(true);

            // A portal request arrives with no scope — working out who is asking
            // is what produces one. Unelevated, the session row is invisible and
            // the student is logged out on their next click.
            Integer visibleUnscoped = TenantContext.runAs(TenantContext.Scope.DENY, () -> jdbc.queryForObject(
                    "SELECT COUNT(*) FROM STUDENTCORE.student_sessions WHERE token = ?", Integer.class, com.dilaw.mohe.util.PortalTokens.hash(token)));
            assertThat(visibleUnscoped)
                    .as("student_sessions is behind the predicate, so an unscoped read finds "
                            + "nothing — which is why StudentPortalTokenInterceptor elevates it")
                    .isZero();

            // What the interceptor actually does.
            TenantContext.set(TenantContext.Scope.DENY);
            assertThat(TenantContext.runAsSystem(() -> authService.resolveFromToken(token)))
                    .as("elevated, the same lookup resolves — safe at the call site because "
                            + "resolveFromToken is not @Transactional, so the connection is "
                            + "borrowed inside the elevated block")
                    .isPresent();
        } finally {
            setPolicy(false);
            TenantContext.runAsSystem(() ->
                    jdbc.update("DELETE FROM STUDENTCORE.student_sessions WHERE token = ?", token));
        }
    }

    @Test
    @DisplayName("the RBAC catalogue stays visible to every country")
    void rbacCatalogue_isMinistryWide() {
        setPolicy(true);

        for (String table : List.of("student_roles", "student_permissions",
                "student_role_permissions", "student_groups", "student_group_roles")) {
            int asSystem = count(table, TenantContext.Scope.SYSTEM);
            int asCountry = count(table, TenantContext.Scope.of(false, List.of(999999)));

            assertThat(asCountry)
                    .as("%s holds role and permission definitions with no student behind them. "
                            + "Their country is NULL, which the predicate reads as ministry-wide — "
                            + "if these were filtered, a portal user in any country would lose "
                            + "every permission they have", table)
                    .isEqualTo(asSystem);
        }
    }

    @Test
    @DisplayName("a student moving country takes their portal rows with them")
    void movingAStudent_cascadesIntoStudentcore() {
        // Rolled back: this rewrites a live student's country to prove the trigger
        // chain, and must not keep the change.
        transactionTemplate.execute(status -> {
            Integer studentId = jdbc.queryForObject(
                    "SELECT TOP 1 student_id FROM STUDENTCORE.student_users ORDER BY student_user_id",
                    Integer.class);
            int moved = 1007;

            jdbc.update("UPDATE MOHECORE.STUDENT_DATA SET STUDY_COUNTRY_IN = ? WHERE STUDENT_ID = ?",
                    moved, studentId);

            assertThat(countryOfUser(studentId))
                    .as("the portal account must follow the student")
                    .isEqualTo(moved);
            assertThat(countryOfUserGroup(studentId))
                    .as("so must the group membership. An earlier version of V037 left this "
                            + "behind — its drift trigger is defined ON student_user_groups, so "
                            + "it fires for writes to that table, not to its parent. The old "
                            + "country kept seeing the row and the new one could not.")
                    .isEqualTo(moved);
            assertThat(countryOfSession(studentId))
                    .as("but not the session: its country is a snapshot of who the caller was "
                            + "when the token was issued, and a move should end the session "
                            + "rather than silently rescope it")
                    .isNotEqualTo(moved);

            status.setRollbackOnly();
            return null;
        });
    }

    // ---------------------------------------------------------------- helpers

    private Integer tableCount() {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM sys.tables WHERE SCHEMA_NAME(schema_id) = 'STUDENTCORE'",
                Integer.class);
    }

    private int countUsers(TenantContext.Scope scope) {
        return count("student_users", scope);
    }

    private int count(String table, TenantContext.Scope scope) {
        return TenantContext.runAs(scope, () -> jdbc.queryForObject(
                "SELECT COUNT(*) FROM STUDENTCORE." + table, Integer.class));
    }

    private Integer countryOfUser(Integer studentId) {
        return jdbc.queryForObject(
                "SELECT STUDY_COUNTRY_IN_ID FROM STUDENTCORE.student_users WHERE student_id = ?",
                Integer.class, studentId);
    }

    private Integer countryOfUserGroup(Integer studentId) {
        return jdbc.queryForObject("""
                SELECT TOP 1 g.STUDY_COUNTRY_IN_ID
                FROM STUDENTCORE.student_user_groups g
                JOIN STUDENTCORE.student_users su ON su.student_user_id = g.student_user_id
                WHERE su.student_id = ?
                """, Integer.class, studentId);
    }

    private Integer countryOfSession(Integer studentId) {
        return jdbc.queryForObject(
                "SELECT TOP 1 STUDY_COUNTRY_IN_ID FROM STUDENTCORE.student_sessions WHERE student_id = ?",
                Integer.class, studentId);
    }

    /**
     * Enables or disables the policy for real.
     *
     * <p>Always put back to its original state in {@link #leaveNothingBehind()}:
     * an earlier version forced it OFF, which switched row-level security off on
     * the development database on every full run.
     */
    private void setPolicy(boolean on) {
        // DDL: through the migrations' login, since mohe_app is denied ALTER ANY SECURITY POLICY (V090).
        ddl.setTenantPolicy(on);
    }
}
