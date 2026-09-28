package com.dilaw.mohe.security.policy;

/**
 * A record that one person prepared and another must confirm.
 *
 * @param what      the noun of the refusal: {@code "An allocation"}, {@code "A payment batch"}
 * @param verb      what the maker did: {@code "created"}, {@code "prepared"}, {@code "entered"}
 * @param makerId   the maker's user id; null when the record has no maker on file
 * @param checkerId the user approving, as the accounting services receive it from the controller
 *                  (the signed-in user); null means the policy's subject
 */
public record MakerChecker(String what, String verb, Integer makerId, Integer checkerId) {

    public static MakerChecker allocation(Integer makerId, Integer checkerId) {
        return new MakerChecker("An allocation", "created", makerId, checkerId);
    }

    public static MakerChecker paymentBatch(Integer makerId, Integer checkerId) {
        return new MakerChecker("A payment batch", "prepared", makerId, checkerId);
    }

    public static MakerChecker appropriation(Integer makerId, Integer checkerId) {
        return new MakerChecker("An appropriation", "entered", makerId, checkerId);
    }

    /** Live QA M2: the creator of a payment voucher authorized it. */
    public static MakerChecker paymentAuth(Integer makerId, Integer checkerId) {
        return new MakerChecker("A payment authorisation", "created", makerId, checkerId);
    }
}
