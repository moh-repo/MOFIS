package com.dilaw.mohe.ums.repository.spec;

import com.dilaw.mohe.ums.model.UmsUser;
import org.springframework.data.jpa.domain.Specification;

/**
 * The search filters of {@code /ums-admin/users}, as specifications.
 *
 * <p>They were a single JPQL query with {@code :keyword IS NULL OR ...} branches.
 * Country scope cannot be bolted onto that — it is a correlated subquery over a
 * second table, and it has to compose with whichever filters the caller supplied
 * rather than be another nullable parameter. Splitting them apart is what lets
 * {@link StaffCountrySpec#administrableBy} be {@code and}-ed onto every read path
 * from one place in {@code UmsUserService.scoped()}.
 */
public final class UmsUserSpecs {

    private UmsUserSpecs() {}

    public static Specification<UmsUser> notDeleted() {
        return (root, query, cb) -> cb.isFalse(root.get("isDeleted"));
    }

    /** Substring match across username, email and both name parts. */
    public static Specification<UmsUser> keyword(String keyword) {
        if (keyword == null || keyword.isBlank()) return null;
        String like = "%" + keyword.trim().toLowerCase() + "%";
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("userName")), like),
                cb.like(cb.lower(root.get("email")), like),
                cb.like(cb.lower(root.get("firstName")), like),
                cb.like(cb.lower(root.get("lastName")), like));
    }

    public static Specification<UmsUser> status(String status) {
        if (status == null || status.isBlank()) return null;
        return (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    /**
     * {@code ADMIN} | {@code REGULAR}; anything else means no filter.
     *
     * <p>Elevated means {@code isAdmin} or {@code isSuperAdmin}, and both are
     * nullable in the database, so each test needs its null coalesced — a plain
     * {@code isFalse} would drop every row that has never had the flag set.
     *
     * <p><b>{@code cb.equal(…, true)} rather than {@code cb.isTrue(…)}.</b> SQL
     * Server has no boolean type, and once a path goes through {@code coalesce}
     * Hibernate no longer knows to render the comparison: {@code isTrue} emits a
     * bare {@code coalesce(IsAdmin, ?)} where a predicate is expected, and the
     * statement fails to parse. A plain boolean column is fine — it is the
     * coalesce that loses the type.
     */
    public static Specification<UmsUser> privilege(String privilege) {
        if (privilege == null || privilege.isBlank()) return null;
        String p = privilege.trim().toUpperCase();
        if ("ADMIN".equals(p)) {
            return (root, query, cb) -> cb.or(
                    cb.equal(cb.coalesce(root.get("isAdmin"), false), true),
                    cb.equal(cb.coalesce(root.get("isSuperAdmin"), false), true));
        }
        if ("REGULAR".equals(p)) {
            return (root, query, cb) -> cb.and(
                    cb.equal(cb.coalesce(root.get("isAdmin"), false), false),
                    cb.equal(cb.coalesce(root.get("isSuperAdmin"), false), false));
        }
        return null;
    }

    /** Accounts holding one attaché actively (N-03: approver candidates of a country). */
    public static Specification<UmsUser> holdsCountry(Integer countryId) {
        if (countryId == null) return null;
        return (root, query, cb) -> {
            jakarta.persistence.criteria.Subquery<Long> sub = query.subquery(Long.class);
            jakarta.persistence.criteria.Root<com.dilaw.mohe.ums.model.UmsUserCountry> uc =
                    sub.from(com.dilaw.mohe.ums.model.UmsUserCountry.class);
            sub.select(uc.get("id")).where(
                    cb.equal(uc.get("user").get("id"), root.get("id")),
                    cb.isTrue(uc.get("isActive")),
                    cb.equal(uc.get("countryId"), countryId));
            return cb.exists(sub);
        };
    }

    /** Elevated accounts only, for {@code GET /ums/users/admins}. */
    public static Specification<UmsUser> adminOnly() {
        return (root, query, cb) -> cb.equal(cb.coalesce(root.get("isAdmin"), false), true);
    }
}
