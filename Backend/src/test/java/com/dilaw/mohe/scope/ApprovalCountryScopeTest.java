package com.dilaw.mohe.scope;

import com.dilaw.mohe.approval.model.ApprovalRequest;
import com.dilaw.mohe.approval.service.ApprovalWorkflowService;
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
 * Approvals: the lists were scoped, the detail paths were not.
 *
 * <p>{@code /pending} and {@code /completed} have consulted the caller's scope for
 * some time. {@code getRequestById} carried a comment saying access was
 * deliberately not restricted — and every screen that opens a request, diffs it,
 * or downloads a file staged with it goes through that one method. A request's
 * {@code RequestData} is the full proposed state of a student record, so reading
 * another attaché's needed only its id.
 *
 * <p>The other half of this is {@code ApprovalRequestRepository}, where the
 * country predicate used to read {@code (countryId IS NULL OR countryId IN ...)}.
 * That admitted every request with no country to every officer. V046 derived a
 * country for all nine such rows and the create path now stamps the requester's
 * own attaché, so the predicate no longer needs the escape hatch — and the lists
 * and the detail path have to agree, or a request appears in the list and 404s
 * when opened.
 *
 * <p>Read-only.
 */
@Tag("integration")
@SpringBootTest
class ApprovalCountryScopeTest {

    @Autowired private ApprovalWorkflowService workflowService;
    @Autowired private JdbcTemplate jdbc;

    private Long egyptianRequestId() {
        return jdbc.queryForObject(
                "SELECT TOP 1 Id FROM ALERTCORE.ApprovalRequests WHERE CountryId = 1 ORDER BY Id", Long.class);
    }

    @Test
    @DisplayName("a request cannot be opened by id from another attaché")
    void getByIdIsScoped() {
        Long id = egyptianRequestId();
        assumeTrue(id != null, "no Egyptian approval requests");

        assertThat(AsUser.call(AsUser.SUPERADMIN, () -> workflowService.getRequestById(id)))
                .as("it exists, so the refusal below is about scope")
                .isPresent();

        assertThat(AsUser.call(AsUser.JO_ADMIN, () -> workflowService.getRequestById(id)))
                .as("""
                    RequestData holds what the student's record was about to become. This is \
                    also the gate for /{id}/changes and /{id}/staged/{fileName}, so the staged \
                    file download is closed by the same line.""")
                .isEmpty();

        assertThat(AsUser.call(AsUser.ADMIN, () -> workflowService.getRequestById(id)))
                .as("positive control: an officer whose scope covers Egypt still gets it")
                .isPresent();
    }

    @Test
    @DisplayName("the decision history follows the request it belongs to")
    void historyIsScoped() {
        Long id = egyptianRequestId();
        assumeTrue(id != null, "no Egyptian approval requests");
        assumeTrue(!AsUser.call(AsUser.SUPERADMIN, () -> workflowService.getRequestHistory(id)).isEmpty(),
                "that request has no history rows");

        assertThat(AsUser.call(AsUser.JO_ADMIN, () -> workflowService.getRequestHistory(id)))
                .as("who approved another attaché's change, when, and with what comment")
                .isEmpty();
    }

    @Test
    @DisplayName("requests for one record are scoped, and so is the pending badge")
    void entityLookupsAreScoped() {
        String entityType = jdbc.queryForObject(
                "SELECT TOP 1 EntityType FROM ALERTCORE.ApprovalRequests WHERE CountryId = 1", String.class);
        assumeTrue(entityType != null, "no Egyptian approval requests");

        assertThat(AsUser.call(AsUser.SUPERADMIN, () -> workflowService.getAllByEntityType(entityType)))
                .isNotEmpty();
        assertThat(AsUser.call(AsUser.JO_ADMIN, () -> workflowService.getAllByEntityType(entityType)))
                .isEmpty();

        assertThat(AsUser.call(AsUser.JO_ADMIN, () -> workflowService.getPendingEntityIds(entityType)))
                .as("""
                    This drives the "has a pending change" marker on list screens. Unscoped, it \
                    told an officer which of another attaché's records were mid-edit.""")
                .isEmpty();
    }

    @Test
    @DisplayName("every request a list shows can be opened by the same user")
    void listAndDetailAgree() {
        // Deliberately the completed history rather than the pending queue: nothing
        // is pending in this database, so a pending-based check would pass on an
        // empty list and prove nothing. The completed list holds 44 rows.
        //
        // `admin` is the right reader here — not ministry, so it runs the scoped
        // query, with assignments covering every country.
        List<ApprovalRequest> listed = AsUser.call(AsUser.ADMIN,
                () -> workflowService.getCompletedHistory(PageRequest.of(0, 200)).getContent());
        assumeTrue(!listed.isEmpty(), "no completed requests");

        // The failure this guards against is a mismatch between the list predicate
        // and the detail check: a row that appears in the list and then 404s when
        // clicked. That is exactly what would happen if the list still admitted a
        // NULL country and the detail path did not.
        assertThat(listed).allSatisfy(request ->
                assertThat(AsUser.call(AsUser.ADMIN, () -> workflowService.getRequestById(request.getId())))
                        .as("request %d is listed, so the same user must be able to open it", request.getId())
                        .isPresent());
    }

    @Test
    @DisplayName("the completed history is scoped, and the two countries are disjoint")
    void completedHistoryIsScoped() {
        long everyCountry = AsUser.call(AsUser.ADMIN,
                () -> workflowService.getCompletedHistory(PageRequest.of(0, 1)).getTotalElements());
        assumeTrue(everyCountry > 0, "no completed requests");

        long jordan = AsUser.call(AsUser.JO_ADMIN,
                () -> workflowService.getCompletedHistory(PageRequest.of(0, 1)).getTotalElements());
        long saudi = AsUser.call(AsUser.SA_ADMIN,
                () -> workflowService.getCompletedHistory(PageRequest.of(0, 1)).getTotalElements());

        assertThat(jordan).as("Jordan has no requests of its own here").isZero();
        assertThat(saudi).as("nor does Saudi Arabia").isZero();
        assertThat(everyCountry)
                .as("but an officer holding all countries reads them all, so the filter permits as well as denies")
                .isGreaterThan(jordan + saudi);
    }

    @Test
    @DisplayName("the pending count matches what the caller could actually open")
    void pendingCountIsScoped() {
        /*
         * Every request on the development database is EXECUTED or REJECTED, so
         * until the third revision pass (27 Aug 2026) this test assumed itself away
         * on "nothing pending" and the badge was never checked. It now raises one
         * PENDING Egyptian request itself — a raw row, inserted as SYSTEM so it
         * passes the tenant block predicate when the policy is on — and removes
         * it, and the audit row its insert trigger wrote, afterwards.
         */
        Long fixtureId = com.dilaw.mohe.config.tenant.TenantContext.runAsSystem(() -> jdbc.queryForObject("""
                INSERT INTO ALERTCORE.ApprovalRequests
                       (ConfigId, RequestType, EntityType, EntityId, RequestData, CurrentLevel, Status,
                        RequestedBy, RequestedAt, CountryId)
                VALUES (1, 'UPDATE', 'STUDENT_DATA', 'scope-test', '{"scopeTest":true}', 1, 'PENDING',
                        ?, SYSUTCDATETIME(), 1);
                SELECT CAST(SCOPE_IDENTITY() AS BIGINT);
                """, Long.class, AsUser.SUPERADMIN));
        assertThat(fixtureId).as("the fixture must have been raised").isNotNull();

        try {
            long ministryCount = AsUser.call(AsUser.SUPERADMIN, () -> workflowService.countPending());
            long jordanCount = AsUser.call(AsUser.JO_ADMIN, () -> workflowService.countPending());

            assertThat(ministryCount).as("positive control: the ministry sees the Egyptian request").isGreaterThan(0);
            assertThat(jordanCount)
                    .as("a badge counting the ministry's pending work is not just wrong, it is a hint")
                    .isLessThan(ministryCount);
            assertThat(jordanCount)
                    .as("and it must equal what the list actually returns")
                    .isEqualTo(AsUser.call(AsUser.JO_ADMIN,
                            () -> workflowService.getAllPending(PageRequest.of(0, 1)).getTotalElements()));
        } finally {
            com.dilaw.mohe.config.tenant.TenantContext.runAsSystem(() -> {
                jdbc.update("DELETE FROM ALERTCORE.ApprovalSteps WHERE RequestId = ?", fixtureId);
                jdbc.update("DELETE FROM ALERTCORE.ApprovalHistory WHERE RequestId = ?", fixtureId);
                jdbc.update("DELETE FROM ALERTCORE.ApprovalRequests WHERE Id = ?", fixtureId);
                jdbc.update("DELETE FROM AUDITLOG.AuditTrail WHERE TABLE_NAME = 'ApprovalRequests' AND RECORD_ID = ?",
                        String.valueOf(fixtureId));
            });
        }
    }

    @Test
    @DisplayName("no request is left without a country for the lists to skip")
    void everyRequestHasACountry() {
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM ALERTCORE.ApprovalRequests WHERE CountryId IS NULL", Integer.class))
                .as("""
                    The country predicate no longer lets a NULL through, so a request without one \
                    is invisible to every officer including the person who raised it. V046 filled \
                    the nine that existed; createRequestInner stamps the requester's attaché from \
                    here on.""")
                .isZero();
    }

    /**
     * {@code GET /api/v1/approvals} answered 500 for every caller whose page held a
     * row: {@code ApprovalRequest.config} is LAZY and Jackson serialises it, and this
     * was the one listing whose queries had no {@code LEFT JOIN FETCH} — the ministry
     * branch used the inherited {@code findAll}, the scoped branch a bare query.
     *
     * <p>The assertion reads {@code config} <em>after</em> the service call has
     * returned, which is where the message converter reads it: outside the
     * transaction, with no session to initialise a proxy. Asserting on the page
     * alone would pass against the broken code, because the failure is in the
     * serialisation that follows, not in the query.
     *
     * <p>Jordan is the control that explains why this went unnoticed: an empty page
     * has no proxy to touch, so the endpoint looked healthy to an attaché with no
     * requests while the ministry got a 500.
     */
    @Test
    @DisplayName("the all-requests listing initialises config, for ministry and for an attaché")
    void getAllRequestsCarriesItsConfig() {
        List<ApprovalRequest> ministry = AsUser.call(AsUser.SUPERADMIN,
                () -> workflowService.getAllRequests(PageRequest.of(0, 20)).getContent());
        assumeTrue(!ministry.isEmpty(), "no approval requests at all");

        assertThat(ministry).allSatisfy(request ->
                assertThat(request.getConfig()).isNotNull()
                        .extracting(c -> c.getEntityType())
                        .as("request %d: reading config outside the session is what the "
                                + "message converter does", request.getId())
                        .isNotNull());

        List<ApprovalRequest> egypt = AsUser.call(AsUser.ADMIN,
                () -> workflowService.getAllRequests(PageRequest.of(0, 20)).getContent());
        assertThat(egypt).as("the scoped branch runs its own query and must fetch too").isNotEmpty();
        assertThat(egypt).allSatisfy(request ->
                assertThat(request.getConfig()).isNotNull()
                        .extracting(c -> c.getEntityType()).isNotNull());

        assertThat(AsUser.call(AsUser.JO_ADMIN,
                () -> workflowService.getAllRequests(PageRequest.of(0, 20)).getContent()))
                .as("an empty page serialises nothing, which is why the 500 hid here")
                .isEmpty();
    }
}
