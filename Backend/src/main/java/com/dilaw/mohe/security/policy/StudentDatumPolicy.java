package com.dilaw.mohe.security.policy;

import com.dilaw.mohe.model.StudentDatum;
import com.dilaw.mohe.repository.StudentDataRepository;
import org.springframework.stereotype.Component;

import java.io.Serializable;
import java.util.Optional;

/**
 * Student records: VIEW, EDIT and DELETE follow the study country
 * ({@code STUDY_COUNTRY_IN}, the destination — not the nationality).
 *
 * <p>Replaces the private {@code validateStudyCountryScope} that
 * {@code StudentDataController} and {@code StudentFamilyController} each kept
 * (R-17). The row-level policy remains the backstop: a student the caller
 * cannot see never reaches this class, and answers 404 from the service.
 */
@Component
public class StudentDatumPolicy implements AccessPolicy<StudentDatum> {

    public static final String NAME = "StudentDatum";

    private final StudentDataRepository studentDataRepository;

    public StudentDatumPolicy(StudentDataRepository studentDataRepository) {
        this.studentDataRepository = studentDataRepository;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public Class<StudentDatum> resourceType() {
        return StudentDatum.class;
    }

    @Override
    public Decision decide(AccessSubject subject, StudentDatum student, String action) {
        if (!subject.isAuthenticated()) return Decision.deny("Access denied: not signed in");
        if (student == null) return Decision.allow();
        return CountryScopedPolicy.inScope(subject, "Student", student.getStudyCountryIn());
    }

    @Override
    public Optional<StudentDatum> load(Serializable id) {
        if (id instanceof Number n) return studentDataRepository.findById(n.intValue());
        if (id instanceof String s) {
            try {
                return studentDataRepository.findById(Integer.parseInt(s));
            } catch (NumberFormatException ignored) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }
}
