package com.dilaw.mohe.codes;

/** LU_INFO_REVIEW_STATUS (V073): StudentInfoRequest.reviewStatus. */
@CodeList(value = "info-review-status", mode = CodeList.Mode.EXACT)
public final class InfoReviewStatus {

    private InfoReviewStatus() {
    }

    public static final String PENDING_REVIEW = "PENDING_REVIEW";
    public static final String APPROVED = "APPROVED";
    public static final String REJECTED = "REJECTED";
}
