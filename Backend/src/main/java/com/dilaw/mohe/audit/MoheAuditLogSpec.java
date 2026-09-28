package com.dilaw.mohe.audit;

import com.dilaw.mohe.ums.service.CountryScopeService.CountryScope;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Filters for the audit log, country scope included.
 *
 * <p>The service composes every read from these rather than from derived query
 * methods. That is the point: the previous shape had nine separate repository
 * methods, so scoping meant remembering to change nine things and a tenth added
 * later would silently be unscoped. One {@code and(countryScope(scope))} in a
 * single place cannot be forgotten.
 */
public final class MoheAuditLogSpec {

    private MoheAuditLogSpec() {}

    /**
     * The caller's country scope.
     *
     * <p><b>A NULL country is ministry-only.</b> Those rows describe records with
     * no country — UMS user accounts and role assignments — and are the
     * ministry's own administrative history. A country officer not seeing them
     * costs nothing; the alternative treats "country unknown" as "visible to
     * everyone", which is how the leak this fixes would come back.
     */
    public static Specification<MoheAuditLog> countryScope(CountryScope scope) {
        return (root, query, cb) -> {
            if (scope == null || scope.isMinistryUser()) return cb.conjunction();
            List<Integer> allowed = scope.getAllowedCountryIds();
            if (allowed.isEmpty()) return cb.disjunction();
            return root.get("countryId").in(allowed);
        };
    }

    public static Specification<MoheAuditLog> tableName(String tableName) {
        return (root, query, cb) -> tableName == null || tableName.isBlank()
                ? cb.conjunction() : cb.equal(root.get("tableName"), tableName);
    }

    public static Specification<MoheAuditLog> recordId(String recordId) {
        return (root, query, cb) -> recordId == null || recordId.isBlank()
                ? cb.conjunction() : cb.equal(root.get("recordId"), recordId);
    }

    public static Specification<MoheAuditLog> operation(String operation) {
        return (root, query, cb) -> operation == null || operation.isBlank()
                ? cb.conjunction() : cb.equal(root.get("operation"), operation);
    }

    public static Specification<MoheAuditLog> changedBy(String changedBy) {
        return (root, query, cb) -> changedBy == null || changedBy.isBlank()
                ? cb.conjunction() : cb.equal(root.get("changedBy"), changedBy);
    }

    public static Specification<MoheAuditLog> changedBetween(LocalDateTime from, LocalDateTime to) {
        return (root, query, cb) -> {
            if (from == null && to == null) return cb.conjunction();
            if (from == null) return cb.lessThanOrEqualTo(root.get("changedAt"), to);
            if (to == null) return cb.greaterThanOrEqualTo(root.get("changedAt"), from);
            return cb.between(root.get("changedAt"), from, to);
        };
    }
}
