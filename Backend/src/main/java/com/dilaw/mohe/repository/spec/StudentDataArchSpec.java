package com.dilaw.mohe.repository.spec;

import com.dilaw.mohe.model.StudentDataArch;
import com.dilaw.mohe.ums.service.CountryScopeService.CountryScope;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;

public final class StudentDataArchSpec {

    private StudentDataArchSpec() {}

    public static Specification<StudentDataArch> countryScope(CountryScope scope) {
        return (root, query, cb) -> {
            if (scope == null || scope.isMinistryUser()) return cb.conjunction();
            List<Integer> allowed = scope.getAllowedCountryIds();
            if (allowed.isEmpty()) return cb.disjunction();
            return root.get("studyCountryIn").in(allowed);
        };
    }

    public static Specification<StudentDataArch> nameContains(String name) {
        return (root, query, cb) -> {
            if (name == null || name.isBlank()) return cb.conjunction();
            return cb.like(cb.lower(root.get("studentName")), "%" + name.toLowerCase() + "%");
        };
    }
}
