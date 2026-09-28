package com.dilaw.mohe.dto;

import com.dilaw.mohe.model.StudentDatum;
import com.dilaw.mohe.security.field.FieldAccessService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The student list's DTO carries what the list shows, and masks what the record masks.
 */
class StudentDatumDtoTest {

    @Test
    @DisplayName("amount, birth date and the added date reach the list (QA DSH-001)")
    void copiesTheFieldsItUsedToDrop() {
        StudentDatum s = new StudentDatum();
        s.setStudentId(7);
        s.setStudentAmount(1500);
        Instant born = Instant.parse("2000-01-01T12:00:00Z");
        Instant added = Instant.parse("2026-09-01T08:00:00Z");
        s.setStudentBirthdate(born);
        s.setStudentMidate(added);

        StudentDatumDto dto = StudentDatumDto.fromEntity(s);

        assertThat(dto.getStudentAmount()).as("was Double against Integer: never copied").isEqualTo(1500);
        assertThat(dto.getStudentBirthdate()).as("was LocalDate against Instant: never copied").isEqualTo(born);
        assertThat(dto.getStudentMidate()).as("the dashboard's Added Date").isEqualTo(added);
    }

    @Test
    @DisplayName("every field the record masks is masked on the DTO too (R-18 / QA N3)")
    void masksWhatTheRecordMasks() {
        Set<String> dtoFields = Arrays.stream(StudentDatumDto.class.getDeclaredFields())
                .map(java.lang.reflect.Field::getName).collect(Collectors.toSet());
        Set<String> maskedOnRecord = FieldAccessService.rulesOf(StudentDatum.class).keySet();
        Set<String> maskedOnDto = FieldAccessService.rulesOf(StudentDatumDto.class).keySet();

        Set<String> shouldBeMasked = maskedOnRecord.stream().filter(dtoFields::contains).collect(Collectors.toSet());
        assertThat(shouldBeMasked).as("sanity: the DTO carries some masked fields")
                .contains("studentPassportNumber", "studentBirthdate", "studentGovId", "studentAmount");
        assertThat(maskedOnDto).containsAll(shouldBeMasked);
    }
}
