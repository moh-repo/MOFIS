package com.dilaw.mohe.repository.spec;

import com.dilaw.mohe.model.StudentTravelTicket;
import com.dilaw.mohe.ums.service.CountryScopeService.CountryScope;
import jakarta.persistence.criteria.JoinType;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;

public final class StudentTravelSpec {

    private StudentTravelSpec() {}

    /**
     * Filters travel tickets by study country via a JOIN to STUDENT_DATA.
     * The join uses the already-mapped lazy @ManyToOne relationship "student".
     */
    public static Specification<StudentTravelTicket> countryScope(CountryScope scope) {
        return (root, query, cb) -> {
            if (scope == null || scope.isMinistryUser()) return cb.conjunction();
            List<Integer> allowed = scope.getAllowedCountryIds();
            if (allowed.isEmpty()) return cb.disjunction();
            query.distinct(true);
            var student = root.join("student", JoinType.INNER);
            return student.get("studyCountryIn").in(allowed);
        };
    }

    /** One student's tickets; no student means no narrowing. */
    public static Specification<StudentTravelTicket> byStudent(Integer studentId) {
        return (root, query, cb) -> studentId == null
                ? cb.conjunction()
                : cb.equal(root.get("studentId"), studentId);
    }

    /** One travel type ({@code ALONE}, {@code WITH_FAMILY}); blank means no narrowing. */
    public static Specification<StudentTravelTicket> byType(String travelType) {
        return (root, query, cb) -> travelType == null || travelType.isBlank()
                ? cb.conjunction()
                : cb.equal(root.get("travelType"), travelType.trim());
    }
}
