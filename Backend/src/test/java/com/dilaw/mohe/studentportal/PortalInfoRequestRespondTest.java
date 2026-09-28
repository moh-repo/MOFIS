package com.dilaw.mohe.studentportal;

import com.dilaw.mohe.codes.InfoRequestStatus;
import com.dilaw.mohe.codes.InfoReviewStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Live QA M15 / L14 (25 Sep 2026): a student answered a request staff had cancelled,
 * and the answer set it back to RESPONDED / PENDING_REVIEW; an empty answer was
 * accepted as well.
 */
class PortalInfoRequestRespondTest {

    private static HttpStatus refused(String status, String review) {
        return InfoRequestRespondRule.refusal(status, review).map(InfoRequestRespondRule.Refusal::status).orElse(null);
    }

    @Test
    @DisplayName("P16: a cancelled request is closed - 409, whatever its earlier review said")
    void cancelledIsClosed() {
        assertThat(refused(InfoRequestStatus.CANCELLED, null)).isEqualTo(HttpStatus.CONFLICT);
        assertThat(refused(InfoRequestStatus.CANCELLED, InfoReviewStatus.REJECTED))
                .as("counted as answerable before the fix").isEqualTo(HttpStatus.CONFLICT);
        assertThat(InfoRequestRespondRule.refusal(InfoRequestStatus.CANCELLED, null).orElseThrow().messageKey())
                .isEqualTo("portal.infoRequest.closed");
    }

    @Test
    @DisplayName("open, overdue and sent-back requests can be answered")
    void answerable() {
        assertThat(refused(InfoRequestStatus.PENDING, null)).isNull();
        assertThat(refused(InfoRequestStatus.OVERDUE, null)).isNull();
        assertThat(refused(InfoRequestStatus.RESPONDED, InfoReviewStatus.REJECTED)).isNull();
    }

    @Test
    @DisplayName("an answer under review or approved cannot be changed")
    void underReviewOrApproved() {
        assertThat(refused(InfoRequestStatus.RESPONDED, InfoReviewStatus.PENDING_REVIEW)).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(refused(InfoRequestStatus.RESPONDED, InfoReviewStatus.APPROVED)).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("P17 / L14: an answer needs text or a file")
    void emptyAnswer() {
        assertThat(InfoRequestRespondRule.hasContent(null, false)).isFalse();
        assertThat(InfoRequestRespondRule.hasContent("   ", false)).isFalse();
        assertThat(InfoRequestRespondRule.hasContent("", true)).isTrue();
        assertThat(InfoRequestRespondRule.hasContent("my answer", false)).isTrue();
    }
}
