package com.dilaw.mohe.controler;

import com.dilaw.mohe.model.FinReceivedWhomDocument;
import com.dilaw.mohe.model.StudyFinReceivedWhom;
import com.dilaw.mohe.repository.FinReceivedWhomDocumentRepository;
import com.dilaw.mohe.repository.StudyFinReceivedWhomRepository;
import com.dilaw.mohe.scope.AsUser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * A recipient's attachments are reachable only by someone who may see the
 * recipient (plan W6-09).
 *
 * <p>The list endpoint was scoped through the parent; the viewer, the download
 * and the soft delete were not. They took a document id, read the row straight
 * out of the table and served the file — so any holder of FINANCE who knew or
 * guessed an id could read another country's passport scans, and retire their
 * paperwork. Ids are sequential, which is what makes guessing them a method
 * rather than a hope.
 *
 * <p>The refusal is a 404 rather than a 403, which is the rule the recipient's
 * own {@code getById} already follows: a 403 would confirm that a record exists
 * at that id.
 */
@Tag("integration")
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
class FinanceReceivedWhomDocumentScopeTest {

    private static final List<String> FINANCE = List.of("FINANCE.VIEW", "FINANCE.EDIT");

    /** Egypt: the country the fixture belongs to, and the one Jordan's admin may not read. */
    private static final int EGYPT = 1;

    @Autowired private MockMvc mvc;
    @Autowired private StudyFinReceivedWhomRepository recipients;
    @Autowired private FinReceivedWhomDocumentRepository documents;

    /** The fixture is written and removed outside any request, so it carries its own transaction. */
    @Autowired private TransactionTemplate tx;

    /** An Egyptian recipient carrying one attachment. Returns the document's id. */
    private Integer egyptianRecipientWithDocument(String tag) {
        return AsUser.callWithAuthorities(AsUser.SUPERADMIN, FINANCE, () -> tx.execute(status -> {
            StudyFinReceivedWhom whom = new StudyFinReceivedWhom();
            whom.setFinReceivedName(tag);
            whom.setFinanceCountryId(EGYPT);
            Integer whomId = recipients.save(whom).getId();

            FinReceivedWhomDocument doc = new FinReceivedWhomDocument();
            doc.setWhomId(whomId);
            doc.setDocumentName(tag + "-passport");
            doc.setFilePath("uploads/finance-recipients/documents/whom_" + whomId + "/zt.pdf");
            doc.setOriginalFilename("zt.pdf");
            doc.setUploadDate(Instant.now());
            doc.setIsActive(true);
            return documents.save(doc).getDocumentId();
        }));
    }

    private void removeFixture(Integer docId) {
        AsUser.run(AsUser.SUPERADMIN, () -> tx.executeWithoutResult(status ->
                documents.findById(docId).ifPresent(d -> {
                    Integer whomId = d.getWhomId();
                    documents.deleteByWhomId(whomId);
                    recipients.deleteById(whomId);
                })));
    }

    @Test
    @DisplayName("another country's admin cannot retire a document they may not see")
    void theSoftDeleteIsScoped() throws Exception {
        Integer docId = egyptianRecipientWithDocument("ZT-scope-delete");
        try {
            AsUser.callWithAuthorities(AsUser.JO_ADMIN, FINANCE, () -> {
                try {
                    mvc.perform(delete("/api/v1/studyFinReceivedWhom/documents/" + docId));
                } catch (Exception e) {
                    // The refusal may arrive as an exception rather than a status;
                    // either way the assertion below is what decides.
                }
                return null;
            });

            boolean stillActive = AsUser.call(AsUser.SUPERADMIN, () -> tx.execute(status ->
                    documents.findById(docId).map(FinReceivedWhomDocument::getIsActive).orElse(false)));
            assertThat(stillActive)
                    .as("Jordan's admin retired an Egyptian recipient's paperwork")
                    .isTrue();
        } finally {
            removeFixture(docId);
        }
    }

    @Test
    @DisplayName("and the owner still can")
    void theOwnerMayRetireIt() throws Exception {
        Integer docId = egyptianRecipientWithDocument("ZT-scope-delete-own");
        try {
            AsUser.callWithAuthorities(AsUser.SUPERADMIN, FINANCE, () -> {
                try {
                    mvc.perform(delete("/api/v1/studyFinReceivedWhom/documents/" + docId));
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
                return null;
            });

            boolean stillActive = AsUser.call(AsUser.SUPERADMIN, () -> tx.execute(status ->
                    documents.findById(docId).map(FinReceivedWhomDocument::getIsActive).orElse(false)));
            assertThat(stillActive).as("the ministry may retire its own").isFalse();
        } finally {
            removeFixture(docId);
        }
    }

    @Test
    @DisplayName("the viewer and the download refuse another country's document")
    void theFileIsNotServedAcrossCountries() throws Exception {
        Integer docId = egyptianRecipientWithDocument("ZT-scope-view");
        try {
            AsUser.callWithAuthorities(AsUser.JO_ADMIN, FINANCE, () -> {
                for (String path : new String[]{"/view", "/download"}) {
                    try {
                        int status = mvc.perform(get("/api/v1/studyFinReceivedWhom/documents/" + docId + path))
                                .andReturn().getResponse().getStatus();
                        assertThat(status)
                                .as("%s served to a country that may not see the recipient", path)
                                .isNotEqualTo(200);
                    } catch (Exception e) {
                        // A raised refusal is a refusal.
                    }
                }
                return null;
            });
        } finally {
            removeFixture(docId);
        }
    }
}
