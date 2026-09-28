package com.dilaw.mohe.approval.service;

import com.dilaw.mohe.approval.model.ApprovalRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Which pending finance requests a deleted recipient takes with it (live QA L8,
 * 24 Sep 2026).
 *
 * <p>Editing a recipient queues a finance update that links the student's
 * finance record to it. When the recipient is then deleted, that link — still
 * waiting for a decision — pointed at a row that no longer existed (request
 * 21718, recipient 49). The executor now withdraws the requests that
 * <em>introduce</em> the link and leaves every other pending finance change
 * alone. This test pins the selection; the withdrawal itself is the workflow's
 * ordinary rejection.
 */
class RecipientDeleteWithdrawsFinanceLinkTest {

    private final ApprovalIntegrationService service = new ApprovalIntegrationService();

    private static ApprovalRequest request(String proposed, String original) {
        ApprovalRequest r = new ApprovalRequest();
        r.setEntityType("STUDENT_FINANCE");
        r.setRequestData(proposed);
        r.setOriginalData(original);
        return r;
    }

    @Test
    @DisplayName("a request that links the record to the deleted recipient is withdrawn")
    void linkToDeletedRecipient() throws Exception {
        ApprovalRequest link = request("{\"id\":1001,\"studyReceivedWhomId\":49}", "{\"id\":1001,\"studyReceivedWhomId\":0}");
        assertThat(service.introducesLinkTo(link, 49)).isTrue();

        ApprovalRequest fromNull = request("{\"id\":1001,\"studyReceivedWhomId\":49}", "{\"id\":1001}");
        assertThat(service.introducesLinkTo(fromNull, 49)).isTrue();

        ApprovalRequest insert = request("{\"id\":1001,\"studyReceivedWhomId\":49}", null);
        assertThat(service.introducesLinkTo(insert, 49)).isTrue();
    }

    @Test
    @DisplayName("a request about another recipient, or one that does not change the link, stays")
    void unrelatedRequestsStay() throws Exception {
        ApprovalRequest other = request("{\"id\":1001,\"studyReceivedWhomId\":50}", "{\"id\":1001,\"studyReceivedWhomId\":0}");
        assertThat(service.introducesLinkTo(other, 49)).isFalse();

        ApprovalRequest noteOnly = request("{\"id\":1001,\"studyReceivedWhomId\":49,\"studyNote1\":\"x\"}",
                "{\"id\":1001,\"studyReceivedWhomId\":49,\"studyNote1\":null}");
        assertThat(service.introducesLinkTo(noteOnly, 49)).as("already linked before the edit").isFalse();

        ApprovalRequest noLink = request("{\"id\":1001}", "{\"id\":1001}");
        assertThat(service.introducesLinkTo(noLink, 49)).isFalse();

        ApprovalRequest empty = request(null, null);
        assertThat(service.introducesLinkTo(empty, 49)).isFalse();
    }
}
