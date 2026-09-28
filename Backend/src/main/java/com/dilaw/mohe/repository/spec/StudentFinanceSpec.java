package com.dilaw.mohe.repository.spec;

import com.dilaw.mohe.model.StudentDatum;
import com.dilaw.mohe.model.StudentFinance;
import com.dilaw.mohe.ums.service.CountryScopeService.CountryScope;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

public final class StudentFinanceSpec {

    private StudentFinanceSpec() {}

    public static Specification<StudentFinance> countryScope(CountryScope scope) {
        return (root, query, cb) -> {
            if (scope == null || scope.isMinistryUser()) return cb.conjunction();
            List<Integer> allowed = scope.getAllowedCountryIds();
            if (allowed.isEmpty()) return cb.disjunction();
            return root.get("studyCountryInId").in(allowed);
        };
    }

    /**
     * The finance row's student exists and is not deleted (live QA H7): a deleted
     * student's row stayed in the Finance Records list. Stated here rather than left to
     * StudentDatum's {@code @Where}, so the rule does not depend on how that reaches a subquery.
     */
    public static Specification<StudentFinance> liveStudent() {
        return (root, query, cb) -> {
            Subquery<Integer> sq = query.subquery(Integer.class);
            Root<StudentDatum> sd = sq.from(StudentDatum.class);
            sq.select(sd.get("studentId")).where(
                    cb.equal(sd.get("studentId"), root.get("id")),
                    cb.or(cb.isNull(sd.get("isDeleted")), cb.isFalse(sd.get("isDeleted"))));
            return cb.exists(sq);
        };
    }

    public static Specification<StudentFinance> searchAndStatus(String term, Integer status) {
        return searchStatusAndDates(term, status, null, null);
    }

    public static Specification<StudentFinance> searchStatusAndDates(
            String term, Integer status, java.time.Instant startDate, java.time.Instant endDate) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (term != null && !term.isBlank()) {
                String like = "%" + term.toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("studentFinName")), like),
                        cb.like(cb.lower(root.get("studentFinId")), like),
                        cb.like(cb.lower(root.get("studyCheckId")), like)
                ));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("studyFinRecievedStatus"), status));
            }
            if (startDate != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("studyCheckDate"), startDate));
            }
            if (endDate != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("studyCheckDate"), endDate));
            }
            return predicates.isEmpty() ? cb.conjunction() : cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    public static Specification<StudentFinance> statusFilter(Integer status) {
        return (root, query, cb) -> {
            if (status == null) return cb.conjunction();
            return cb.equal(root.get("studyFinRecievedStatus"), status);
        };
    }
}
