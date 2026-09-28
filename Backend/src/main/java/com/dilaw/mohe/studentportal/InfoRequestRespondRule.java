package com.dilaw.mohe.studentportal;

import com.dilaw.mohe.codes.InfoRequestStatus;
import com.dilaw.mohe.codes.InfoReviewStatus;
import org.springframework.http.HttpStatus;

import java.util.Optional;

/**
 * When a student may answer an information request (live QA M15, L14; 25 Sep 2026).
 *
 * <p>The portal refused only a request that was already answered and not sent back,
 * so a CANCELLED request fell through, was set to RESPONDED / PENDING_REVIEW and so
 * reopened; and a cancelled request whose earlier answer had been rejected counted as
 * answerable. An empty answer (no text, no file) was accepted too.
 *
 * <p>Answerable: a request that is PENDING or OVERDUE, or one that was answered and
 * whose answer was rejected (the student answers again). Nothing else.
 */
final class InfoRequestRespondRule {

    private InfoRequestRespondRule() {
    }

    record Refusal(HttpStatus status, String messageKey) {}

    /** Why the request cannot be answered now; empty when it can. */
    static Optional<Refusal> refusal(String status, String reviewStatus) {
        if (InfoReviewStatus.APPROVED.equals(reviewStatus)) {
            return Optional.of(new Refusal(HttpStatus.BAD_REQUEST, "portal.infoRequest.approvedLocked"));
        }
        if (InfoRequestStatus.PENDING.equals(status) || InfoRequestStatus.OVERDUE.equals(status)) {
            return Optional.empty();
        }
        if (InfoRequestStatus.RESPONDED.equals(status)) {
            return InfoReviewStatus.REJECTED.equals(reviewStatus)
                    ? Optional.empty()
                    : Optional.of(new Refusal(HttpStatus.BAD_REQUEST, "portal.infoRequest.underReview"));
        }
        // CANCELLED, or any state this code does not know: closed, not reopened by an answer.
        return Optional.of(new Refusal(HttpStatus.CONFLICT, "portal.infoRequest.closed"));
    }

    /** An answer says something: text that is not blank, or a file. */
    static boolean hasContent(String responseText, boolean hasFile) {
        return hasFile || (responseText != null && !responseText.isBlank());
    }
}
