package com.dilaw.mohe.codes;

/**
 * UMSCORE.AccessReviews.Status (security plan S-04). An internal state of the
 * review, not a lookup-backed list: there is no LU table and no {@code @CodeList}
 * for it, so the startup guard does not verify it; the constants live here
 * because status codes are written nowhere else (NoCodeLiteralsInServicesTest).
 */
public final class AccessReviewStatus {

    private AccessReviewStatus() {
    }

    public static final String OPEN = "OPEN";
    public static final String CLOSED = "CLOSED";
}
