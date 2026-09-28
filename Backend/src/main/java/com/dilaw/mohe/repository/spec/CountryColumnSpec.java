package com.dilaw.mohe.repository.spec;

import com.dilaw.mohe.ums.service.CountryScopeService.CountryScope;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;

/**
 * Country scope for any entity that carries its own country column.
 *
 * <p>The counterpart to {@link StudentIdCountrySpec}, which is for entities that
 * hold only a {@code studentId} and have to reach the country through a
 * correlated subquery. When the column is right there, use this — it is one
 * predicate rather than a subquery per row.
 *
 * <p><b>A null country is ministry-only.</b> {@code IN} never matches null, so a
 * row whose country was never recorded disappears for a country user and stays
 * visible to a ministry one. That is deliberate: on the tables this covers, a null
 * is missing data rather than a deliberate "global" marker, and showing one
 * attaché's unattributed spending to another is the failure worth avoiding.
 * {@code MOHE_QUARTER} is the exception and does not use this spec — there a null
 * genuinely means every country, and it is handled in {@code MoheQuarterService}.
 */
public final class CountryColumnSpec {

    private CountryColumnSpec() {}

    /**
     * @param scope     the caller's scope; ministry sees everything
     * @param fieldName the entity attribute holding the country id, e.g.
     *                  {@code "studyCountryInId"}
     */
    public static <T> Specification<T> countryScope(CountryScope scope, String fieldName) {
        return (root, query, cb) -> {
            if (scope == null || scope.isMinistryUser()) return cb.conjunction();
            List<Integer> allowed = scope.getAllowedCountryIds();
            // No countries assigned means no rows — never "all rows".
            if (allowed.isEmpty()) return cb.disjunction();
            return root.get(fieldName).in(allowed);
        };
    }

    /**
     * As {@link #countryScope}, but <b>a null country means shared with everyone</b>
     * rather than ministry-only.
     *
     * <p>Use this only where a null is a deliberate statement and not a gap. Two
     * places in FINCORE qualify, and both are structure rather than money:
     *
     * <ul>
     *   <li>{@code COA_ACCOUNT} — a null country is the الدليل المحاسبي الموحد, the
     *       unified chart every attaché books against. Hiding it from country
     *       officers would leave them with an empty chart and no way to classify
     *       anything. A non-null country is an attaché's own addition to it.
     *   <li>{@code FISCAL_YEAR} — all three rows are null, because 2024 is 2024
     *       everywhere. Fail-closed here would empty the fiscal-year selector that
     *       every other accounting screen depends on.
     * </ul>
     *
     * <p>It is the wrong choice anywhere a null means "we never recorded it" —
     * {@code WAFAR_SPENDING} and {@code AuditTrail} both have nulls of that kind,
     * and treating them as shared is exactly the leak this module is closing. The
     * safeguard is on the write side: a null country may only be created by a
     * ministry user, so an officer cannot manufacture a shared row.
     */
    public static <T> Specification<T> countryScopeOrShared(CountryScope scope, String fieldName) {
        return (root, query, cb) -> {
            if (scope == null || scope.isMinistryUser()) return cb.conjunction();
            List<Integer> allowed = scope.getAllowedCountryIds();
            if (allowed.isEmpty()) return cb.isNull(root.get(fieldName));
            return cb.or(cb.isNull(root.get(fieldName)), root.get(fieldName).in(allowed));
        };
    }
}
