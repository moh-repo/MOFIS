package com.dilaw.mohe.scope;

import com.dilaw.mohe.config.tenant.TenantContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * V102: the database's own opinion about who may read a staff account.
 *
 * <p>{@code StaffAdministrationScopeTest} asserts the Java rule by asking the
 * service. This asks nothing — it goes to the tables directly with raw SQL,
 * because the whole point of a backstop is what happens when the service is not
 * in the way. A finder written without {@code UmsUserService.scoped()} is the
 * failure this catches, and it is a failure no Java-level test can see.
 *
 * <p><b>This suite never switches the policy.</b> {@code DdlJdbc.setTenantPolicy}
 * exists and would work, but a test that toggles staff RLS and then dies leaves
 * a database nobody can sign in to, and the JUnit run that does it is
 * unattended by definition. So each test asks whether the policy is enabled and
 * assumes itself away when it is not: silent until the policy is switched on,
 * load-bearing after. {@link #policyIsInstalled()} is the one that always runs,
 * so "V102 was never applied here" is reported rather than skipped past.
 *
 * <p>V102 creates the policy disabled and {@code adhoc/enable_staff_country_policy.sql}
 * turns it on, deliberately not a migration: Flyway applies the numbered scripts
 * on startup, so an enable script among them would flip staff RLS on at the next
 * boot of any environment — exactly the unattended change the two-step rollout
 * exists to prevent.
 *
 * <p>The scope is set through {@link TenantContext#runAs} rather than
 * {@link AsUser}, deliberately: these tests are about the predicate, so they name
 * the country ids the predicate will see instead of routing through the service
 * that resolves them.
 */
@Tag("integration")
@SpringBootTest
class StaffRlsBackstopTest {

    private static final int JORDAN = 1004;
    private static final int SAUDI = 1005;

    @Autowired private JdbcTemplate jdbc;

    private boolean policyEnabled() {
        Boolean on = jdbc.queryForObject(
                "SELECT CAST(COALESCE(MAX(CAST(is_enabled AS INT)), 0) AS BIT) "
                        + "FROM sys.security_policies WHERE name = 'staff_country_policy'", Boolean.class);
        return Boolean.TRUE.equals(on);
    }

    private void assumePolicyOn() {
        assumeTrue(policyEnabled(),
                "UMSCORE.staff_country_policy is not enabled here — apply V102 and adhoc/enable_staff_country_policy.sql to exercise the backstop");
    }

    /** Usernames a connection scoped to {@code countryIds} can read, straight from the table. */
    private List<String> staffVisibleTo(Integer... countryIds) {
        return TenantContext.runAs(
                TenantContext.Scope.of(false, List.of(countryIds)),
                () -> jdbc.queryForList(
                        "SELECT UserName FROM UMSCORE.Users WHERE IsDeleted = 0 ORDER BY Id", String.class));
    }

    @Test
    @DisplayName("V102 is applied and covers the staff tables")
    void policyIsInstalled() {
        List<String> covered = jdbc.queryForList("""
                SELECT DISTINCT OBJECT_NAME(sp.target_object_id)
                FROM sys.security_predicates sp
                JOIN sys.security_policies p ON p.object_id = sp.object_id
                WHERE p.name = 'staff_country_policy'
                """, String.class);

        assertThat(covered)
                .as("""
                    The account and everything hanging off it. A satellite row says as much as \
                    the account does: UserSessions carries an address and a device, UserActions \
                    carries who granted what to whom, UserMfa says whether a second factor is on.""")
                .contains("Users", "UserCountries", "UserRoles", "UserGroupMemberships", "UserPermissions",
                        "UserSessions", "UserActions", "UserMfa", "UserMfaRecoveryCodes", "AccessReviewItems");
    }

    @Test
    @DisplayName("a Jordan connection cannot read a Saudi-only staff account")
    void jordanCannotReadSaudiStaff() {
        assumePolicyOn();

        assertThat(staffVisibleTo(JORDAN))
                .as("the leak this policy exists to close, tested where the Java layer cannot reach")
                .doesNotContain("sa_admin", "sa_staff", "sa_reviewer")
                .contains("jo_admin", "jo_staff", "jo_reviewer");
    }

    @Test
    @DisplayName("and the mirror image holds for Saudi")
    void saudiCannotReadJordanStaff() {
        assumePolicyOn();

        assertThat(staffVisibleTo(SAUDI))
                .doesNotContain("jo_admin", "jo_staff", "jo_reviewer")
                .contains("sa_admin", "sa_staff", "sa_reviewer");
    }

    @Test
    @DisplayName("the ministry still reads every staff account")
    void ministryReadsEverything() {
        assumePolicyOn();

        Integer total = TenantContext.runAsSystem(() -> jdbc.queryForObject(
                // tenant-bypass: the control for this suite is the unfiltered count, which is
                // what the system scope is for. Reads a count of staff rows, nothing tenanted.
                "SELECT COUNT(*) FROM UMSCORE.Users WHERE IsDeleted = 0", Integer.class));

        List<String> ministry = TenantContext.runAs(TenantContext.Scope.MINISTRY,
                () -> jdbc.queryForList("SELECT UserName FROM UMSCORE.Users WHERE IsDeleted = 0", String.class));

        assertThat(ministry)
                .as("TenantAll = 1 short-circuits the predicate; a ministry tier loses nothing")
                .hasSize(total);
    }

    @Test
    @DisplayName("a ministry-wide account is visible to a country connection, and that is deliberate")
    void intersectionNotSubset() {
        assumePolicyOn();

        assertThat(staffVisibleTo(JORDAN))
                .as("""
                    The one place the database rule is weaker than the Java rule, on purpose. \
                    StaffCountrySpec asks whether an account's attachés are *within* mine, so \
                    `admin` — which holds all eighteen — is not Jordan's to administer. Enforced \
                    in the predicate that same rule would hide every ministry-wide account from \
                    every country connection, and each ordinary read that resolves a name (who \
                    signed this, who is the named approver) would come back empty across the \
                    whole application. So the predicate asks the weaker question: do we share an \
                    attaché at all. The strict rule stays in StaffCountrySpec, which guards one \
                    screen rather than every query — and StaffAdministrationScopeTest asserts it \
                    is still strict there.""")
                .contains("admin");
    }

    @Test
    @DisplayName("an account awaiting assignment stays visible, so somebody can assign it")
    void unassignedAccountIsStillVisible() {
        assumePolicyOn();
        assumeTrue(TenantContext.runAsSystem(() -> jdbc.queryForObject(
                        // tenant-bypass: reads whether the fixture account has assignments, to
                        // decide whether this test has anything to measure. One count, no rows.
                        "SELECT COUNT(*) FROM UMSCORE.UserCountries uc JOIN UMSCORE.Users u ON u.Id = uc.UserId "
                                + "WHERE u.UserName = 'sa_superadmin' AND uc.IsActive = 1", Integer.class)) == 0,
                "sa_superadmin has been given countries; this test needs the unassigned case");

        assertThat(staffVisibleTo(JORDAN))
                .as("""
                    Clause 3, and ungated here where StaffCountrySpec gates it on being an admin. \
                    The account that has just been created and not yet assigned is the one the \
                    creation wizard is holding; hiding it in the database breaks the wizard for \
                    the administrator who is allowed to see it anyway.""")
                .contains("sa_superadmin");
    }

    @Test
    @DisplayName("staff sessions and actions follow the account they belong to")
    void satellitesFollowTheirAccount() {
        assumePolicyOn();

        for (String table : Set.of("UMSCORE.UserSessions", "UMSCORE.UserActions", "UMSCORE.UserRoles")) {
            int jordan = TenantContext.runAs(TenantContext.Scope.of(false, List.of(JORDAN)),
                    () -> jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class));
            int ministry = TenantContext.runAs(TenantContext.Scope.MINISTRY,
                    () -> jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class));

            assertThat(jordan)
                    .as("%s is filtered by the account it hangs off, not left open", table)
                    .isLessThan(ministry);
        }
    }

    @Test
    @DisplayName("a connection cannot hand out an attaché it does not hold")
    void cannotAssignACountryYouDoNotHold() {
        assumePolicyOn();

        Long unassigned = TenantContext.runAsSystem(() -> jdbc.queryForObject(
                // tenant-bypass: resolving the fixture account's id. Under the policy a Jordan
                // connection can see this row anyway (it holds nothing), but the id must be
                // right whatever the fixture's assignments are, so it is read unfiltered.
                "SELECT Id FROM UMSCORE.Users WHERE UserName = 'sa_superadmin'", Long.class));

        assertThatThrownBy(() -> TenantContext.runAs(TenantContext.Scope.of(false, List.of(JORDAN)),
                () -> jdbc.update("""
                        INSERT INTO UMSCORE.UserCountries (UserId, CountryId, AssignedAt, IsActive)
                        VALUES (?, ?, SYSUTCDATETIME(), 1)
                        """, unassigned, SAUDI)))
                .as("""
                    The block predicate, and the one write worth stopping in the database: \
                    handing out an attaché you do not hold is how a country administrator would \
                    otherwise widen its own reach. Note it tests the row's country rather than \
                    its user — a user already inside your scope stays inside it however many \
                    countries you add, so the user-shaped predicate would let this through.""")
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("block predicate");

        assertThat(TenantContext.runAsSystem(() -> jdbc.queryForObject(
                // tenant-bypass: proving the refused insert left nothing behind has to look
                // past the predicate, or an invisible row would read as an absent one.
                "SELECT COUNT(*) FROM UMSCORE.UserCountries WHERE UserId = ? AND CountryId = ?",
                Integer.class, unassigned, SAUDI)))
                .as("refused, not merely hidden")
                .isZero();
    }

    @Test
    @DisplayName("assigning an attaché the connection does hold still works")
    void canAssignACountryYouHold() {
        assumePolicyOn();

        Long unassigned = TenantContext.runAsSystem(() -> jdbc.queryForObject(
                // tenant-bypass: the same fixture id as above, read the same way.
                "SELECT Id FROM UMSCORE.Users WHERE UserName = 'sa_superadmin'", Long.class));

        try {
            TenantContext.runAs(TenantContext.Scope.of(false, List.of(JORDAN)),
                    () -> jdbc.update("""
                            INSERT INTO UMSCORE.UserCountries (UserId, CountryId, AssignedAt, IsActive)
                            VALUES (?, ?, SYSUTCDATETIME(), 1)
                            """, unassigned, JORDAN));

            assertThat(TenantContext.runAsSystem(() -> jdbc.queryForObject(
                    // tenant-bypass: the positive control has to be read unfiltered for the
                    // same reason the negative one does.
                    "SELECT COUNT(*) FROM UMSCORE.UserCountries WHERE UserId = ? AND CountryId = ?",
                    Integer.class, unassigned, JORDAN)))
                    .as("""
                        The positive control, and it is not a formality: a block predicate that \
                        refuses everything would pass every test above while making the users \
                        screen useless.""")
                    .isEqualTo(1);
        } finally {
            TenantContext.runAsSystem(() -> jdbc.update(
                    // tenant-bypass: teardown must remove the row whatever scope it ended up
                    // visible under, or the next run inherits it and sa_superadmin is no longer
                    // the unassigned fixture the rest of this suite relies on.
                    "DELETE FROM UMSCORE.UserCountries WHERE UserId = ? AND CountryId = ?", unassigned, JORDAN));
        }
    }
}
