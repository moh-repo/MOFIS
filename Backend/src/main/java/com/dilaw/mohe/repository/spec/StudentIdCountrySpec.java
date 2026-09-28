package com.dilaw.mohe.repository.spec;

import com.dilaw.mohe.model.StudentDatum;
import com.dilaw.mohe.ums.service.CountryScopeService.CountryScope;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;

/**
 * Generic country-scope Specification for entities that carry only a raw
 * {@code studentId} integer FK (no @ManyToOne mapping to StudentDatum).
 * Uses a correlated subquery so no relationship annotation is needed.
 */
public final class StudentIdCountrySpec {

    private StudentIdCountrySpec() {}

    public static <T> Specification<T> countryScope(CountryScope scope) {
        return (root, query, cb) -> {
            if (scope == null || scope.isMinistryUser()) return cb.conjunction();
            List<Integer> allowed = scope.getAllowedCountryIds();
            if (allowed.isEmpty()) return cb.disjunction();
            Subquery<Integer> sub = query.subquery(Integer.class);
            Root<StudentDatum> sd = sub.from(StudentDatum.class);
            sub.select(sd.get("studentId"))
               .where(sd.get("studyCountryIn").in(allowed));
            return root.get("studentId").in(sub);
        };
    }
}
