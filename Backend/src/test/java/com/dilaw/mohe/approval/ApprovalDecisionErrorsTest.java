package com.dilaw.mohe.approval;

import com.dilaw.mohe.approval.controler.ApprovalController;
import com.dilaw.mohe.approval.service.ApprovalChangeService;
import com.dilaw.mohe.approval.service.ApprovalWorkflowService;
import com.dilaw.mohe.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * A failed approval decision tells the approver what happened, not what SQL ran.
 *
 * <p>The approve, reject and create endpoints caught every exception and answered
 * 400 with its message. When the final level's execution hit a constraint, that
 * message was the driver's: the approver saw "Cannot insert the value NULL into column
 * 'STUDY_FIN_RECIEVED_STATUS', table 'MOHECORE.STUDENT_FINANCE_PAYMENT' …" twice
 * (live QA BRD-002, requests 20868/20869). The errors now reach the global handler.
 */
@ExtendWith(MockitoExtension.class)
class ApprovalDecisionErrorsTest {

    private static final String SQL_TEXT =
            "Cannot insert the value NULL into column 'STUDY_FIN_RECIEVED_STATUS', table 'MOHECORE.STUDENT_FINANCE_PAYMENT'";

    @Mock private ApprovalWorkflowService workflowService;
    @Mock private ApprovalChangeService changeService;
    @InjectMocks private ApprovalController controller;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private MvcResult approve() throws Exception {
        return mvc.perform(post("/api/v1/approvals/20868/approve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"approverId\":1,\"comments\":\"ok\"}"))
                .andReturn();
    }

    @Test
    @DisplayName("a constraint failure while executing is a 409 without the SQL")
    void constraintFailureDoesNotLeakSql() throws Exception {
        when(workflowService.approveStep(anyLong(), anyLong(), any()))
                .thenThrow(new DataIntegrityViolationException("could not execute statement",
                        new SQLException(SQL_TEXT, "23000", 515)));

        MvcResult result = approve();

        assertThat(result.getResponse().getStatus()).isEqualTo(409);
        String body = result.getResponse().getContentAsString();
        assertThat(body).doesNotContain("STUDENT_FINANCE_PAYMENT").doesNotContain("Cannot insert");
    }

    @Test
    @DisplayName("a request that is already decided is a 409 that says so")
    void alreadyDecidedIsAConflict() throws Exception {
        when(workflowService.approveStep(anyLong(), anyLong(), any()))
                .thenThrow(new IllegalStateException("Request is already EXECUTED"));

        MvcResult result = approve();

        assertThat(result.getResponse().getStatus()).isEqualTo(409);
        assertThat(result.getResponse().getContentAsString()).contains("Request is already EXECUTED");
    }

    @Test
    @DisplayName("an unexpected fault is a 500 with a generic message, never its text")
    void unexpectedFaultIsGeneric() throws Exception {
        when(workflowService.rejectRequest(anyLong(), anyLong(), any()))
                .thenThrow(new RuntimeException("NullPointerException in ApprovalWorkflowService.java:444"));

        MvcResult result = mvc.perform(post("/api/v1/approvals/1/reject")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rejectedBy\":1,\"reason\":\"no\"}"))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(500);
        assertThat(result.getResponse().getContentAsString()).doesNotContain("ApprovalWorkflowService");
    }
}
