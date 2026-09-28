package com.dilaw.mohe.controler;

import com.dilaw.mohe.scope.AsUser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * Three finance screens now write nothing on their own.
 *
 * <p>Loans, books allowances and payment recipients used to reach their tables
 * the moment a finance officer pressed Save, with nobody else in the loop. They
 * are behind the maker-checker workflow now, as student data, student finance,
 * wafar, university fees, travel tickets and extensions already were. What that
 * has to mean, and what this holds:
 *
 * <ul>
 *   <li>every write answers {@code PENDING} and <b>changes no row</b>;</li>
 *   <li>a second edit of the same record is refused while the first is
 *       undecided — the workflow's own duplicate check;</li>
 *   <li>but a <b>second Add is not</b>. An INSERT carries no entity id, so two
 *       pending inserts both match on {@code entityId IS NULL}; the workflow
 *       keeps a list of entity types exempt from the duplicate check for exactly
 *       that reason, and a screen missing from it can only ever have one new
 *       record queued at a time.</li>
 * </ul>
 *
 * <p>What an approved change then does to the row is held by the three CRUD
 * tests. Every approval request opened here is removed in {@code @AfterEach},
 * or the next run's edits would be refused as duplicates of this one's.
 */
@Tag("integration")
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
class FinanceApprovalSubmissionTest {

    private static final List<String> FINANCE = List.of("FINANCE.VIEW", "FINANCE.EDIT");

    private static final String LOANS = "/api/v1/studentLoan";
    private static final String BOOKS = "/api/v1/studentBooksAllowance";
    private static final String RECIPIENTS = "/api/v1/studyFinReceivedWhom";

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private JdbcTemplate jdbc;

    /** Entity types this test opens requests for; all of them are cleared afterwards. */
    private static final List<String> TYPES =
            List.of("STUDENT_LOAN", "STUDENT_BOOKS_ALLOWANCE", "STUDY_FIN_RECEIVED_WHOM");

    @AfterEach
    void removeOpenedRequests() {
        String in = "('STUDENT_LOAN','STUDENT_BOOKS_ALLOWANCE','STUDY_FIN_RECEIVED_WHOM')";
        // Children first: the order is explicit rather than left to JPA cascade,
        // so a leftover here cannot depend on mapping details.
        jdbc.update("DELETE FROM ALERTCORE.ApprovalHistory WHERE RequestId IN "
                + "(SELECT Id FROM ALERTCORE.ApprovalRequests WHERE EntityType IN " + in + ")");
        jdbc.update("DELETE FROM ALERTCORE.ApprovalSteps WHERE RequestId IN "
                + "(SELECT Id FROM ALERTCORE.ApprovalRequests WHERE EntityType IN " + in + ")");
        jdbc.update("DELETE FROM ALERTCORE.ApprovalRequests WHERE EntityType IN " + in);
    }

    private MvcResult perform(RequestCall call) {
        Supplier<MvcResult> request = () -> {
            try {
                return call.run();
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        };
        return AsUser.callWithAuthorities(AsUser.SUPERADMIN, FINANCE, request);
    }

    @FunctionalInterface
    private interface RequestCall {
        MvcResult run() throws Exception;
    }

    /** Asserts the answer is the "submitted for approval" envelope, not a saved record. */
    private void assertPending(MvcResult result) throws Exception {
        assertThat(result.getResponse().getStatus())
                .as("answered %s: %s", result.getResponse().getStatus(),
                        result.getResponse().getContentAsString())
                .isEqualTo(200);
        JsonNode body = json.readTree(result.getResponse().getContentAsString());
        assertThat(body.path("status").asText()).isEqualTo("PENDING");
        assertThat(body.path("message").asText()).contains("approval");
    }

    private long countOf(String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM MOHECORE." + table, Long.class);
    }

    private long pendingRequests(String entityType) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM ALERTCORE.ApprovalRequests WHERE EntityType = ? AND Status IN ('PENDING','IN_PROGRESS')",
                Long.class, entityType);
    }

    private Integer aStudent() {
        return jdbc.queryForObject(
                "SELECT TOP 1 STUDENT_ID FROM MOHECORE.STUDENT_DATA WHERE STUDY_COUNTRY_IN IS NOT NULL ORDER BY STUDENT_ID",
                Integer.class);
    }

    private Integer anExistingId(String table, String idColumn) {
        return jdbc.queryForObject(
                "SELECT TOP 1 " + idColumn + " FROM MOHECORE." + table + " ORDER BY " + idColumn, Integer.class);
    }

    private String loanBody(Integer studentId) {
        return """
                {"studentId":%d,"studyYear":"2024-2025","moheQuarterId":null,
                 "loanAmount":4500,"currencyId":1,"loanStatus":0,"repaymentNotes":"ZT-submit"}
                """.formatted(studentId);
    }

    private String booksBody(Integer studentId) {
        return """
                {"studentId":%d,"studyYear":"2024-2025","moheQuarterId":null,
                 "allowanceAmount":375,"payStatus":0,"receiptDate":null,"notes":"ZT-submit"}
                """.formatted(studentId);
    }

    private String recipientBody(Integer studentId) {
        return """
                {"finReceivedName":"ZT-submit","studentForDelegationId":%d,
                 "finRecievedYear":2024,"finRecievedMonth":6}
                """.formatted(studentId);
    }

    // ── Nothing is written ───────────────────────────────────────────────────

    @Test
    @DisplayName("adding a loan opens a request and writes no row")
    void addingALoanWritesNothing() throws Exception {
        long before = countOf("STUDENT_LOAN");

        assertPending(perform(() -> mvc.perform(post(LOANS + "/")
                .contentType(MediaType.APPLICATION_JSON).content(loanBody(aStudent()))).andReturn()));

        assertThat(countOf("STUDENT_LOAN")).as("no row was written").isEqualTo(before);
        assertThat(pendingRequests("STUDENT_LOAN")).isEqualTo(1);
    }

    @Test
    @DisplayName("adding a books allowance opens a request and writes no row")
    void addingABooksAllowanceWritesNothing() throws Exception {
        long before = countOf("STUDENT_BOOKS_ALLOWANCE");

        assertPending(perform(() -> mvc.perform(post(BOOKS + "/")
                .contentType(MediaType.APPLICATION_JSON).content(booksBody(aStudent()))).andReturn()));

        assertThat(countOf("STUDENT_BOOKS_ALLOWANCE")).as("no row was written").isEqualTo(before);
        assertThat(pendingRequests("STUDENT_BOOKS_ALLOWANCE")).isEqualTo(1);
    }

    @Test
    @DisplayName("adding a finance recipient opens a request and writes no row")
    void addingARecipientWritesNothing() throws Exception {
        long before = countOf("STUDY_FIN_RECEIVED_WHOM");

        assertPending(perform(() -> mvc.perform(post(RECIPIENTS + "/")
                .contentType(MediaType.APPLICATION_JSON).content(recipientBody(aStudent()))).andReturn()));

        assertThat(countOf("STUDY_FIN_RECEIVED_WHOM")).as("no row was written").isEqualTo(before);
        assertThat(pendingRequests("STUDY_FIN_RECEIVED_WHOM")).isEqualTo(1);
    }

    @Test
    @DisplayName("an edit changes nothing until it is approved")
    void editingChangesNothing() throws Exception {
        Integer id = anExistingId("STUDENT_LOAN", "LOAN_ID");
        Integer amountBefore = jdbc.queryForObject(
                "SELECT LOAN_AMOUNT FROM MOHECORE.STUDENT_LOAN WHERE LOAN_ID = ?", Integer.class, id);

        assertPending(perform(() -> mvc.perform(put(LOANS + "/" + id)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"loanAmount\":999999}")).andReturn()));

        assertThat(jdbc.queryForObject(
                "SELECT LOAN_AMOUNT FROM MOHECORE.STUDENT_LOAN WHERE LOAN_ID = ?", Integer.class, id))
                .as("the amount is untouched until an approver signs off")
                .isEqualTo(amountBefore);
    }

    @Test
    @DisplayName("a delete leaves the row in place until it is approved")
    void deletingLeavesTheRow() throws Exception {
        Integer id = anExistingId("STUDENT_BOOKS_ALLOWANCE", "BOOKS_ALLOW_ID");
        long before = countOf("STUDENT_BOOKS_ALLOWANCE");

        assertPending(perform(() -> mvc.perform(delete(BOOKS + "/" + id)).andReturn()));

        assertThat(countOf("STUDENT_BOOKS_ALLOWANCE")).isEqualTo(before);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM MOHECORE.STUDENT_BOOKS_ALLOWANCE WHERE BOOKS_ALLOW_ID = ?", Long.class, id))
                .isEqualTo(1);
    }

    // ── The duplicate check, both ways ───────────────────────────────────────

    @Test
    @DisplayName("a second edit of the same record is refused while the first is undecided")
    void secondEditOfOneRecordIsRefused() throws Exception {
        Integer id = anExistingId("STUDENT_LOAN", "LOAN_ID");

        assertPending(perform(() -> mvc.perform(put(LOANS + "/" + id)
                .contentType(MediaType.APPLICATION_JSON).content("{\"loanStatus\":1}")).andReturn()));

        MvcResult second = perform(() -> mvc.perform(put(LOANS + "/" + id)
                .contentType(MediaType.APPLICATION_JSON).content("{\"loanStatus\":2}")).andReturn());

        // W4-08: one answer on every screen — 409, PENDING_EXISTS and the request to open.
        assertThat(second.getResponse().getStatus()).isEqualTo(409);
        assertThat(second.getResponse().getContentAsString())
                .contains("\"code\":\"PENDING_EXISTS\"").contains("\"requestId\":")
                .contains("awaiting approval");
        assertThat(pendingRequests("STUDENT_LOAN")).as("still just the one").isEqualTo(1);
    }

    @Test
    @DisplayName("two new records can be queued at once on each of the three screens")
    void secondAddIsNotRefused() throws Exception {
        // An INSERT has no entity id, so both pending requests match on
        // entityId IS NULL. Without these entity types on the workflow's
        // skipDupCheck list, the second Add is refused as a duplicate of the
        // first — a finance officer could queue one new record and no more.
        Integer studentId = aStudent();

        for (String[] screen : new String[][] {
                { LOANS, loanBody(studentId), "STUDENT_LOAN" },
                { BOOKS, booksBody(studentId), "STUDENT_BOOKS_ALLOWANCE" },
                { RECIPIENTS, recipientBody(studentId), "STUDY_FIN_RECEIVED_WHOM" } }) {

            assertPending(perform(() -> mvc.perform(post(screen[0] + "/")
                    .contentType(MediaType.APPLICATION_JSON).content(screen[1])).andReturn()));
            assertPending(perform(() -> mvc.perform(post(screen[0] + "/")
                    .contentType(MediaType.APPLICATION_JSON).content(screen[1])).andReturn()));

            assertThat(pendingRequests(screen[2]))
                    .as("%s: both new records are queued", screen[2])
                    .isEqualTo(2);
        }
    }

    // ── The requests carry what an approver needs ────────────────────────────

    @Test
    @DisplayName("a request is filed under the student's country, and an edit stores only what was sent")
    void requestsCarryCountryAndThePartialBody() throws Exception {
        Integer id = anExistingId("STUDENT_LOAN", "LOAN_ID");
        Integer studentId = jdbc.queryForObject(
                "SELECT STUDENT_ID FROM MOHECORE.STUDENT_LOAN WHERE LOAN_ID = ?", Integer.class, id);
        Integer expectedCountry = jdbc.queryForObject(
                "SELECT STUDY_COUNTRY_IN FROM MOHECORE.STUDENT_DATA WHERE STUDENT_ID = ?",
                Integer.class, studentId);

        perform(() -> mvc.perform(put(LOANS + "/" + id)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"loanStatus\":3}")).andReturn());

        var row = jdbc.queryForMap(
                "SELECT CountryId, RequestData, OriginalData FROM ALERTCORE.ApprovalRequests "
                        + "WHERE EntityType = 'STUDENT_LOAN' AND EntityId = ?", String.valueOf(id));

        // The country decides which attaché's approvers see the request at all.
        assertThat(row.get("CountryId")).as("CountryId").isEqualTo(expectedCountry);

        // The stored payload is the dialog's own tree. Keeping it partial is what
        // lets the executor tell a column that was omitted from one that was
        // cleared — and it is also why the approver's diff shows one field, not
        // every column of the row.
        JsonNode stored = json.readTree((String) row.get("RequestData"));
        assertThat(stored.has("loanStatus")).isTrue();
        assertThat(stored.has("repaymentNotes")).as("a field the dialog never sent").isFalse();
        assertThat((String) row.get("OriginalData")).as("the row as it stands, for the diff").isNotNull();
    }

    @Test
    @DisplayName("every entity type this test uses has an approval config of its own")
    void eachScreenHasItsOwnConfig() {
        // Without a config the workflow silently falls back to DEFAULT, which
        // would work — and would leave the approvals screen unable to name the
        // request, since approval-entity-type is served from ApprovalConfigs.
        for (String type : TYPES) {
            assertThat(jdbc.queryForObject(
                    "SELECT COUNT(*) FROM ALERTCORE.ApprovalConfigs WHERE EntityType = ? AND IsActive = 1",
                    Long.class, type))
                    .as("approval config for %s (see docs/scripts/adhoc/seed_*_approval_config.sql)", type)
                    .isEqualTo(1);
        }
    }
}
