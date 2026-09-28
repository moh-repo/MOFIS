package com.dilaw.mohe.repository.spec;

import com.dilaw.mohe.model.StudentInfoRequest;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;

public final class StudentInfoRequestSpec {

    private StudentInfoRequestSpec() {}

    public static Specification<StudentInfoRequest> filter(
            String createdBy,
            String status,
            String requestType,
            Integer studentId,
            Instant dateFrom,
            Instant dateTo) {

        return (root, query, cb) -> {
            var predicates = new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();

            if (createdBy != null) {
                predicates.add(cb.equal(root.get("createdBy"), createdBy));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (requestType != null) {
                predicates.add(cb.equal(root.get("requestType"), requestType));
            }
            if (studentId != null) {
                predicates.add(cb.equal(root.get("studentId"), studentId));
            }
            if (dateFrom != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), dateFrom));
            }
            if (dateTo != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), dateTo));
            }

            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
    }
}
