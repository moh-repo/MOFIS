package com.dilaw.mohe.service;

import com.dilaw.mohe.model.StudentDatum;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import com.dilaw.mohe.exception.LocalizedArgumentException;
import com.dilaw.mohe.exception.LocalizedMessage;

/**
 * Checks that every lookup id on a student points at an existing row, when the student
 * is submitted rather than when the request is approved (review item A2).
 *
 * <p>A student edit is stored as an approval request and written to STUDENT_DATA only on
 * approval. The foreign keys are therefore first enforced by the approver's click: the
 * wizard once sent a passport-issue country from STUDENT_COUNTRY_ALL, which the FK to
 * STUDENT_COUNTRY rejects, and the submitter saw success while the approver got an FK
 * error they could do nothing about.
 *
 * <p>The list mirrors the FK constraints on MOHECORE.STUDENT_DATA. Null and 0 mean "not
 * set" (0 is the column default the forms send for an empty choice) and are not checked.
 */
@Component
public class StudentReferenceValidator {

    private record Ref(String field, String table, String idColumn, Function<StudentDatum, Integer> value) {}

    private static final List<Ref> REFS = List.of(
            new Ref("studentCountryId", "MOHECORE.STUDENT_COUNTRY", "STUDENT_COUNTRY_ID", StudentDatum::getStudentCountryId),
            new Ref("studentPassportIssueCountryId", "MOHECORE.STUDENT_COUNTRY", "STUDENT_COUNTRY_ID", StudentDatum::getStudentPassportIssueCountryId),
            new Ref("studentGovId", "MOHECORE.STUDENT_GOV", "STUDENT_GOV_ID", StudentDatum::getStudentGovId),
            new Ref("studentMudId", "MOHECORE.STUDENT_GOV_MUD", "STUDENT_MUD_ID", StudentDatum::getStudentMudId),
            new Ref("studentGender", "MOHECORE.STUDENT_GENDER", "STUDENT_GENDER_ID", StudentDatum::getStudentGender),
            new Ref("studentDegreeId", "MOHECORE.STUDENT_DEGREE", "STUDENT_DEGREE_ID", StudentDatum::getStudentDegreeId),
            new Ref("studentMajorId", "MOHECORE.STUDENT_MAJOR", "STUDENT_MAJOR_ID", StudentDatum::getStudentMajorId),
            new Ref("studentCurrentLevelId", "MOHECORE.STUDENT_CURRENT_LEVEL", "STUDENT_CURRENT_LEVEL_ID", StudentDatum::getStudentCurrentLevelId),
            new Ref("studentStudyStatusId", "MOHECORE.STUDENT_STUDY_STATUS", "STUDENT_STUDY_STATUS_ID", StudentDatum::getStudentStudyStatusId),
            new Ref("studentAgencyTypeId", "MOHECORE.STUDENT_AGENCY_TYPE", "STUDENT_AGENCY_TYPE_ID", StudentDatum::getStudentAgencyTypeId),
            new Ref("studentMinistryTypeId", "MOHECORE.STUDENT_MINISTRY_TYPE", "STUDENT_MINISTRY_TYPE_ID", StudentDatum::getStudentMinistryTypeId),
            new Ref("studentStudyTypeId", "MOHECORE.STUDENT_STUDY_TYPE", "STUDENT_STUDY_TYPE_ID", StudentDatum::getStudentStudyTypeId),
            new Ref("studentUniversityTypeId", "MOHECORE.STUDENT_UNIVERSITY_TYPE", "STUDENT_UNIVERSITY_TYPE_ID", StudentDatum::getStudentUniversityTypeId),
            new Ref("studyCountryIn", "MOHECORE.STUDY_COUNTRY_IN", "STUDY_COUNTRY_IN_ID", StudentDatum::getStudyCountryIn),
            new Ref("syudyGovIn", "MOHECORE.STUDY_GOV_IN", "STUDY_GOV_IN_ID", StudentDatum::getSyudyGovIn),
            new Ref("studyUniversityIn", "MOHECORE.STUDY_UNIVERSITY_IN", "STUDY_UNIVERSITY_IN_ID", StudentDatum::getStudyUniversityIn),
            new Ref("studyCollegeIn", "MOHECORE.STUDY_COLLEGE_IN", "STUDY_COLLEGE_IN_ID", StudentDatum::getStudyCollegeIn),
            new Ref("studyEducationIn", "MOHECORE.STUDY_EDUCTION_IN", "STUDY_EDUCTION_ID", StudentDatum::getStudyEducationIn),
            new Ref("studyMajorIn", "MOHECORE.STUDY_MAJOR_IN", "STUDY_MAJOR_ID", StudentDatum::getStudyMajorIn)
    );

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /** What {@link #check} found for one field's value. */
    public enum Reference { NOT_A_LOOKUP, EXISTS, MISSING, INACTIVE }

    /**
     * One field's value against its lookup table (live QA M18, 25 Sep 2026): a portal
     * change request naming university 99999999 was accepted and failed only at
     * approval, on the foreign key. Also refuses a row that is no longer in use.
     *
     * @param field a STUDENT_DATA field name, as a change request names it
     * @param value the proposed value, as text
     * @return {@link Reference#NOT_A_LOOKUP} for a field no foreign key backs
     */
    public Reference check(String field, String value) {
        Ref ref = REFS.stream().filter(r -> r.field().equals(field)).findFirst().orElse(null);
        if (ref == null) return Reference.NOT_A_LOOKUP;
        int id;
        try {
            id = Integer.parseInt(value == null ? "" : value.trim());
        } catch (NumberFormatException e) {
            return Reference.MISSING;
        }
        // Table and column names come from the constant list above, never from input.
        List<Boolean> active = jdbcTemplate.queryForList(
                "SELECT CAST(ISNULL(IS_ACTIVE, 1) AS bit) FROM " + ref.table() + " WHERE " + ref.idColumn() + " = ?",
                Boolean.class, id);
        if (active.isEmpty()) return Reference.MISSING;
        return Boolean.FALSE.equals(active.get(0)) ? Reference.INACTIVE : Reference.EXISTS;
    }

    /**
     * @throws IllegalArgumentException naming every field whose id has no row, so the
     *         submitter is told at once instead of the approver later
     */
    public void requireExistingReferences(StudentDatum student) {
        List<String> checks = new ArrayList<>();
        List<Object> args = new ArrayList<>();
        for (Ref ref : REFS) {
            Integer id = ref.value().apply(student);
            if (id == null || id == 0) continue;
            // Table and column names come from the constant list above, never from input.
            checks.add("SELECT '" + ref.field() + "' WHERE NOT EXISTS (SELECT 1 FROM "
                    + ref.table() + " WHERE " + ref.idColumn() + " = ?)");
            args.add(id);
        }
        if (checks.isEmpty()) return;

        List<String> missing = jdbcTemplate.queryForList(String.join(" UNION ALL ", checks), String.class, args.toArray());
        if (!missing.isEmpty()) {
            // Each field by its name, in the reader's language (W3-08): the answer listed the Java names.
            throw new LocalizedArgumentException("These fields refer to a value that does not exist: "
                    + String.join(", ", missing), "student.reference.missing",
                    missing.stream().map(LocalizedMessage::field).toList());
        }
    }
}
