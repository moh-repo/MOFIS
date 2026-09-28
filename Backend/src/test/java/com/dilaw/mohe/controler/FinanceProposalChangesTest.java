package com.dilaw.mohe.controler;

import com.dilaw.mohe.approval.dto.ApprovalChangeDetailsDto.FieldChange;
import com.dilaw.mohe.approval.model.ApprovalRequest;
import com.dilaw.mohe.approval.model.RequestType;
import com.dilaw.mohe.approval.service.ApprovalChangeService;
import com.dilaw.mohe.approval.service.ApprovalIntegrationService;
import com.dilaw.mohe.approval.service.ApprovalWorkflowService;
import com.dilaw.mohe.model.StudentFinance;
import com.dilaw.mohe.service.FileEncryptionService;
import com.dilaw.mohe.service.StudentFinanceWafarService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * What the approver is shown for a finance edit (QA B12 = F3, N10, live #13).
 *
 * <p>The changes view compares the proposal with the record on file, field by
 * field, so anything the proposal did not carry read as a change nobody made. A
 * status change built its proposal from twenty hand-picked fields and showed the
 * currency, bank account, quarter and wafar as cleared; every form edit showed
 * "net 1200 → (empty)", because the net is computed by the database and the form
 * cannot send it; and the wafar note, kept on another table, read as newly added.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FinanceProposalChangesTest {

    @Mock private ApprovalWorkflowService workflowService;
    @Mock private StudentFinanceWafarService studentFinanceWafarService;
    @Mock private com.dilaw.mohe.ums.service.CountryScopeService countryScopeService;
    @Mock private com.dilaw.mohe.config.tenant.WafarWriteKey wafarWriteKey;
    @Mock private com.dilaw.mohe.ums.service.UmsAuditLogService umsAuditLogService;

    @InjectMocks private ApprovalIntegrationService integration;

    private final ApprovalChangeService changes = new ApprovalChangeService(mock(FileEncryptionService.class));

    /** The same shape the controllers read a request body with. */
    private final ObjectMapper json = new ObjectMapper().registerModule(new JavaTimeModule());

    @BeforeEach
    void setUp() {
        when(workflowService.createRequest(anyString(), any(), any(), any(), any(), anyLong(), any(), any()))
                .thenReturn(new ApprovalRequest());
        when(studentFinanceWafarService.noteOf(4)).thenReturn("note on file");
    }

    private static StudentFinance onFile() {
        StudentFinance f = new StudentFinance();
        f.setId(4);
        f.setStudentFinId("4");
        f.setStudentFinName("Student");
        f.setStudentFinRecievedAmount(1200);
        f.setStudyCurrencyInId(3);
        f.setStudyCountryInId(1);
        f.setStudyCheckDate(Instant.parse("2026-01-10T12:00:00Z"));
        f.setStudyCheckFormNumber("F-9");
        f.setStudyFinRecievedStatus(1);
        f.setWafarAmount(200);
        f.setWafarTypeId(2);
        f.setNetAmount(1000);
        f.setStudentBankId(7);
        f.setStudentAccountNumber("123456");
        f.setMoheQuarterId(5);
        return f;
    }

    /** Raises the request and returns the fields the approver is shown as changed. */
    private List<String> changedFields(StudentFinance proposed, StudentFinance original) {
        integration.createStudentFinanceRequest(RequestType.UPDATE, proposed, original, 1L);
        ArgumentCaptor<String> requestData = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> originalData = ArgumentCaptor.forClass(String.class);
        verify(workflowService).createRequest(eq("STUDENT_FINANCE"), eq(RequestType.UPDATE), eq("4"),
                requestData.capture(), originalData.capture(), eq(1L), any(), any());

        ApprovalRequest request = new ApprovalRequest();
        request.setId(1L);
        request.setEntityType("STUDENT_FINANCE");
        request.setRequestType(RequestType.UPDATE);
        request.setEntityId("4");
        request.setRequestData(requestData.getValue());
        request.setOriginalData(originalData.getValue());
        return changes.describe(request).getFieldChanges().stream().map(FieldChange::field).toList();
    }

    @Test
    @DisplayName("a status change shows the status, and nothing else")
    void statusChangeShowsOnlyTheStatus() {
        StudentFinance original = onFile();
        StudentFinance proposed = StudentFinanceController.copyFinanceForApproval(original);
        proposed.setStudyFinRecievedStatus(2);

        assertThat(changedFields(proposed, original)).containsExactly("studyFinRecievedStatus");
    }

    @Test
    @DisplayName("a form edit of the wafar shows the wafar and the net it changes — not the note")
    void wafarEditShowsWafarAndNet() throws Exception {
        StudentFinance original = onFile();
        // What the form's PUT body becomes: netAmount is read-only in JSON, and the
        // form sends no note when the user did not touch it.
        StudentFinance proposed = json.readValue(json.writeValueAsString(original), StudentFinance.class);
        proposed.setWafarAmount(300);

        assertThat(changedFields(proposed, original))
                .containsExactlyInAnyOrder("wafarAmount", "netAmount");
    }

    @Test
    @DisplayName("an untouched form save shows no change at all")
    void untouchedSaveShowsNothing() throws Exception {
        StudentFinance original = onFile();
        StudentFinance proposed = json.readValue(json.writeValueAsString(original), StudentFinance.class);

        assertThat(changedFields(proposed, original)).isEmpty();
    }

    @Test
    @DisplayName("the status proposal carries every property of the record")
    void copyCarriesEveryProperty() throws Exception {
        StudentFinance source = new StudentFinance();
        int n = 1;
        for (Field field : StudentFinance.class.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers())) {
                continue;
            }
            field.setAccessible(true);
            Class<?> type = field.getType();
            if (type == Integer.class) field.set(source, n++);
            else if (type == String.class) field.set(source, "v" + n++);
            else if (type == Instant.class) field.set(source, Instant.ofEpochSecond(n++));
        }

        StudentFinance copy = StudentFinanceController.copyFinanceForApproval(source);

        for (Field field : StudentFinance.class.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers())) {
                continue;
            }
            field.setAccessible(true);
            assertThat(field.get(copy)).as(field.getName()).isEqualTo(field.get(source));
        }
    }
}
