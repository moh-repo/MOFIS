package com.dilaw.mohe.repository.spec;

import com.dilaw.mohe.model.StudyFinReceivedWhom;
import com.dilaw.mohe.ums.service.CountryScopeService.CountryScope;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;

/**
 * Country scope for the "who collected this money" records.
 *
 * <p><b>A null country is ministry-only.</b> {@code IN} never matches null, and
 * that is the intended behaviour here: a record with no country is one whose
 * attaché was never written down, not a record that belongs to everyone. This is
 * the same rule as {@code CountryColumnSpec.countryScope} and the opposite of the
 * one on the study-years and study-amount lookups, where a null row is a
 * deliberate shared default.
 *
 * <p>The service enforces the matching write rule — every new record must name a
 * country — so the ministry-only bucket cannot grow from the application.
 */
public final class StudyFinReceivedWhomSpec {

    private StudyFinReceivedWhomSpec() {}

    public static Specification<StudyFinReceivedWhom> countryScope(CountryScope scope) {
        return (root, query, cb) -> {
            if (scope == null || scope.isMinistryUser()) return cb.conjunction();
            List<Integer> allowed = scope.getAllowedCountryIds();
            if (allowed.isEmpty()) return cb.disjunction();
            return root.get("financeCountryId").in(allowed);
        };
    }
}
