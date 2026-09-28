package com.dilaw.mohe.repository.spec;

import com.dilaw.mohe.model.StudentFinanceArch;
import com.dilaw.mohe.ums.service.CountryScopeService.CountryScope;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;

public final class StudentFinanceArchSpec {

    private StudentFinanceArchSpec() {}

    public static Specification<StudentFinanceArch> countryScope(CountryScope scope) {
        return (root, query, cb) -> {
            if (scope == null || scope.isMinistryUser()) return cb.conjunction();
            List<Integer> allowed = scope.getAllowedCountryIds();
            if (allowed.isEmpty()) return cb.disjunction();
            return root.get("studyCountryInId").in(allowed);
        };
    }
}
