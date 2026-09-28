package com.dilaw.mohe.service;

import com.dilaw.mohe.config.tenant.TenantContext;
import com.dilaw.mohe.model.StudentDocument;
import com.dilaw.mohe.model.StudentTravelTicket;
import com.dilaw.mohe.repository.StudentDocumentRepository;
import com.dilaw.mohe.repository.StudentTravelTicketRepository;
import com.dilaw.mohe.scope.AsUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Review T3, plan W4-09: a travel-ticket PDF that no ticket and no open request
 * names — a rejected ticket's, or one an edit replaced — is removed with its file.
 * The fixture is committed (the clean-up runs in its own transaction) and removed
 * afterwards.
 */
@Tag("integration")
@SpringBootTest
class TravelTicketDocumentCleanupTest {

    @Autowired private TravelTicketDocumentCleanup cleanup;
    @Autowired private StudentDocumentRepository documents;
    @Autowired private StudentTravelTicketRepository tickets;
    @Autowired private StudentFileStorageService storage;
    @Autowired private JdbcTemplate jdbc;

    private final List<Integer> documentIds = new ArrayList<>();
    private final List<Integer> ticketIds = new ArrayList<>();
    private final List<Path> files = new ArrayList<>();

    @AfterEach
    void remove() {
        TenantContext.runAsSystem(() -> {
            ticketIds.forEach(id -> jdbc.update("DELETE FROM MOHECORE.STUDENT_TRAVEL_TICKET WHERE TRAVEL_TICKET_ID = ?", id));
            documentIds.forEach(id -> jdbc.update("DELETE FROM MOHECORE.STUDENT_DOCUMENTS WHERE DOCUMENT_ID = ?", id));
        });
        for (Path f : files) {
            try {
                Files.deleteIfExists(f);
            } catch (Exception ignored) {
                // best effort
            }
        }
        try {
            Files.deleteIfExists(storage.resolveStored("w409-cleanup-test"));
        } catch (Exception ignored) {
            // not empty, or never made
        }
    }

    private int aStudent() {
        return TenantContext.runAsSystem(() -> jdbc.queryForObject(
                "SELECT TOP 1 STUDENT_ID FROM MOHECORE.STUDENT_DATA ORDER BY STUDENT_ID", Integer.class));
    }

    /** A hidden ticket PDF with a real file under the storage root. */
    private StudentDocument ticketPdf(int studentId) throws Exception {
        String rel = "w409-cleanup-test/" + UUID.randomUUID() + ".pdf";
        Path file = storage.resolveStored(rel);
        Files.createDirectories(file.getParent());
        Files.write(file, new byte[] {1, 2, 3});
        files.add(file);
        StudentDocument d = new StudentDocument(studentId, "W4-09 test", StudentTravelTicketService.TICKET_DOCUMENT_TYPE,
                rel, "ticket.pdf", 3L);
        d.setIsActive(false);
        StudentDocument saved = AsUser.call(AsUser.SUPERADMIN,
                () -> TenantContext.runAsSystem(() -> documents.saveAndFlush(d)));
        documentIds.add(saved.getDocumentId());
        return saved;
    }

    @Test
    @DisplayName("a ticket PDF nothing names is removed with its file")
    void unusedPdfIsRemoved() throws Exception {
        StudentDocument pdf = ticketPdf(aStudent());
        Path file = storage.resolveStored(pdf.getFilePath());

        boolean removed = AsUser.call(AsUser.SUPERADMIN,
                () -> TenantContext.runAsSystem(() -> cleanup.removeIfUnused(pdf.getDocumentId())));

        assertThat(removed).isTrue();
        assertThat(TenantContext.runAsSystem(() -> documents.findById(pdf.getDocumentId()))).isEmpty();
        assertThat(Files.exists(file)).as("the file goes too").isFalse();
    }

    @Test
    @DisplayName("a PDF a ticket still names is kept")
    void pdfOfATicketIsKept() throws Exception {
        int student = aStudent();
        StudentDocument pdf = ticketPdf(student);
        StudentTravelTicket ticket = new StudentTravelTicket();
        ticket.setStudentId(student);
        ticket.setTravelType("ALONE");
        ticket.setDocumentId(pdf.getDocumentId());
        StudentTravelTicket saved = AsUser.call(AsUser.SUPERADMIN,
                () -> TenantContext.runAsSystem(() -> tickets.saveAndFlush(ticket)));
        ticketIds.add(saved.getTravelTicketId());

        boolean removed = AsUser.call(AsUser.SUPERADMIN,
                () -> TenantContext.runAsSystem(() -> cleanup.removeIfUnused(pdf.getDocumentId())));

        assertThat(removed).isFalse();
        assertThat(Files.exists(storage.resolveStored(pdf.getFilePath()))).isTrue();
    }

    @Test
    @DisplayName("a document that is not a ticket PDF is never touched")
    void otherDocumentsAreLeftAlone() {
        StudentDocument passport = new StudentDocument(aStudent(), "W4-09 passport", "جواز سفر",
                "w409-cleanup-test/not-on-disk.pdf", "p.pdf", 1L);
        StudentDocument saved = AsUser.call(AsUser.SUPERADMIN,
                () -> TenantContext.runAsSystem(() -> documents.saveAndFlush(passport)));
        documentIds.add(saved.getDocumentId());

        assertThat(AsUser.call(AsUser.SUPERADMIN,
                () -> TenantContext.runAsSystem(() -> cleanup.removeIfUnused(saved.getDocumentId())))).isFalse();
    }

    @Test
    @DisplayName("the document id is read from a ticket payload")
    void readsTheDocumentId() {
        assertThat(cleanup.documentIdIn("{\"studentId\":5,\"documentId\":812,\"travelType\":\"ALONE\"}")).isEqualTo(812);
        assertThat(cleanup.documentIdIn("{\"studentId\":5,\"documentId\":null}")).isNull();
        assertThat(cleanup.documentIdIn("not json")).isNull();
    }
}
