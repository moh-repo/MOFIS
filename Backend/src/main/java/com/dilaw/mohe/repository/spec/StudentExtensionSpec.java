package com.dilaw.mohe.repository.spec;

import com.dilaw.mohe.model.StudentDatum;
import com.dilaw.mohe.model.StudentExtension;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

public final class StudentExtensionSpec {

    private StudentExtensionSpec() {}

    public static Specification<StudentExtension> filter(
            Integer studentId,
            Integer filterStudentId,
            String extensionType,
            String status,
            String passport) {

        return (root, query, cb) -> {
            var predicates = new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();

            if (studentId != null) {
                predicates.add(cb.equal(root.get("studentId"), studentId));
            }
            if (filterStudentId != null) {
                predicates.add(cb.equal(root.get("studentId"), filterStudentId));
            }
            if (StringUtils.hasText(extensionType)) {
                predicates.add(cb.equal(cb.lower(root.get("extensionType")), extensionType.toLowerCase()));
            }
            if (StringUtils.hasText(status)) {
                predicates.add(cb.equal(cb.lower(root.get("extensionStatus")), status.toLowerCase()));
            }
            if (StringUtils.hasText(passport)) {
                Subquery<Integer> passSub = query.subquery(Integer.class);
                Root<StudentDatum> sd = passSub.from(StudentDatum.class);
                passSub.select(sd.get("studentId"))
                       .where(cb.like(cb.lower(sd.get("studentPassportNumber")),
                               "%" + passport.toLowerCase() + "%"));
                predicates.add(root.get("studentId").in(passSub));
            }

            if (!Boolean.class.equals(query.getResultType()) && !Long.class.equals(query.getResultType())) {
                query.orderBy(
                        cb.desc(root.get("extensionStartDate")),
                        cb.desc(root.get("createdDate")),
                        cb.desc(root.get("extensionId"))
                );
            }

            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
    }
}
