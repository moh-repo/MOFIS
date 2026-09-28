package com.dilaw.mohe;

import com.dilaw.mohe.approval.event.ApprovalExecutionEvent;
import com.dilaw.mohe.approval.model.ApprovalRequest;
import com.dilaw.mohe.approval.model.RequestType;
import com.dilaw.mohe.approval.service.ApprovalIntegrationService;
import com.dilaw.mohe.model.StudentFinance;
import com.dilaw.mohe.model.StudentFinanceWafar;
import com.dilaw.mohe.service.StudentFinanceService;
import com.dilaw.mohe.service.StudentFinanceWafarService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * STUDENT_FINANCE and STUDENT_FINANCE_WAFAR hold the same wafar from two directions, and
 * either screen can change it. The services that keep them in step are covered by
 * {@link StudentFinanceWafarSyncTest} and {@link StudentFinanceWafarApplyToFinanceTest};
 * what those cannot catch is the executor simply not calling them.
 *
 * That wiring is the whole feature — a missing call here leaves the two tables disagreeing
 * with no error anywhere, which is exactly how the wafar write was lost before it was
 * routed through approval.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ApprovalWafarSyncWiringTest {

    @Mock
    private StudentFinanceService studentFinanceService;

    @Mock
    private StudentFinanceWafarService studentFinanceWafarService;

    // N-04.d: the executor consults the scope and may raise the Wafar write key; mocked
    // here, a ministry-level executor is not what these wiring cases are about.
    @Mock
    private com.dilaw.mohe.ums.service.CountryScopeService countryScopeService;

    @Mock
    private com.dilaw.mohe.config.tenant.WafarWriteKey wafarWriteKey;

    @Mock
    private com.dilaw.mohe.ums.service.UmsAuditLogService umsAuditLogService;

    @InjectMocks
    private ApprovalIntegrationService service;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private ApprovalRequest request(String entityType, RequestType type, String entityId, Object payload)
            throws Exception {
        ApprovalRequest r = new ApprovalRequest();
        r.setId(1L);
        r.setEntityType(entityType);
        r.setRequestType(type);
        r.setEntityId(entityId);
        r.setRequestData(objectMapper.writeValueAsString(payload));
        return r;
    }

    private void execute(ApprovalRequest r) {
        service.handleApprovalExecution(new ApprovalExecutionEvent(this, r, 1L));
    }

    /** Editing the wafar on the finance form has to reach the wafar table. */
    @Test
    void financeUpdateSyncsTheWafarRow() throws Exception {
        StudentFinance payload = new StudentFinance();
        payload.setId(42);
        payload.setWafarAmount(1500);
        payload.setWafarTypeId(3);
        payload.setWafarNote("revised wafar");

        StudentFinance persisted = new StudentFinance();
        persisted.setId(42);
        persisted.setWafarAmount(1500);
        when(studentFinanceService.update(eq(42), any(StudentFinance.class))).thenReturn(persisted);

        execute(request("STUDENT_FINANCE", RequestType.UPDATE, "42", payload));

        ArgumentCaptor<StudentFinance> captor = ArgumentCaptor.forClass(StudentFinance.class);
        verify(studentFinanceWafarService).syncFromFinance(captor.capture(), eq("revised wafar"));
        // The persisted record, not the raw payload — it carries the resolved column values.
        assertEquals(1500, captor.getValue().getWafarAmount());
    }

    /** Clearing the wafar has to remove the row, not leave it at its old amount. */
    @Test
    void financeUpdateWithTheWafarClearedStillSyncs() throws Exception {
        StudentFinance payload = new StudentFinance();
        payload.setId(42);
        payload.setWafarAmount(0);

        StudentFinance persisted = new StudentFinance();
        persisted.setId(42);
        persisted.setWafarAmount(0);
        when(studentFinanceService.update(eq(42), any(StudentFinance.class))).thenReturn(persisted);

        execute(request("STUDENT_FINANCE", RequestType.UPDATE, "42", payload));

        verify(studentFinanceWafarService).syncFromFinance(any(StudentFinance.class), any());
    }

    @Test
    void financeDeleteRemovesTheWafarRow() throws Exception {
        StudentFinance payload = new StudentFinance();
        payload.setId(42);

        execute(request("STUDENT_FINANCE", RequestType.DELETE, "42", payload));

        verify(studentFinanceService).delete(42);
        verify(studentFinanceWafarService).deleteIfExists(42);
    }

    /** The other direction: a wafar edited on the wafar page has to reach the finance record. */
    @Test
    void wafarUpdateAppliesToTheFinanceRecord() throws Exception {
        StudentFinanceWafar payload = new StudentFinanceWafar();
        payload.setId(42);
        payload.setStudentFinRecievedAmount(1500);

        StudentFinanceWafar saved = new StudentFinanceWafar();
        saved.setId(42);
        saved.setStudentFinRecievedAmount(1500);
        when(studentFinanceWafarService.update(eq(42), any(StudentFinanceWafar.class))).thenReturn(saved);

        execute(request("STUDENT_FINANCE_WAFAR", RequestType.UPDATE, "42", payload));

        verify(studentFinanceWafarService).applyToFinance(saved);
    }

    @Test
    void wafarInsertAppliesToTheFinanceRecord() throws Exception {
        StudentFinanceWafar payload = new StudentFinanceWafar();
        payload.setId(42);
        payload.setStudentFinRecievedAmount(1500);

        execute(request("STUDENT_FINANCE_WAFAR", RequestType.INSERT, "42", payload));

        verify(studentFinanceWafarService).create(any(StudentFinanceWafar.class));
        verify(studentFinanceWafarService).applyToFinance(any(StudentFinanceWafar.class));
    }

    /*
     * STUDENT_ID is a manually-assigned key. A payload that lost it must fall back to
     * entityId rather than reach Hibernate and fail with "ids for this class must be
     * manually assigned before calling save()".
     */
    @Test
    void wafarInsertFallsBackToEntityIdWhenThePayloadHasNoStudentId() throws Exception {
        StudentFinanceWafar payload = new StudentFinanceWafar();
        payload.setStudentFinRecievedAmount(1500);

        execute(request("STUDENT_FINANCE_WAFAR", RequestType.INSERT, "42", payload));

        ArgumentCaptor<StudentFinanceWafar> captor = ArgumentCaptor.forClass(StudentFinanceWafar.class);
        verify(studentFinanceWafarService).create(captor.capture());
        assertEquals(42, captor.getValue().getId());
    }

    @Test
    void wafarDeleteClearsTheWafarOffTheFinanceRecord() throws Exception {
        StudentFinanceWafar payload = new StudentFinanceWafar();
        payload.setId(42);

        execute(request("STUDENT_FINANCE_WAFAR", RequestType.DELETE, "42", payload));

        verify(studentFinanceWafarService).deleteIfExists(42);
        verify(studentFinanceWafarService).clearFinanceWafar(42);
    }
}
