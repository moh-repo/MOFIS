package com.dilaw.mohe.repository.spec;

import com.dilaw.mohe.studentportal.model.StudentChangeRequest;
import org.springframework.data.jpa.domain.Specification;

import java.util.Set;

public final class StudentChangeRequestSpec {

    private StudentChangeRequestSpec() {}

    public static Specification<StudentChangeRequest> filter(
            String status,
            Integer studentId,
            Set<Integer> nameMatchIds) {

        return (root, query, cb) -> {
            var predicates = new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();

            predicates.add(cb.isFalse(root.get("isDeleted")));

            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (studentId != null) {
                predicates.add(cb.equal(root.get("studentId"), studentId));
            }
            if (nameMatchIds != null && !nameMatchIds.isEmpty()) {
                predicates.add(root.get("studentId").in(nameMatchIds));
            }

            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
    }
}
