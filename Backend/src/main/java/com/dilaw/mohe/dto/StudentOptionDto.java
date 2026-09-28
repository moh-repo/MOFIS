package com.dilaw.mohe.dto;

import com.dilaw.mohe.model.StudentDatum;

/**
 * A student as a dropdown option: an id and a name, and nothing else.
 *
 * <p>Screens that are not student screens still have to name a student — the
 * finance recipients form picks the one a payment was collected on behalf of.
 * They were reaching for {@link StudentDatumDto}, which carries the passport
 * number and the date of birth and is rightly gated behind the student
 * permissions. This is what a picker actually needs, so it can be read by the
 * finance roles without handing them a student's personal record.
 */
public record StudentOptionDto(Integer studentId, String studentName) {

    public static StudentOptionDto fromEntity(StudentDatum entity) {
        return entity == null ? null : new StudentOptionDto(entity.getStudentId(), entity.getStudentName());
    }
}
