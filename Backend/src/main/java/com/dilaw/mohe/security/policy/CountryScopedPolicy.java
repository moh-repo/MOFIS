package com.dilaw.mohe.security.policy;

import org.springframework.stereotype.Component;

/**
 * The rule every tenanted table shares: a row is in front of the caller when
 * its country is in the caller's request scope.
 *
 * <p>A row with no country is ministry-only. The old guards tested
 * {@code country != null} first, which made such a record readable by every
 * officer — the opposite of what a missing country should mean, and the
 * reading {@code StudentDataController.validateStudyCountryScope} corrected on
 * 27 Aug 2026. The rule is the same for VIEW, EDIT and DELETE; finer rules
 * (status, ownership, maker-checker) belong to the resource's own policy,
 * which calls this one first.
 */
@Component
public class CountryScopedPolicy implements AccessPolicy<CountryOwned> {

    public static final String NAME = "CountryScoped";

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public Class<CountryOwned> resourceType() {
        return CountryOwned.class;
    }

    @Override
    public Decision decide(AccessSubject subject, CountryOwned resource, String action) {
        if (!subject.isAuthenticated()) return Decision.deny("Access denied: not signed in");
        if (resource == null) return Decision.allow();
        return inScope(subject, resource.what(), resource.countryId());
    }

    /** Shared with the resource policies so the wording is the same everywhere. */
    static Decision inScope(AccessSubject subject, String what, Integer countryId) {
        if (countryId == null) {
            // The request scope, not the tier: a ministry officer who narrowed the
            // picker to one attaché is looking at that attaché, and a record filed
            // under no attaché is not part of it.
            if (subject.allCountries()) return Decision.allow();
            return Decision.deny("Access denied: " + lower(what) + " has no country on record");
        }
        if (subject.holdsCountry(countryId)) return Decision.allow();
        return Decision.deny("Access denied: country not in scope");
    }

    private static String lower(String what) {
        return what == null || what.isBlank() ? "record" : Character.toLowerCase(what.charAt(0)) + what.substring(1);
    }
}
