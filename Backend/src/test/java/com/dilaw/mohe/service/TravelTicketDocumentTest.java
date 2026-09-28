package com.dilaw.mohe.service;

import com.dilaw.mohe.config.tenant.TenantContext;
import com.dilaw.mohe.model.StudentDocument;
import com.dilaw.mohe.model.StudentTravelTicket;
import com.dilaw.mohe.repository.StudentDocumentRepository;
import com.dilaw.mohe.scope.AsUser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * A travel ticket's PDF (review T3): the ticket may only name its own student's
 * ticket document, and that document stays hidden until the ticket is written.
 * Runs in a transaction it rolls back itself; the document rows have no file.
 * Needs V116.
 */
@Tag("integration")
@SpringBootTest
class TravelTicketDocumentTest {

    @Autowired private StudentTravelTicketService ticketService;
    @Autowired private StudentDocumentRepository documents;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager txManager;

    private StudentDocument document(int studentId, String type, boolean active) {
        StudentDocument d = new StudentDocument(studentId, "T3 test", type,
                "test/t3-not-on-disk.pdf", "ticket.pdf", 10L);
        d.setIsActive(active);
        return documents.saveAndFlush(d);
    }

    @Test
    @DisplayName("a ticket attaches only its own student's ticket document, and makes it visible")
    void ticketDocumentRules() {
        TenantContext.runAsSystem(() -> new TransactionTemplate(txManager).execute(status -> {
            status.setRollbackOnly();
            List<Integer> students = jdbc.queryForList(
                    "SELECT TOP 2 STUDENT_ID FROM MOHECORE.STUDENT_DATA ORDER BY STUDENT_ID", Integer.class);
            int mine = students.get(0);
            int other = students.get(1);

            AsUser.run(AsUser.SUPERADMIN, () -> {
                StudentDocument ticketPdf = document(mine, StudentTravelTicketService.TICKET_DOCUMENT_TYPE, false);
                StudentDocument othersPdf = document(other, StudentTravelTicketService.TICKET_DOCUMENT_TYPE, false);
                StudentDocument passport = document(mine, "جواز سفر", true);

                assertThatThrownBy(() -> ticketService.requireTicketDocument(mine, othersPdf.getDocumentId()))
                        .as("another student's ticket").isInstanceOf(ResponseStatusException.class);
                assertThatThrownBy(() -> ticketService.requireTicketDocument(mine, passport.getDocumentId()))
                        .as("a document that is not a ticket").isInstanceOf(ResponseStatusException.class);

                StudentTravelTicket ticket = new StudentTravelTicket();
                ticket.setStudentId(mine);
                ticket.setTravelType("ALONE");
                ticket.setDocumentId(ticketPdf.getDocumentId());
                StudentTravelTicket saved = ticketService.create(ticket);

                assertThat(saved.getDocumentId()).isEqualTo(ticketPdf.getDocumentId());
                assertThat(documents.findById(ticketPdf.getDocumentId()).orElseThrow().getIsActive())
                        .as("the PDF joins the student's documents once the ticket is written").isTrue();
            });
            return null;
        }));
    }
}
