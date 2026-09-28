package com.dilaw.mohe.security.policy;

import org.springframework.stereotype.Component;

/**
 * The N-04 principle applied to per-country settings (security plan S-06).
 *
 * <p>An attaché's row - its SMTP settings, its bank branches - is written by
 * that attaché's own staff; ministry accounts read it. The global row (no
 * country) is the ministry's and nobody else's. No super-admin hatch: a wrong
 * SMTP host at an attaché is the attaché's to fix, and a wrong global one the
 * ministry's, and the two never overlap.
 *
 * <p>One exception, asked for by the owner (19 Sep 2026): a row marked
 * {@link CountryOwnedConfig#ministryMayManage()} — the notification settings —
 * may also be added, changed and deleted by ministry-level accounts for any
 * attaché, so the ministry can set up an attaché that has no administrator yet.
 * The attaché's own staff keep writing their own row; bank branches stay as above.
 */
@Component
public class CountryOwnedConfigPolicy implements AccessPolicy<CountryOwnedConfig> {

    public static final String NAME = "CountryOwnedConfig";

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public Class<CountryOwnedConfig> resourceType() {
        return CountryOwnedConfig.class;
    }

    @Override
    public Decision decide(AccessSubject subject, CountryOwnedConfig row, String action) {
        if (!subject.isAuthenticated()) return Decision.deny("Access denied: not signed in");
        if (row == null) return Decision.allow();
        boolean write = !"VIEW".equals(action);
        if (row.countryId() == null) {
            if (subject.isMinistryLevel() || !write) return Decision.allow();
            return Decision.deny("The global " + row.what() + " row is the ministry's to change.");
        }
        if (!write) {
            return subject.holdsCountry(row.countryId()) ? Decision.allow()
                    : Decision.deny("Access denied: country not in scope");
        }
        if (subject.isMinistryLevel()) {
            if (row.ministryMayManage()) return Decision.allow();
            return Decision.deny("An attaché's " + row.what() + " is changed by the attaché; ministry accounts may only view it.");
        }
        return subject.holdsCountry(row.countryId()) ? Decision.allow()
                : Decision.deny("Access denied: country not in scope");
    }
}
