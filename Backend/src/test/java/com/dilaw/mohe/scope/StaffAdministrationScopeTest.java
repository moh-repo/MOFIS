package com.dilaw.mohe.scope;

import com.dilaw.mohe.ums.model.UmsUser;
import com.dilaw.mohe.ums.service.UmsUserService;
import com.dilaw.mohe.ums.service.UmsUserSessionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * {@code /ums-admin/users} and {@code /ums-admin/sessions} — who may administer
 * whom.
 *
 * <p>Asserts the four clauses of {@code StaffCountrySpec} against the real seeded
 * accounts, one clause at a time, because each was a separate decision:
 *
 * <ul>
 *   <li>a super admin sees everyone;
 *   <li>a country admin sees only staff whose attachés are a <b>subset</b> of
 *       theirs — so not the ministry-wide accounts, even though those cover their
 *       country too;
 *   <li>an account with no attaché is <b>awaiting assignment</b> and visible to
 *       any admin, not to nobody and not to everybody;
 *   <li>everyone sees their own row.
 * </ul>
 *
 * <p>Read-only apart from one temporary account, which is removed in a finally.
 */
@Tag("integration")
@SpringBootTest
class StaffAdministrationScopeTest {

    @Autowired private UmsUserService userService;
    @Autowired private UmsUserSessionService sessionService;
    @Autowired private JdbcTemplate jdbc;

    private List<String> visibleTo(long userId) {
        return AsUser.call(userId, () -> userService.listUsers(null, null, null, PageRequest.of(0, 100)))
                .getContent().stream().map(UmsUser::getUserName).sorted().toList();
    }

    @Test
    @DisplayName("a super admin sees every staff account")
    void superAdminSeesEverything() {
        Integer total = jdbc.queryForObject(
                "SELECT COUNT(*) FROM UMSCORE.Users WHERE IsDeleted = 0", Integer.class);

        assertThat(visibleTo(AsUser.SUPERADMIN))
                .as("clause 1 — ministry scope bypasses the country filter entirely")
                .hasSize(total);
    }

    @Test
    @DisplayName("admin holds every attaché, so every account is a subset of its own")
    void ministryAdminSeesEverything() {
        Integer total = jdbc.queryForObject(
                "SELECT COUNT(*) FROM UMSCORE.Users WHERE IsDeleted = 0", Integer.class);

        assertThat(visibleTo(AsUser.ADMIN))
                .as("""
                    `admin` is not a super admin and does not bypass scope. It sees everything \
                    because it holds all eighteen attachés, so every other account's countries \
                    fall inside its own — and clause 3 covers sa_superadmin, which holds none. \
                    That the flag and the countries agree here is the point: the reach comes \
                    from the assignments, not from IsAdmin.""")
                .hasSize(total);
    }

    @Test
    @DisplayName("a country admin does not see the ministry-wide accounts")
    void countryAdminDoesNotSeeMinistryAccounts() {
        List<String> jordan = visibleTo(AsUser.JO_ADMIN);

        assertThat(jordan)
                .as("""
                    The subset rule, and the reason it is a subset rather than an intersection: \
                    `admin` and `superadmin` both cover Jordan, so an intersection rule would \
                    list them here — and `jo_admin` holds UMS_ADMIN.EDIT.""")
                .doesNotContain("admin", "superadmin")
                .contains("jo_admin", "jo_staff", "jo_reviewer");
    }

    @Test
    @DisplayName("Jordan and Saudi see disjoint staff, apart from what both may assign")
    void jordanAndSaudiAreDisjoint() {
        List<String> jordan = visibleTo(AsUser.JO_ADMIN);
        List<String> saudi = visibleTo(AsUser.SA_ADMIN);

        assertThat(jordan).doesNotContain("sa_staff", "sa_reviewer", "sa_admin");
        assertThat(saudi).doesNotContain("jo_staff", "jo_reviewer", "jo_admin");
    }

    @Test
    @DisplayName("an account with no attaché is visible to an admin, so somebody can assign it")
    void unassignedAccountIsVisibleToAdmins() {
        assumeTrue(jdbc.queryForObject(
                "SELECT COUNT(*) FROM UMSCORE.UserCountries WHERE UserId = ? AND IsActive = 1",
                Integer.class, AsUser.SA_SUPERADMIN) == 0,
                "sa_superadmin has been given countries; this test needs the unassigned case");

        assertThat(visibleTo(AsUser.JO_ADMIN))
                .as("""
                    Clause 3. Without it the account nobody can see is the account nobody can \
                    fix: a freshly created user has no countries, so the admin who just created \
                    them could not open them to assign one.""")
                .contains("sa_superadmin");
    }

    @Test
    @DisplayName("an unassigned account is not visible to a non-admin")
    void unassignedAccountIsNotVisibleToEveryone() {
        // hr_manager holds Egypt + Unspecified and is not an admin.
        long hrManager = 3L;
        assumeTrue(Boolean.FALSE.equals(jdbc.queryForObject(
                "SELECT COALESCE(IsAdmin, 0) FROM UMSCORE.Users WHERE Id = ?", Boolean.class, hrManager)),
                "hr_manager has become an admin; pick another non-admin");

        assertThat(visibleTo(hrManager))
                .as("""
                    Clause 3 is granted to admins, not to everyone. This is the case that makes \
                    the EXISTS companion on clause 2 necessary: "all of their countries are \
                    within mine" is vacuously true of an account with none, so without it every \
                    caller would see this row.""")
                .doesNotContain("sa_superadmin");
    }

    @Test
    @DisplayName("everyone sees their own row, whatever their countries")
    void everyoneSeesThemselves() {
        assertThat(visibleTo(AsUser.JO_ADMIN)).contains("jo_admin");
        assertThat(visibleTo(AsUser.SA_ADMIN)).contains("sa_admin");
        assertThat(visibleTo(AsUser.SA_SUPERADMIN))
                .as("clause 4 — and without it a user cannot change their own password")
                .contains("sa_superadmin");
    }

    @Test
    @DisplayName("a new account is visible to its creator, and leaves once assigned elsewhere")
    void newAccountIsVisibleUntilAssignedAway() {
        jdbc.update("""
                INSERT INTO UMSCORE.Users (UserName, PasswordHash, Email, FirstName, LastName,
                                           Status, IsAdmin, IsSuperAdmin, IsDeleted, CreatedAt)
                VALUES (N'scope_probe', N'x', N'scope_probe@test', N'Scope', N'Probe',
                        N'ACTIVE', 0, 0, 0, SYSUTCDATETIME())
                """);
        Long id = jdbc.queryForObject("SELECT Id FROM UMSCORE.Users WHERE UserName = N'scope_probe'", Long.class);

        try {
            assertThat(visibleTo(AsUser.JO_ADMIN))
                    .as("no countries yet, so clause 3 — this is what keeps the creation wizard working")
                    .contains("scope_probe");

            // Assign it to Saudi, which jo_admin does not hold.
            jdbc.update("""
                    INSERT INTO UMSCORE.UserCountries (UserId, CountryId, AssignedAt, IsActive)
                    VALUES (?, 1005, SYSUTCDATETIME(), 1)
                    """, id);

            assertThat(visibleTo(AsUser.JO_ADMIN))
                    .as("once it belongs to an attaché, it belongs to that attaché's admin")
                    .doesNotContain("scope_probe");
            assertThat(visibleTo(AsUser.SA_ADMIN))
                    .as("and it has moved, not vanished")
                    .contains("scope_probe");
        } finally {
            jdbc.update("DELETE FROM UMSCORE.UserCountries WHERE UserId = ?", id);
            jdbc.update("DELETE FROM AUDITLOG.AuditTrail WHERE TABLE_NAME = N'Users' AND RECORD_ID = ?",
                    String.valueOf(id));
            jdbc.update("DELETE FROM UMSCORE.Users WHERE Id = ?", id);
        }
    }

    @Test
    @DisplayName("reading one staff account by id obeys the same rule as the list")
    void detailAgreesWithList() {
        // getUserDetail is what GET /users/{id} calls; it reaches the database
        // through getUserById, so this covers both.
        assertThat(AsUser.call(AsUser.JO_ADMIN, () -> userService.getUserDetail(AsUser.SUPERADMIN)))
                .as("""
                    List and detail have to agree. A row that is hidden from the list but \
                    readable by id is the leak the list was supposed to close.""")
                .isEmpty();

        assertThat(AsUser.call(AsUser.SUPERADMIN, () -> userService.getUserDetail(AsUser.JO_ADMIN)))
                .as("positive control — the same call succeeds for someone who may make it")
                .isPresent();
    }

    @Test
    @DisplayName("reading one staff account by id names its attachés, as the list does")
    void detailNamesTheAttaches() {
        UmsUser fromList = AsUser.call(AsUser.SUPERADMIN,
                        () -> userService.listUsers(null, null, null, PageRequest.of(0, 500)))
                .getContent().stream()
                .filter(u -> AsUser.JO_ADMIN == u.getId())
                .findFirst()
                .orElseThrow();

        assumeTrue(!fromList.getCountryNames().isEmpty(),
                "jo_admin has no attaché in this database, so there is nothing to compare");

        UmsUser fromDetail = AsUser.call(AsUser.SUPERADMIN, () -> userService.getUserDetail(AsUser.JO_ADMIN))
                .orElseThrow();

        assertThat(fromDetail.getCountryNames())
                .as("""
                    The list shows two attachés and a count; the detail view is where the whole \
                    scope is named. countryNames is @Transient, so a detail read that does not \
                    fill it reports every account, however wide its scope, as awaiting assignment.""")
                .isEqualTo(fromList.getCountryNames());
    }

    @Test
    @DisplayName("the privilege filter and the admins list run at all")
    void privilegeFilteredListsExecute() {
        // These two paths reach the database through coalesce(IsAdmin, false),
        // and SQL Server has no boolean type: a predicate built with isTrue()
        // renders as a bare expression where a condition is expected and the
        // statement fails to parse. Nothing else in this suite goes down either
        // path, which is how it reached a running server. Asserting they execute
        // at all is the point.
        assertThat(AsUser.call(AsUser.SUPERADMIN,
                () -> userService.getAdminUsers(PageRequest.of(0, 100))).getTotalElements())
                .as("admin, superadmin, sa_superadmin, sa_admin and jo_admin all carry IsAdmin")
                .isPositive();

        long elevated = AsUser.call(AsUser.SUPERADMIN,
                () -> userService.listUsers(null, null, "ADMIN", PageRequest.of(0, 100))).getTotalElements();
        long regular = AsUser.call(AsUser.SUPERADMIN,
                () -> userService.listUsers(null, null, "REGULAR", PageRequest.of(0, 100))).getTotalElements();
        Integer total = jdbc.queryForObject(
                "SELECT COUNT(*) FROM UMSCORE.Users WHERE IsDeleted = 0", Integer.class);

        assertThat(elevated + regular)
                .as("the two halves partition the list — neither silently drops rows with a null flag")
                .isEqualTo(total.longValue());
    }

    @Test
    @DisplayName("the privilege filter narrows within the caller's scope, not past it")
    void privilegeFilterStaysInsideScope() {
        long jordanElevated = AsUser.call(AsUser.JO_ADMIN,
                () -> userService.listUsers(null, null, "ADMIN", PageRequest.of(0, 100))).getTotalElements();

        assertThat(jordanElevated)
                .as("a filter is a narrowing of what you may see, never a way around it")
                .isLessThan(AsUser.call(AsUser.SUPERADMIN,
                        () -> userService.listUsers(null, null, "ADMIN", PageRequest.of(0, 100))).getTotalElements());
    }

    @Test
    @DisplayName("staff sessions follow the user who opened them")
    void sessionsFollowTheirUser() {
        long ministrySessions = AsUser.call(AsUser.SUPERADMIN,
                () -> sessionService.listSessions(null, null, PageRequest.of(0, 500))).getTotalElements();
        assumeTrue(ministrySessions > 0, "no sessions recorded");

        long jordanSessions = AsUser.call(AsUser.JO_ADMIN,
                () -> sessionService.listSessions(null, null, PageRequest.of(0, 500))).getTotalElements();

        assertThat(jordanSessions)
                .as("""
                    A session carries an IP address, a device and a user agent — it says where \
                    somebody was. 185 of these belong to `admin` alone, and a Jordan officer has \
                    no business reading them.""")
                .isLessThan(ministrySessions);
    }

    @Test
    @DisplayName("asking for another user's sessions by id does not bypass the list")
    void sessionsByUserIdAreGatedToo() {
        assertThat(AsUser.call(AsUser.JO_ADMIN, () -> sessionService.getSessionsByUserId(AsUser.SUPERADMIN)))
                .as("the id is a path variable, so this is the same leak one URL away")
                .isEmpty();

        assertThat(AsUser.call(AsUser.JO_ADMIN, () -> sessionService.getSessionsByUserId(AsUser.JO_ADMIN)))
                .as("positive control — your own sessions are still readable")
                .isNotNull();
    }
}
