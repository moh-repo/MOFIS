package com.dilaw.mohe.scope;

import com.dilaw.mohe.approval.controler.ApprovalController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.List;

/**
 * {@code GET /approvals/my-requests} — identity, not country.
 *
 * <p>The endpoint took a {@code userId} query parameter and passed it straight to
 * the repository. Any authenticated user holding the {@code APPROVALS} permission
 * could read anyone else's requests by changing a number in the URL, and a request
 * carries {@code RequestData} — the full proposed state of a student record,
 * including whatever personal detail was being changed.
 *
 * <p>This is horizontal privilege escalation rather than a country leak, and
 * country scope would not have caught it: two officers at the same attaché are in
 * the same scope. It is here because it lives on one of the ten pages, and because
 * it was one query string away.
 *
 * <p>Read-only.
 */
@Tag("integration")
@SpringBootTest
class ApprovalMyRequestsIdentityTest {

    @Autowired private ApprovalController controller;
    @Autowired private JdbcTemplate jdbc;

    /** Enough to pass the controller's @PreAuthorize, so identity is what is being tested. */
    private static final List<String> CAN_VIEW_APPROVALS = List.of("APPROVALS.VIEW");

    private static <T> T as(long userId, java.util.function.Supplier<T> body) {
        return AsUser.callWithAuthorities(userId, CAN_VIEW_APPROVALS, body);
    }

    @Test
    @DisplayName("asking for another user's requests is refused, not quietly answered")
    void anotherUsersRequestsAreRefused() {
        Long someoneElse = jdbc.queryForObject(
                "SELECT TOP 1 RequestedBy FROM ALERTCORE.ApprovalRequests WHERE RequestedBy <> ? AND RequestedBy IS NOT NULL",
                Long.class, AsUser.JO_ADMIN);
        assumeTrue(someoneElse != null, "everything was requested by the same user");

        assertThatThrownBy(() -> as(AsUser.JO_ADMIN,
                () -> controller.getMyRequests(someoneElse, 0, 10, null, null, null)))
                .as("""
                    Refused rather than silently answered with the caller's own rows. A silent \
                    substitution looks like the feature working and hides that somebody tried.""")
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("403");
    }

    @Test
    @DisplayName("the caller's own id is accepted, and so is omitting it")
    void ownRequestsStillWork() {
        long withParameter = as(AsUser.JO_ADMIN,
                () -> controller.getMyRequests(AsUser.JO_ADMIN, 0, 10, null, null, null))
                .getBody().getTotalElements();

        long withoutParameter = as(AsUser.JO_ADMIN,
                () -> controller.getMyRequests(null, 0, 10, null, null, null))
                .getBody().getTotalElements();

        assertThat(withParameter)
                .as("the parameter is still accepted so existing clients keep working, but only when it agrees with the token")
                .isEqualTo(withoutParameter);
    }

    @Test
    @DisplayName("the rows returned really are the caller's own")
    void resultsBelongToTheCaller() {
        Long prolific = jdbc.queryForObject(
                "SELECT TOP 1 RequestedBy FROM ALERTCORE.ApprovalRequests WHERE RequestedBy IS NOT NULL "
              + "GROUP BY RequestedBy ORDER BY COUNT(*) DESC", Long.class);
        assumeTrue(prolific != null, "no requests with a requester");

        var page = as(prolific, () -> controller.getMyRequests(null, 0, 50, null, null, null)).getBody();

        assumeTrue(page.getTotalElements() > 0, "that user has no requests");
        assertThat(page.getContent())
                .as("every row must have been raised by the caller and nobody else")
                .allSatisfy(request -> assertThat(request.getRequestedBy()).isEqualTo(prolific));
    }

    @Test
    @DisplayName("my own request at an attaché I no longer serve is not mine to read")
    void countryScopeAppliesToOnesOwnRequests() {
        Long configId = jdbc.queryForObject(
                "SELECT TOP 1 Id FROM ALERTCORE.ApprovalConfigs", Long.class);
        assumeTrue(configId != null, "no approval configs");

        // A request raised by jo_admin but belonging to Egypt. Contrived, and it is
        // exactly the case that separates the two filters: identity says it is
        // theirs, scope says it is Egypt's. Reassigning an officer between attachés
        // produces it for real.
        jdbc.update("""
                INSERT INTO ALERTCORE.ApprovalRequests
                    (ConfigId, RequestType, EntityType, RequestData, CurrentLevel, Status,
                     RequestedBy, RequestedAt, CountryId)
                VALUES (?, N'UPDATE', N'STUDENT_DATA', N'{"scopeTest":true}', 1, N'PENDING', ?, SYSUTCDATETIME(), 1)
                """, configId, AsUser.JO_ADMIN);
        Long id = jdbc.queryForObject("SELECT MAX(Id) FROM ALERTCORE.ApprovalRequests", Long.class);

        try {
            var mine = as(AsUser.JO_ADMIN, () -> controller.getMyRequests(null, 0, 50, null, null, null)).getBody();

            assertThat(mine.getContent()).extracting(r -> r.getId())
                    .as("""
                        Identity alone would return it — jo_admin did raise it. Country scope is a \
                        second filter, and the RequestData inside belongs to Egypt's students.""")
                    .doesNotContain(id);

            // And it has not simply vanished: the attaché that owns it still has it.
            assertThat(jdbc.queryForObject(
                    "SELECT COUNT(*) FROM ALERTCORE.ApprovalRequests WHERE Id = ?", Integer.class, id))
                    .as("hidden from that reader, not deleted")
                    .isEqualTo(1);
        } finally {
            jdbc.update("DELETE FROM AUDITLOG.AuditTrail WHERE TABLE_NAME = N'ApprovalRequests' AND RECORD_ID = ?",
                    String.valueOf(id));
            jdbc.update("DELETE FROM ALERTCORE.ApprovalRequests WHERE Id = ?", id);
        }
    }

    @Test
    @DisplayName("an unauthenticated call is rejected rather than defaulted")
    void unauthenticatedIsRejected() {
        // Two gates stand in front of this, and which one fires is not the point:
        // the controller's @PreAuthorize refuses an anonymous caller before the
        // method body runs, and the body itself refuses a request with no user id.
        // What matters is that neither picks a default user.
        assertThatThrownBy(() -> controller.getMyRequests(null, 0, 10, null, null, null))
                .as("there is no sensible default user here, and picking one would be worse than failing")
                .isInstanceOf(Exception.class);

        // An authenticated id that belongs to nobody gets nothing. Worth stating,
        // because the failure mode that would matter is the opposite one — a
        // filter that matches no user falling through to matching every user.
        assertThat(AsUser.callWithAuthorities(-1L, CAN_VIEW_APPROVALS,
                        () -> controller.getMyRequests(null, 0, 10, null, null, null))
                        .getBody().getTotalElements())
                .as("no such user, so no requests — not everybody's")
                .isZero();
    }
}
