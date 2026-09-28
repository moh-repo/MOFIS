package com.dilaw.mohe.controler;

import com.dilaw.mohe.approval.event.ApprovalExecutionEvent;
import com.dilaw.mohe.approval.model.ApprovalRequest;
import com.dilaw.mohe.approval.model.RequestType;
import com.dilaw.mohe.approval.service.ApprovalIntegrationService;
import com.dilaw.mohe.model.FinReceivedWhomDocument;
import com.dilaw.mohe.model.StudyFinReceivedWhom;
import com.dilaw.mohe.repository.FinReceivedWhomDocumentRepository;
import com.dilaw.mohe.repository.StudyFinReceivedWhomRepository;
import com.dilaw.mohe.scope.AsUser;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * What an approved recipient change does to the row.
 *
 * <p>The screen is behind the approval workflow now, so a POST or PUT writes
 * nothing — that half is {@link FinanceApprovalSubmissionTest}. Held here is
 * what happens once an approver signs off, and it is the three faults the
 * 31 August pass fixed that must keep holding:
 *
 * <ul>
 *   <li>an edit changes what it names and leaves the rest of the row alone. The
 *       dialog binds 24 of 27 columns, and {@code repository.save(body)} wrote
 *       NULL over the other three — {@code LETTER_REASON_ID},
 *       {@code FINANCE_PAYMENT_ID} and {@code FIN_MANUAL_FORM_DATE};</li>
 *   <li>a delete takes the recipient's documents with it. Ids are handed out as
 *       {@code MAX(id) + 1}, so leaving them behind meant the next recipient
 *       created could inherit a stranger's passport scans;</li>
 *   <li>a recipient cannot be stored under attaché 0 — the dialog's empty
 *       selection — because no attaché's staff would ever see it again.</li>
 * </ul>
 *
 * <p>Driven through {@code handleApprovalExecution}: the request stores the
 * dialog's own JSON tree, and replaying it is the only way a change now reaches
 * the table. That is also what keeps the partial-edit distinction testable —
 * a bound entity could not express "this column was not sent".
 */
@Tag("integration")
@SpringBootTest(webEnvironment = WebEnvironment.MOCK)
@AutoConfigureMockMvc(addFilters = false)
class FinanceReceivedWhomCrudTest {

    private static final List<String> FINANCE = List.of("FINANCE.VIEW", "FINANCE.EDIT");

    /** Egypt — the country the seeded super admin is scoped to. */
    private static final int COUNTRY = 1;

    @Autowired private ObjectMapper json;
    @Autowired private StudyFinReceivedWhomRepository repository;
    @Autowired private FinReceivedWhomDocumentRepository documents;
    @Autowired private ApprovalIntegrationService integration;

    /** Applies a request the way an approver's final sign-off does. */
    private void execute(RequestType type, Integer entityId, String requestData) {
        ApprovalRequest request = new ApprovalRequest();
        request.setId(1L);
        request.setEntityType("STUDY_FIN_RECEIVED_WHOM");
        request.setRequestType(type);
        request.setEntityId(entityId == null ? null : String.valueOf(entityId));
        request.setRequestData(requestData);
        request.setRequestedBy(1L);
        AsUser.callWithAuthorities(AsUser.SUPERADMIN, FINANCE, () -> {
            integration.handleApprovalExecution(new ApprovalExecutionEvent(this, request, 1L));
            return null;
        });
    }

    /** A recipient with every column the dialog does not bind already filled in. */
    private StudyFinReceivedWhom draft(String name) {
        StudyFinReceivedWhom row = new StudyFinReceivedWhom();
        row.setFinReceivedName(name);
        row.setFinReceivedRelationship("PARENT");
        row.setFinRecievedYear(2026);
        row.setFinRecievedMonth(1);
        row.setFinRecievedYearMonth(202601);
        row.setFinRecievedAmount(1000);
        row.setFinanceCountryId(COUNTRY);
        row.setFinRecievedStatus(1);
        row.setCheckId("ZT-CHK-1");
        // The three the dialog never sends.
        row.setLetterReasonId(3);
        row.setFinancePaymentId(4242L);
        row.setFinManualFormDate(Instant.parse("2026-01-15T00:00:00Z"));
        return row;
    }

    /** Applies an approved INSERT and returns the id of the row it created. */
    private Integer createApproved(String name) throws Exception {
        execute(RequestType.INSERT, null, json.writeValueAsString(draft(name)));
        return repository.findAll().stream()
                .filter(r -> name.equals(r.getFinReceivedName()))
                .map(StudyFinReceivedWhom::getId)
                .findFirst()
                .orElseThrow(() -> new AssertionError("the approved insert wrote no row"));
    }

    @Test
    @DisplayName("an approved recipient is written and can be read back")
    void addAndView() throws Exception {
        Integer id = createApproved("ZT-recipient-insert");
        try {
            assertThat(id).isPositive();

            StudyFinReceivedWhom row = repository.findById(id).orElseThrow();
            assertThat(row.getFinReceivedName()).isEqualTo("ZT-recipient-insert");
            assertThat(row.getFinRecievedAmount()).isEqualTo(1000);
            assertThat(row.getFinanceCountryId()).isEqualTo(COUNTRY);
        } finally {
            repository.deleteById(id);
        }
    }

    @Test
    @DisplayName("an approved edit leaves the columns the dialog never sends alone")
    void editKeepsUnsentColumns() throws Exception {
        Integer id = createApproved("ZT-recipient-edit");
        try {
            // Exactly what the dialog sends: the columns it binds, and not one more.
            String body = """
                    {"id":%d,"finReceivedName":"ZT-recipient-edited","finRecievedAmount":1500,
                     "financeCountryId":%d,"finRecievedStatus":1,"checkId":"ZT-CHK-2"}
                    """.formatted(id, COUNTRY);
            execute(RequestType.UPDATE, id, body);

            StudyFinReceivedWhom after = repository.findById(id).orElseThrow();
            assertThat(after.getFinReceivedName()).isEqualTo("ZT-recipient-edited");
            assertThat(after.getFinRecievedAmount()).isEqualTo(1500);

            // A field that is absent is not a field set to nothing.
            assertThat(after.getLetterReasonId()).as("LETTER_REASON_ID").isEqualTo(3);
            assertThat(after.getFinancePaymentId()).as("FINANCE_PAYMENT_ID").isEqualTo(4242L);
            assertThat(after.getFinManualFormDate()).as("FIN_MANUAL_FORM_DATE").isNotNull();
        } finally {
            repository.deleteById(id);
        }
    }

    @Test
    @DisplayName("an approved edit still clears a field it does send as null")
    void editStillClearsWhatItOwns() throws Exception {
        Integer id = createApproved("ZT-recipient-clear");
        try {
            // The distinction is only worth keeping if the dialog can still empty
            // one of its own fields — which a null-guarded copy would have blocked.
            String body = """
                    {"id":%d,"financeCountryId":%d,"checkId":null}
                    """.formatted(id, COUNTRY);
            execute(RequestType.UPDATE, id, body);

            StudyFinReceivedWhom after = repository.findById(id).orElseThrow();
            assertThat(after.getCheckId()).as("a null the dialog sent").isNull();
            assertThat(after.getLetterReasonId()).as("a field it did not send").isEqualTo(3);
        } finally {
            repository.deleteById(id);
        }
    }

    @Test
    @DisplayName("an approved delete takes the recipient's documents with it")
    void deleteTakesTheDocuments() throws Exception {
        Integer id = createApproved("ZT-recipient-delete");
        boolean deleted = false;
        try {
            FinReceivedWhomDocument doc = new FinReceivedWhomDocument();
            doc.setWhomId(id);
            doc.setDocumentName("ZT-passport");
            doc.setFilePath("uploads/finance-recipients/documents/whom_" + id + "/zt.pdf");
            doc.setOriginalFilename("zt.pdf");
            doc.setUploadDate(Instant.now());
            doc.setIsActive(true);
            documents.save(doc);
            assertThat(documents.findByWhomId(id)).isNotEmpty();

            execute(RequestType.DELETE, id, "{}");
            deleted = true;

            // Ids are MAX(id) + 1. A document left behind is one the next recipient
            // to take this id would inherit — someone else's passport scan.
            assertThat(documents.findByWhomId(id))
                    .as("documents of the deleted recipient")
                    .isEmpty();
            assertThat(repository.findById(id)).isEmpty();
        } finally {
            documents.deleteByWhomId(id);
            if (!deleted) {
                repository.deleteById(id);
            }
        }
    }

    @Test
    @DisplayName("a recipient cannot be stored under attaché 0")
    void attacheIsRequired() throws Exception {
        StudyFinReceivedWhom row = draft("ZT-recipient-no-attache");
        row.setFinanceCountryId(0);

        // 0 is the dialog's empty selection and the column's default, and it used
        // to pass the null check — storing a row no attaché's staff could see.
        // The refusal now lands at execution rather than at submit, which is where
        // every rule of these screens moved when they went behind the workflow.
        assertThatThrownBy(() -> execute(RequestType.INSERT, null, json.writeValueAsString(row)))
                .isInstanceOf(RuntimeException.class);

        assertThat(repository.findAll())
                .as("nothing was written")
                .noneMatch(r -> "ZT-recipient-no-attache".equals(r.getFinReceivedName()));
    }
}
