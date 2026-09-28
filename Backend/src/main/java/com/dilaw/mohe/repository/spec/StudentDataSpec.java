package com.dilaw.mohe.repository.spec;

import com.dilaw.mohe.model.StudentDatum;
import com.dilaw.mohe.ums.service.CountryScopeService.CountryScope;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public final class StudentDataSpec {

    private StudentDataSpec() {}

    /**
     * Restricts results to the study countries the current user is allowed to access.
     * Ministry users receive a match-all predicate; country-restricted users are filtered
     * to their assigned study countries via STUDY_COUNTRY_IN.
     * An empty allowed-country list produces a match-none predicate (deny-all).
     */
    public static Specification<StudentDatum> countryScope(CountryScope scope) {
        return (root, query, cb) -> {
            if (scope == null || scope.isMinistryUser()) return cb.conjunction();
            List<Integer> allowed = scope.getAllowedCountryIds();
            if (allowed.isEmpty()) return cb.disjunction();
            return root.get("studyCountryIn").in(allowed);
        };
    }

    /**
     * The list's active / not-active filter. {@code activeStatusId} is the id of the
     * ACTIVE row of {@code STUDENT_STUDY_STATUS}, resolved by CODE by the caller —
     * the literal 1 this used to test happened to be right, but no filter should
     * assume an id.
     */
    public static Specification<StudentDatum> statusFilter(Integer statusId, Integer activeStatusId) {
        return (root, query, cb) -> {
            if (statusId == null) return cb.conjunction();
            if (activeStatusId == null) return cb.conjunction();
            if (statusId.equals(activeStatusId)) return cb.equal(root.get("studentStudyStatusId"), activeStatusId);
            return cb.or(
                    cb.isNull(root.get("studentStudyStatusId")),
                    cb.notEqual(root.get("studentStudyStatusId"), activeStatusId)
            );
        };
    }

    public static Specification<StudentDatum> searchTerm(String term) {
        return (root, query, cb) -> {
            if (term == null || term.isBlank()) return cb.conjunction();
            String like = "%" + term.toLowerCase() + "%";
            return cb.or(
                    cb.like(cb.lower(root.get("studentName")), like),
                    cb.like(cb.lower(root.get("studentPassportNumber")), like),
                    cb.like(cb.lower(root.get("studentMajorName")), like)
            );
        };
    }

    /**
     * What a student picker searches: the name, or the id when the term is one.
     *
     * <p>Deliberately narrower than {@link #searchTerm(String)}, which also looks
     * in the passport number and the major. A picker answers with nothing but an
     * id and a name, and is open to finance roles that hold no student
     * permission — so it must not become a way to probe passport numbers by
     * prefix.
     */
    public static Specification<StudentDatum> pickerTerm(String term) {
        return (root, query, cb) -> {
            if (term == null || term.isBlank()) return cb.conjunction();
            String trimmed = term.trim();
            Predicate byName = cb.like(cb.lower(root.get("studentName")), "%" + trimmed.toLowerCase() + "%");
            try {
                // The picker labels each option "id — name", so a digits-only term
                // is most likely the id the user is reading off a record.
                return cb.or(byName, cb.equal(root.get("studentId"), Integer.valueOf(trimmed)));
            } catch (NumberFormatException notAnId) {
                return byName;
            }
        };
    }

    /**
     * Exactly these students, for resolving ids a list is already showing.
     *
     * <p>A picker holds one page of options, so the names on a saved record are
     * usually not among them. This fetches the handful the screen needs by id,
     * still through the country scope: an id outside it simply does not come
     * back. An empty collection matches nothing rather than everything.
     */
    public static Specification<StudentDatum> idsIn(Collection<Integer> ids) {
        return (root, query, cb) -> {
            if (ids == null) return cb.conjunction();
            if (ids.isEmpty()) return cb.disjunction();
            return root.get("studentId").in(ids);
        };
    }

    /**
     * Restricts results based on RECORD_VISIBILITY.
     * Admin/SuperAdmin users see all records regardless of visibility.
     * All other users only see records tagged 'ALL'.
     */
    public static Specification<StudentDatum> visibilityFilter(CountryScope scope) {
        return (root, query, cb) -> {
            if (scope == null || scope.isAdmin()) return cb.conjunction();
            return cb.equal(root.get("recordVisibility"), "ALL");
        };
    }

    public static Specification<StudentDatum> fieldFilters(Integer studentId, String name, String passport) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (studentId != null) {
                predicates.add(cb.equal(root.get("studentId"), studentId));
            }
            if (name != null && !name.isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("studentName")), "%" + name.toLowerCase() + "%"));
            }
            if (passport != null && !passport.isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("studentPassportNumber")), "%" + passport.toLowerCase() + "%"));
            }
            return predicates.isEmpty() ? cb.conjunction() : cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
