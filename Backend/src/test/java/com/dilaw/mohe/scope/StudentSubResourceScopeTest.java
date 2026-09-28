package com.dilaw.mohe.scope;

import com.dilaw.mohe.controler.StudentDataController;
import com.dilaw.mohe.service.StudentBooksAllowanceService;
import com.dilaw.mohe.service.StudentInfoRequestService;
import com.dilaw.mohe.service.StudentLoanService;
import com.dilaw.mohe.service.StudentTravelTicketService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * The per-student sub-resources that the country scope had not reached.
 *
 * <p>{@link StudentDetailScopeTest} closed documents, year results and the timeline by
 * routing them through {@code StudentAccessGuard}. Four sibling reads never adopted it —
 * books allowances, loans, travel tickets and information requests by {@code studentId},
 * plus travel tickets by type, which returned every country's — and the finance payment
 * list called its repository straight from the controller. Same class of hole: a small
 * sequential id, guessed, read everything behind it.
 *
 * <p>Every denial is paired with a ministry positive control on the same id, so a guard
 * that refuses everything cannot pass as a working one.
 */
@Tag("integration")
@SpringBootTest
class StudentSubResourceScopeTest {

    @Autowired private StudentBooksAllowanceService booksAllowance;
    @Autowired private StudentLoanService loans;
    @Autowired private StudentTravelTicketService travelTickets;
    @Autowired private StudentInfoRequestService infoRequests;
    @Autowired private StudentDataController studentDataController;
    @Autowired private JdbcTemplate jdbc;

    private static final int EGYPT = 1;

    private Integer anEgyptianStudent() {
        return jdbc.queryForObject(
                "SELECT TOP 1 STUDENT_ID FROM MOHECORE.STUDENT_DATA WHERE STUDY_COUNTRY_IN = ? ORDER BY STUDENT_ID",
                Integer.class, EGYPT);
    }

    /** Ministry reads it; Jordan and Saudi are refused — see {@link ScopeDenial}. */
    private void assertScoped(String what, Supplier<?> read) {
        assertThat(AsUser.call(AsUser.SUPERADMIN, read))
                .as("positive control: the ministry must reach %s, or the denial below proves nothing", what)
                .isNotNull();

        ScopeDenial.assertRefused(what, () -> AsUser.call(AsUser.JO_ADMIN, read));

        assertThatThrownBy(() -> AsUser.call(AsUser.SA_ADMIN, read))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    @DisplayName("books allowances by student carry the student's scope")
    void booksAllowancesByStudent() {
        Integer studentId = anEgyptianStudent();
        assumeTrue(studentId != null, "no Egyptian student seeded");
        assertScoped("books allowances", () -> booksAllowance.getByStudentId(studentId));
    }

    @Test
    @DisplayName("loans by student carry the student's scope")
    void loansByStudent() {
        Integer studentId = anEgyptianStudent();
        assumeTrue(studentId != null, "no Egyptian student seeded");
        assertScoped("loans", () -> loans.getByStudentId(studentId));
    }

    @Test
    @DisplayName("travel tickets by student carry the student's scope")
    void travelTicketsByStudent() {
        Integer studentId = anEgyptianStudent();
        assumeTrue(studentId != null, "no Egyptian student seeded");
        assertScoped("travel tickets", () -> travelTickets.getByStudentId(studentId));
    }

    @Test
    @DisplayName("information requests by student carry the student's scope")
    void infoRequestsByStudent() {
        Integer studentId = anEgyptianStudent();
        assumeTrue(studentId != null, "no Egyptian student seeded");
        assertScoped("information requests", () -> infoRequests.findByStudentId(studentId, PageRequest.of(0, 5)));
    }

    /**
     * Live QA H1 (25 Sep 2026): the staging row has no country column, so row-level
     * security never hid it; Jordan read an Egyptian student's submitted data. It is now
     * reached through the request, which Jordan either cannot see (empty) or is refused.
     */
    @Test
    @DisplayName("information-request staging carries the student's scope")
    void infoRequestStagingIsScoped() {
        Integer requestId = jdbc.query(
                "SELECT TOP 1 s.RequestId FROM MOHECORE.StudentInfoRequestStaging s "
                        + "JOIN MOHECORE.StudentInfoRequest r ON r.Id = s.RequestId "
                        + "JOIN MOHECORE.STUDENT_DATA d ON d.STUDENT_ID = r.StudentId WHERE d.STUDY_COUNTRY_IN = ?",
                rs -> rs.next() ? rs.getInt(1) : null, EGYPT);
        assumeTrue(requestId != null, "no Egyptian information request with staging data");

        assertThat(AsUser.call(AsUser.SUPERADMIN, () -> infoRequests.getStaging(requestId)))
                .as("positive control: the ministry reads the staging data").isPresent();
        java.util.Optional<?> jordan;
        try {
            jordan = AsUser.call(AsUser.JO_ADMIN, () -> infoRequests.getStaging(requestId));
        } catch (ResponseStatusException refused) {
            ScopeDenial.assertRefused("staging data", () -> { throw refused; });
            return;
        }
        assertThat(jordan).as("Jordan must not read an Egyptian student's staging data").isEmpty();
    }

    /** FIX-PLAN N2: a ticket's family members (names, passports) follow the ticket's scope. */
    @Test
    @DisplayName("travel-ticket family members carry the ticket's scope")
    void travelTicketFamilyMembersAreScoped() {
        Integer ticketId = jdbc.query(
                "SELECT TOP 1 t.TRAVEL_TICKET_ID FROM MOHECORE.STUDENT_TRAVEL_TICKET t "
                        + "JOIN MOHECORE.STUDENT_DATA d ON d.STUDENT_ID = t.STUDENT_ID WHERE d.STUDY_COUNTRY_IN = ?",
                rs -> rs.next() ? rs.getInt(1) : null, EGYPT);
        assumeTrue(ticketId != null, "no Egyptian travel ticket");
        assertScoped("travel-ticket family members", () -> travelTickets.getFamilyMembersByTicketId(ticketId));
    }

    /** Live QA H2: a request cannot be raised for a student outside the caller's countries. */
    @Test
    @DisplayName("information requests cannot target another country's student")
    void infoRequestTargetIsScoped() {
        Integer studentId = anEgyptianStudent();
        assumeTrue(studentId != null, "no Egyptian student seeded");
        assertThat(AsUser.call(AsUser.SUPERADMIN,
                () -> infoRequests.resolveTargetStudentIds(java.util.List.of(studentId), false, null, null)))
                .containsExactly(studentId);
        ScopeDenial.assertRefused("an information request for an Egyptian student",
                () -> AsUser.call(AsUser.JO_ADMIN,
                        () -> infoRequests.resolveTargetStudentIds(java.util.List.of(studentId), false, null, null)));
    }

    /**
     * A document upload for a student outside the caller's countries is refused with
     * 403, not reported as a bad request.
     *
     * <p>The handler ran the scope check inside its catch-all, so the refusal came back
     * as {@code 400 "Error uploading document: Access denied…"}. The check is now
     * hoisted out of the try, as the replace and photo handlers already did.
     *
     * <p>Nothing is written: the ministry's positive control sends an empty file, which
     * the handler rejects with 400 <em>after</em> the scope check has passed.
     */
    @Test
    @DisplayName("document upload for another attaché's student is 403, not 400")
    void documentUploadIsScoped() {
        Integer studentId = anEgyptianStudent();
        assumeTrue(studentId != null, "no Egyptian student seeded");
        List<String> canWrite = List.of("STUDENTS.EDIT");
        MockMultipartFile empty = new MockMultipartFile("file", "x.pdf", "application/pdf", new byte[0]);
        Supplier<ResponseEntity<?>> upload = () ->
                studentDataController.uploadStudentDocument(studentId, empty, "x", null, null);

        ResponseEntity<?> ministry = AsUser.callWithAuthorities(AsUser.SUPERADMIN, canWrite, upload);
        assertThat(ministry.getStatusCode())
                .as("positive control: the ministry gets past the scope check and is refused only for the empty file")
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(String.valueOf(ministry.getBody())).doesNotContain("Access denied");

        ScopeDenial.assertRefused("Jordan uploading for an Egyptian student",
                () -> AsUser.callWithAuthorities(AsUser.JO_ADMIN, canWrite, upload));
    }

    @Test
    @DisplayName("travel tickets by type are filtered to the caller's countries, not the whole ministry")
    void travelTicketsByTypeAreScoped() {
        Integer egyptianTickets = jdbc.queryForObject("""
                SELECT COUNT(*) FROM MOHECORE.STUDENT_TRAVEL_TICKET t
                  JOIN MOHECORE.STUDENT_DATA s ON s.STUDENT_ID = t.STUDENT_ID
                 WHERE s.STUDY_COUNTRY_IN = ?
                """, Integer.class, EGYPT);
        assumeTrue(egyptianTickets != null && egyptianTickets > 0, "no Egyptian travel tickets seeded");

        String type = jdbc.queryForObject("""
                SELECT TOP 1 t.TRAVEL_TYPE FROM MOHECORE.STUDENT_TRAVEL_TICKET t
                  JOIN MOHECORE.STUDENT_DATA s ON s.STUDENT_ID = t.STUDENT_ID
                 WHERE s.STUDY_COUNTRY_IN = ?
                """, String.class, EGYPT);

        int ministrySees = AsUser.call(AsUser.SUPERADMIN, () -> travelTickets.getByTravelType(type)).size();
        int jordanSees = AsUser.call(AsUser.JO_ADMIN, () -> travelTickets.getByTravelType(type)).size();

        assertThat(ministrySees).as("positive control: the ministry sees the Egyptian tickets").isGreaterThan(0);
        assertThat(jordanSees).as("Jordan must not see Egyptian tickets").isLessThan(ministrySees);
    }
}
