package com.dilaw.mohe.alerteventcases.repository.spec;

import com.dilaw.mohe.alerteventcases.model.AlertEventCase;
import com.dilaw.mohe.ums.service.CountryScopeService.CountryScope;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public final class AlertEventCaseSpec {

    private AlertEventCaseSpec() {}

    public static Specification<AlertEventCase> notDeleted() {
        return (root, query, cb) -> cb.equal(root.get("isDeleted"), false);
    }

    public static Specification<AlertEventCase> countryScope(CountryScope scope) {
        return (root, query, cb) -> {
            if (scope == null || scope.isMinistryUser()) return cb.conjunction();
            List<Integer> allowed = scope.getAllowedCountryIds();
            if (allowed.isEmpty()) return cb.disjunction();
            return root.get("studyCountryInId").in(allowed);
        };
    }

    public static Specification<AlertEventCase> byStatus(String status) {
        return (root, query, cb) -> {
            if (status == null || status.isBlank()) return cb.conjunction();
            return cb.equal(root.get("status"), status.toUpperCase());
        };
    }

    public static Specification<AlertEventCase> byScenarioCode(String scenarioCode) {
        return (root, query, cb) -> {
            if (scenarioCode == null || scenarioCode.isBlank()) return cb.conjunction();
            return cb.equal(root.get("scenario").get("scenarioCode"), scenarioCode);
        };
    }

    public static Specification<AlertEventCase> bySeverity(String severity) {
        return (root, query, cb) -> {
            if (severity == null || severity.isBlank()) return cb.conjunction();
            return cb.equal(root.get("scenario").get("severity"), severity.toUpperCase());
        };
    }

    public static Specification<AlertEventCase> byStudentId(Integer studentId) {
        return (root, query, cb) -> {
            if (studentId == null) return cb.conjunction();
            return cb.equal(root.get("student").get("studentId"), studentId);
        };
    }

    public static Specification<AlertEventCase> byStudentName(String name) {
        return (root, query, cb) -> {
            if (name == null || name.isBlank()) return cb.conjunction();
            return cb.like(cb.lower(root.get("student").get("studentName")), "%" + name.toLowerCase() + "%");
        };
    }

    public static Specification<AlertEventCase> byCountryId(Integer countryId) {
        return (root, query, cb) -> {
            if (countryId == null) return cb.conjunction();
            return cb.equal(root.get("studyCountryInId"), countryId);
        };
    }

    public static Specification<AlertEventCase> byDateRange(Instant from, Instant to) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (from != null) predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from));
            if (to != null) predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), to));
            return predicates.isEmpty() ? cb.conjunction() : cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
