package com.dilaw.mohe.scope;

import com.dilaw.mohe.ums.model.UmsAuditLog;
import com.dilaw.mohe.ums.service.UmsAuditLogService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code UMSCORE.UserActions} — who may read what somebody else did.
 *
 * <p>The staff audit trail is the country scope's back door. A row names its
 * actor and nothing else, so the table looks like one with no tenancy to
 * enforce; but "who granted what to whom, and when" is a list of accounts, and
 * reading it across attachés hands a country officer exactly what
 * {@code StaffAdministrationScopeTest} proves the users screen withholds. These
 * tests assert the two agree.
 *
 * <p>The rule is {@code StaffCountrySpec}'s, so the clauses are the same four,
 * each asserted here through the actor rather than through the account:
 *
 * <ul>
 *   <li>a super admin reads every action;
 *   <li>a country admin reads its own attaché's actions and not the
 *       ministry-wide accounts' — the subset rule again;
 *   <li>Jordan and Saudi read disjoint trails;
 *   <li>everyone reads their own actions, whatever their countries.
 * </ul>
 *
 * <p>Writing is deliberately unscoped and is not tested for scope: an action is
 * recorded whoever takes it, which is the whole point of a log.
 *
 * <p>Fixture rows carry {@link #PROBE_ACTION} and are removed in an
 * {@code @AfterEach}, so the suite leaves the trail as it found it.
 */
@Tag("integration")
@SpringBootTest
class UmsAuditLogScopeTest {

    /** Distinctive enough that the cleanup cannot take a real audit row with it. */
    private static final String PROBE_ACTION = "SCOPE_PROBE_ACTION";

    /** Egypt + Unspecified, not an admin — the caller clause 4 has to carry alone. */
    private static final long HR_MANAGER = 3L;

    @Autowired private UmsAuditLogService auditLogService;
    @Autowired private JdbcTemplate jdbc;

    @BeforeEach
    void recordOneActionPerActor() {
        clean();
        for (long actor : List.of(AsUser.SUPERADMIN, AsUser.ADMIN, AsUser.SA_ADMIN, AsUser.JO_ADMIN, HR_MANAGER)) {
            auditLogService.log(actor, PROBE_ACTION, "USER", actor, null, "probe");
        }
    }

    @AfterEach
    void clean() {
        jdbc.update("DELETE FROM UMSCORE.UserActions WHERE Action = ?", PROBE_ACTION);
    }

    /** The actors whose probe rows {@code userId} can see. */
    private List<Long> actorsVisibleTo(long userId) {
        return AsUser.call(userId, () -> auditLogService.getAuditLogsByAction(PROBE_ACTION, PageRequest.of(0, 50)))
                .getContent().stream().map(UmsAuditLog::getUserId).sorted().toList();
    }

    @Test
    @DisplayName("a super admin reads every account's actions")
    void superAdminReadsEverything() {
        assertThat(actorsVisibleTo(AsUser.SUPERADMIN))
                .as("clause 1 — ministry scope bypasses the actor filter entirely")
                .containsExactlyInAnyOrder(
                        AsUser.SUPERADMIN, AsUser.ADMIN, AsUser.SA_ADMIN, AsUser.JO_ADMIN, HR_MANAGER);
    }

    @Test
    @DisplayName("a country admin does not read the ministry-wide accounts' actions")
    void countryAdminDoesNotReadMinistryActions() {
        assertThat(actorsVisibleTo(AsUser.JO_ADMIN))
                .as("""
                    The same subset rule as the users screen. `admin` and `superadmin` both \
                    cover Jordan, so an intersection rule would list what they did here — and \
                    what the ministry's own administrators do to other attachés is the most \
                    revealing thing in this table.""")
                .doesNotContain(AsUser.ADMIN, AsUser.SUPERADMIN)
                .contains(AsUser.JO_ADMIN);
    }

    @Test
    @DisplayName("Jordan and Saudi read disjoint trails")
    void jordanAndSaudiAreDisjoint() {
        assertThat(actorsVisibleTo(AsUser.JO_ADMIN)).doesNotContain(AsUser.SA_ADMIN);
        assertThat(actorsVisibleTo(AsUser.SA_ADMIN)).doesNotContain(AsUser.JO_ADMIN);
    }

    @Test
    @DisplayName("everyone reads their own actions")
    void everyoneReadsTheirOwn() {
        assertThat(actorsVisibleTo(HR_MANAGER))
                .as("""
                    Clause 4. hr_manager is not an admin and holds one attaché, so its own row \
                    is the only one it is guaranteed. An account that cannot read back what it \
                    just did cannot be held to it.""")
                .contains(HR_MANAGER);
    }

    @Test
    @DisplayName("a non-admin reads no other attaché's actions")
    void nonAdminReadsNoOtherAttache() {
        assertThat(actorsVisibleTo(HR_MANAGER))
                .as("Egypt is not Jordan, not Saudi, and not all eighteen")
                .doesNotContain(AsUser.JO_ADMIN, AsUser.SA_ADMIN, AsUser.ADMIN);
    }

    @Test
    @DisplayName("superadmin's actions are readable by whoever holds its attachés")
    void superAdminActionsFollowItsAssignments() {
        assertThat(actorsVisibleTo(HR_MANAGER))
                .as("""
                    Not an accident and not this change's doing: `superadmin` is ministry-wide \
                    through the IsSuperAdmin flag, but its UserCountries rows say Egypt and \
                    Unspecified — the same two hr_manager holds. Clause 2 compares assignments, \
                    not tiers, so that set is a subset of hr_manager's and the row is visible. \
                    The users screen has always shown `superadmin` to hr_manager for exactly \
                    this reason (StaffAdministrationScopeTest picks hr_manager for the \
                    unassigned case only). Asserted rather than worked around, so that a rule \
                    change tightening it fails here and is seen.""")
                .contains(AsUser.SUPERADMIN);
    }

    @Test
    @DisplayName("asking for one account's actions by id does not bypass the list")
    void byUserIdIsGatedToo() {
        assertThat(AsUser.call(AsUser.JO_ADMIN,
                        () -> auditLogService.getAuditLogsByUserId(AsUser.SA_ADMIN, PageRequest.of(0, 50)))
                        .getContent())
                .as("the id is a path variable, so this is the same leak one URL away")
                .isEmpty();

        assertThat(AsUser.call(AsUser.JO_ADMIN,
                        () -> auditLogService.getAuditLogsByUserId(AsUser.JO_ADMIN, PageRequest.of(0, 50)))
                        .getContent())
                .as("positive control — the same call succeeds for someone who may make it")
                .isNotEmpty();
    }

    @Test
    @DisplayName("the unfiltered list and the date range obey the same rule")
    void listAndDateRangeAgree() {
        long jordanAll = AsUser.call(AsUser.JO_ADMIN,
                () -> auditLogService.getAuditLogs(PageRequest.of(0, 1))).getTotalElements();
        long ministryAll = AsUser.call(AsUser.SUPERADMIN,
                () -> auditLogService.getAuditLogs(PageRequest.of(0, 1))).getTotalElements();

        assertThat(jordanAll)
                .as("every read on this service starts from scoped(), not only the filtered ones")
                .isLessThan(ministryAll);

        LocalDateTime from = LocalDateTime.now().minusMinutes(5);
        LocalDateTime to = LocalDateTime.now().plusMinutes(5);

        assertThat(AsUser.call(AsUser.JO_ADMIN,
                        () -> auditLogService.getAuditLogsByDateRange(from, to, PageRequest.of(0, 200)))
                        .getContent().stream().map(UmsAuditLog::getUserId).toList())
                .as("a date range is a narrowing of what you may see, never a way around it")
                .doesNotContain(AsUser.SA_ADMIN, AsUser.ADMIN);
    }

    @Test
    @DisplayName("an unsorted page still comes back newest first")
    void unsortedPageIsNewestFirst() {
        List<LocalDateTime> times = AsUser.call(AsUser.SUPERADMIN,
                        () -> auditLogService.getAuditLogs(PageRequest.of(0, 20)))
                .getContent().stream().map(UmsAuditLog::getCreatedAt).toList();

        assertThat(times)
                .as("""
                    The derived finders these reads replaced carried OrderByCreatedAtDesc in \
                    their names; a specification has no such half, so the order moves onto the \
                    pageable. An audit screen in insertion order is nearly right, and silently \
                    wrong after any backfill.""")
                .isSortedAccordingTo(Comparator.reverseOrder());
    }

    @Test
    @DisplayName("a caller's own sort is honoured rather than overridden")
    void explicitSortWins() {
        List<LocalDateTime> times = AsUser.call(AsUser.SUPERADMIN,
                        () -> auditLogService.getAuditLogs(
                                PageRequest.of(0, 20, Sort.by(Sort.Direction.ASC, "createdAt"))))
                .getContent().stream().map(UmsAuditLog::getCreatedAt).toList();

        assertThat(times).isSortedAccordingTo(Comparator.naturalOrder());
    }
}
