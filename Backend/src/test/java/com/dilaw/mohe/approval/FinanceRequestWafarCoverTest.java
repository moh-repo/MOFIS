package com.dilaw.mohe.approval;

import com.dilaw.mohe.approval.model.ApprovalRequest;
import com.dilaw.mohe.approval.model.RequestType;
import com.dilaw.mohe.approval.service.ApprovalIntegrationService;
import com.dilaw.mohe.approval.service.ApprovalWorkflowService;
import com.dilaw.mohe.exception.LocalizedStatusException;
import com.dilaw.mohe.model.StudentFinance;
import com.dilaw.mohe.service.StudentFinanceWafarService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Round-6 QA R6-01: a finance edit that drops the Wafar while approved spending needs it
 * was accepted when raised and refused only when the attaché approved level 4, after
 * three approvers had acted. The Wafar requests were already checked when raised; a
 * finance request is now checked there too.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FinanceRequestWafarCoverTest {

    private static final int STUDENT = 10119;

    @Mock private ApprovalWorkflowService workflowService;
    @Mock private StudentFinanceWafarService studentFinanceWafarService;
    @Mock private com.dilaw.mohe.ums.service.CountryScopeService countryScopeService;
    @Mock private com.dilaw.mohe.config.tenant.WafarWriteKey wafarWriteKey;
    @Mock private com.dilaw.mohe.ums.service.UmsAuditLogService umsAuditLogService;

    @InjectMocks private ApprovalIntegrationService integration;

    @BeforeEach
    void setUp() {
        when(workflowService.createRequest(anyString(), any(), any(), any(), any(), anyLong(), any(), any()))
                .thenReturn(new ApprovalRequest());
    }

    private static StudentFinance finance(Integer wafar) {
        StudentFinance f = new StudentFinance();
        f.setId(STUDENT);
        f.setStudyCountryInId(1004);
        f.setWafarAmount(wafar);
        return f;
    }

    @Test
    @DisplayName("an edit is checked against the proposal, a delete as the Wafar row going, an add not at all")
    void checkedWhenRaised() {
        StudentFinance original = finance(100);
        StudentFinance proposed = finance(0);

        integration.createStudentFinanceRequest(RequestType.UPDATE, proposed, original, 1L);
        verify(studentFinanceWafarService).requireWithdrawableByFinance(eq(STUDENT), same(proposed));

        integration.createStudentFinanceRequest(RequestType.DELETE, original, original, 1L);
        verify(studentFinanceWafarService).requireWithdrawableByFinance(eq(STUDENT), isNull());

        // An add has no Wafar row to take from; the execution check still covers a row left behind.
        integration.createStudentFinanceRequest(RequestType.INSERT, finance(50), null, 1L);
        verify(studentFinanceWafarService, times(2)).requireWithdrawableByFinance(any(), any());
    }

    @Test
    @DisplayName("the QA case: a refused edit raises no request")
    void refusalRaisesNoRequest() {
        doThrow(new LocalizedStatusException(HttpStatus.CONFLICT, "not covered", "wafar.deposit.notCovered", "100", "10"))
                .when(studentFinanceWafarService).requireWithdrawableByFinance(eq(STUDENT), any());

        assertThatThrownBy(() -> integration.createStudentFinanceRequest(RequestType.UPDATE, finance(0), finance(100), 1L))
                .isInstanceOf(LocalizedStatusException.class);
        verify(workflowService, never()).createRequest(anyString(), any(), any(), any(), any(), anyLong(), any(), any());
    }
}
