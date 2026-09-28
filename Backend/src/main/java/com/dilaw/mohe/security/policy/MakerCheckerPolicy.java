package com.dilaw.mohe.security.policy;

import org.springframework.stereotype.Component;

/**
 * Maker and checker are two people, with no exemption for anyone.
 *
 * <p>The accounting module (allocations, payment batches, budget
 * appropriations) has held this rule inline since the ledger was written; it
 * lives here now (R-16) so that the three services state the rule once and the
 * same words come back from each. There is deliberately no super-admin hatch:
 * the rule exists precisely for the accounts that can do everything else, and
 * money that leaves the ministry is not a single-officer attaché's Wafar
 * (compare {@link WafarPolicy}).
 */
@Component
public class MakerCheckerPolicy implements AccessPolicy<MakerChecker> {

    public static final String NAME = "MakerChecker";

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public Class<MakerChecker> resourceType() {
        return MakerChecker.class;
    }

    @Override
    public Decision decide(AccessSubject subject, MakerChecker record, String action) {
        if (record == null || !"APPROVE".equals(action)) {
            return subject.isAuthenticated() ? Decision.allow() : Decision.deny("Access denied: not signed in");
        }
        // The accounting services name the checker (the signed-in user, from the
        // controller); the subject is the fallback when they do not.
        Integer checker = record.checkerId() != null ? record.checkerId()
                : subject.userId() == null ? null : subject.userId().intValue();
        if (checker == null) return Decision.deny("Access denied: not signed in");
        if (record.makerId() != null && record.makerId().equals(checker)) {
            return Decision.deny(record.what() + " cannot be approved by the user who " + record.verb() + " it.");
        }
        return Decision.allow();
    }
}
