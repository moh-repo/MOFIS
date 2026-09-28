package com.dilaw.mohe.ums.repository.spec;

import com.dilaw.mohe.ums.model.UmsAuditLog;
import com.dilaw.mohe.ums.model.UmsUser;
import com.dilaw.mohe.ums.model.UmsUserCountry;
import com.dilaw.mohe.ums.model.UmsUserSession;
import com.dilaw.mohe.ums.service.CountryScopeService.CountryScope;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

/**
 * Which staff accounts a caller may administer.
 *
 * <p>The third country-scope primitive in this codebase, and it exists because
 * the other two answer a question this one does not. {@code CountryColumnSpec}
 * scopes a row that <em>has</em> a country; {@code StudentIdCountrySpec} scopes a
 * row that reaches one through a student. A staff account has a <em>set</em> of
 * countries, and a set has to be compared against another set rather than tested
 * for membership.
 *
 * <h2>The null rule, for the third time and differently</h2>
 *
 * Each table that carries a country means something different by its absence, and
 * copying the rule across is how this goes wrong:
 *
 * <ul>
 *   <li>{@code WAFAR_SPENDING}, {@code AuditTrail} — a null is <b>missing data</b>.
 *       Ministry-only, because showing one attaché's unattributed spending to
 *       another is the leak. See {@code CountryColumnSpec.countryScope}.
 *   <li>{@code COA_ACCOUNT}, {@code FISCAL_YEAR} — a null is a <b>deliberate
 *       statement</b>: the الدليل المحاسبي الموحد, the shared calendar. Visible to
 *       everyone, because hiding it empties the screens that depend on it. See
 *       {@code CountryColumnSpec.countryScopeOrShared}.
 *   <li>{@code UserCountries} — no rows means the account is <b>awaiting
 *       assignment</b>. Visible to any admin, because somebody has to be able to
 *       assign it, and the account nobody can see is the account nobody can fix.
 * </ul>
 *
 * <p>The empty case needs stating explicitly rather than falling out of the
 * arithmetic: "every one of the target's countries is within mine" is
 * <em>vacuously true</em> of an account with no countries, so without the
 * {@code EXISTS} companion on clause 2 an unassigned account would be visible to
 * every caller, including one holding a single attaché. That is the opposite of
 * what clause 3 grants, which is visibility to admins only.
 *
 * <h2>Not to be confused with {@code findEmployeesInCountryScope}</h2>
 *
 * <p>{@code UmsUserRepository.findEmployeesInCountryScope} compares the same two
 * sets with an <em>intersection</em> rule, and that is correct there and would be
 * wrong here. It selects notification recipients: anyone who serves my attaché
 * should hear from me, so overlapping in one country is enough. This selects
 * administrable accounts: someone who also serves fifteen other attachés is not
 * mine to administer. Different questions, different answers — the divergence is
 * deliberate, and neither is a bug to be aligned with the other.
 */
public final class StaffCountrySpec {

    private StaffCountrySpec() {}

    /**
     * A staff account is visible when <b>any</b> of four clauses holds:
     *
     * <ol>
     *   <li>the caller is a super admin or ministry user — sees everyone;
     *   <li>the target's active countries are a non-empty <b>subset</b> of the
     *       caller's;
     *   <li>the target has no active countries and the caller is an admin;
     *   <li>the target <i>is</i> the caller — nobody loses sight of their own
     *       row, and without it a user cannot change their own password.
     * </ol>
     *
     * <p>Clause 2 is a subset rather than an intersection so that a ministry-wide
     * account does not appear on every country officer's screen. {@code jo_admin}
     * holds Jordan alone; {@code admin} holds all eighteen attachés and so is not
     * Jordan's to administer, even though Jordan is among them.
     *
     * <p>{@code isAdmin} deliberately does not bypass the country filter — that
     * decision belongs to {@code CountryScopeService.getScopeForUser}, which grants
     * ministry scope for {@code IsSuperAdmin} only. The flag stays honest because
     * the countries held decide the reach: {@code admin} holds all eighteen and is
     * a ministry admin in practice, while {@code jo_admin} holds one and is a
     * country admin. Clause 3 is the only extra authority the flag carries here.
     */
    public static Specification<UmsUser> administrableBy(CountryScope scope) {
        return (root, query, cb) -> visible(root.get("id"), query, cb, scope);
    }

    /**
     * The same rule applied to a staff <em>session</em>: you may see a session
     * exactly when you may administer the user who opened it.
     *
     * <p>Reaching the user by id rather than joining {@code UmsUser} keeps this a
     * foreign-key comparison — every clause below needs the user's id and nothing
     * else from the row, which is why one predicate serves both roots.
     */
    public static Specification<UmsUserSession> sessionsAdministrableBy(CountryScope scope) {
        return (root, query, cb) -> visible(root.get("user").get("id"), query, cb, scope);
    }

    /**
     * The same rule applied to a staff <em>action</em> ({@code UMSCORE.UserActions}):
     * you may read what somebody did exactly when you may administer them.
     *
     * <p>A log row carries its actor and no country of its own, so there is nothing
     * else it could be filtered on — and the action log is where the country scope
     * would otherwise be undone from the side. "jo_staff granted PAYMENT_BATCH.APPROVE
     * to sa_staff" names an account Jordan may not see, and the ministry's own
     * administration is enumerable from a list of who changed what.
     *
     * <p>An action by an account since deleted stays with the attaché it was made
     * for: {@code UserCountries} rows outlive the account, and an access review
     * reading a leaver's trail is the case that needs them to.
     */
    public static Specification<UmsAuditLog> actionsAdministrableBy(CountryScope scope) {
        return (root, query, cb) -> visible(root.get("userId"), query, cb, scope);
    }

    /**
     * The four clauses, written once against whatever path holds the staff user's
     * id. Nothing here reads any other column of {@code UmsUser}, so the same
     * predicate serves the user list, the session list and the action log without
     * any of them being expressed in terms of the others.
     */
    private static Predicate visible(
            Path<Long> userId, CriteriaQuery<?> query, CriteriaBuilder cb, CountryScope scope) {

        if (scope == null) return cb.disjunction();
        if (scope.isMinistryUser()) return cb.conjunction();

        List<Predicate> clauses = new ArrayList<>();

        // Clause 4 — always yourself.
        if (scope.getUserId() != null) {
            clauses.add(cb.equal(userId, scope.getUserId()));
        }

        List<Integer> allowed = scope.getAllowedCountryIds();
        if (!allowed.isEmpty()) {
            // Clause 2. Both halves are required: the "none outside mine" test
            // alone is satisfied by an account with no countries at all.
            clauses.add(cb.and(
                    cb.not(cb.exists(activeCountries(userId, query, cb, allowed))),
                    cb.exists(activeCountries(userId, query, cb, null))));
        }

        if (scope.isAdmin()) {
            // Clause 3 — awaiting assignment.
            clauses.add(cb.not(cb.exists(activeCountries(userId, query, cb, null))));
        }

        // A caller with no countries, not an admin, and unidentifiable sees
        // nothing. Never "everything".
        return clauses.isEmpty() ? cb.disjunction() : cb.or(clauses.toArray(new Predicate[0]));
    }

    /**
     * Correlated subquery over one user's active country assignments.
     *
     * <p>A fresh instance per call rather than one reused across clauses: the same
     * subquery node appearing twice in one criteria tree is not something to rely
     * on.
     *
     * @param outside when non-null, restricts to assignments <em>outside</em> that
     *                set — the "is not a subset" half of clause 2
     */
    private static Subquery<Long> activeCountries(
            Path<Long> userId, CriteriaQuery<?> query, CriteriaBuilder cb, List<Integer> outside) {
        Subquery<Long> sub = query.subquery(Long.class);
        Root<UmsUserCountry> uc = sub.from(UmsUserCountry.class);

        List<Predicate> where = new ArrayList<>();
        where.add(cb.equal(uc.get("user").get("id"), userId));
        where.add(cb.isTrue(uc.get("isActive")));
        if (outside != null) {
            where.add(cb.not(uc.get("countryId").in(outside)));
        }

        return sub.select(uc.get("id")).where(where.toArray(new Predicate[0]));
    }
}
