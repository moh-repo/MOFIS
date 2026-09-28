package com.dilaw.mohe.dto;

import com.dilaw.mohe.model.StudentDatum;
import com.dilaw.mohe.security.field.FieldAccessService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The edit wizard's DTO carries what the wizard binds, and masks what the record masks
 * (live QA H3, 23 Sep 2026).
 */
class StudentDatumDetailDtoTest {

    /** The nine columns the wizard used to clear because the list DTO never carried them. */
    private static final Set<String> CLEARED_BY_THE_PROBE = Set.of(
            "amountCurrency", "studentAgencyTypeId", "studentMinistryTypeId", "studentStudyTypeId",
            "studentUniversityTypeId", "studentBirthdatePlace", "studentPassportIssueDate",
            "studentPassportExpireDate", "studentPassportIssueCountryId");

    private static Set<String> fieldsOf(Class<?> type) {
        Set<String> names = new HashSet<>();
        for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
            Arrays.stream(c.getDeclaredFields()).map(java.lang.reflect.Field::getName).forEach(names::add);
        }
        return names;
    }

    @Test
    @DisplayName("the nine fields the probe cleared, and the contact fields, round-trip")
    void carriesWhatTheWizardBinds() {
        StudentDatum s = new StudentDatum();
        s.setStudentId(1003);
        s.setAmountCurrency(1);
        s.setStudentAgencyTypeId(1);
        s.setStudentMinistryTypeId(1);
        s.setStudentStudyTypeId(2);
        s.setStudentUniversityTypeId(2);
        s.setStudentBirthdatePlace("1");
        Instant issued = Instant.parse("2026-05-26T22:19:49.557Z");
        Instant expires = Instant.parse("2026-05-30T00:00:00Z");
        s.setStudentPassportIssueDate(issued);
        s.setStudentPassportExpireDate(expires);
        s.setStudentPassportIssueCountryId(1);
        s.setStudentEmail("student@example.org");
        s.setStudentCellphone("0777000000");

        StudentDatumDetailDto dto = StudentDatumDetailDto.fromEntity(s);

        assertThat(dto.getStudentId()).isEqualTo(1003);
        assertThat(dto.getAmountCurrency()).isEqualTo(1);
        assertThat(dto.getStudentAgencyTypeId()).isEqualTo(1);
        assertThat(dto.getStudentMinistryTypeId()).isEqualTo(1);
        assertThat(dto.getStudentStudyTypeId()).isEqualTo(2);
        assertThat(dto.getStudentUniversityTypeId()).isEqualTo(2);
        assertThat(dto.getStudentBirthdatePlace()).isEqualTo("1");
        assertThat(dto.getStudentPassportIssueDate()).isEqualTo(issued);
        assertThat(dto.getStudentPassportExpireDate()).isEqualTo(expires);
        assertThat(dto.getStudentPassportIssueCountryId()).isEqualTo(1);
        assertThat(dto.getStudentEmail()).isEqualTo("student@example.org");
        assertThat(dto.getStudentCellphone()).isEqualTo("0777000000");
        assertThat(fieldsOf(StudentDatumDetailDto.class)).containsAll(CLEARED_BY_THE_PROBE);
    }

    @Test
    @DisplayName("every field the record masks is masked on the detail DTO too (R-18 / QA N3)")
    void masksWhatTheRecordMasks() {
        Set<String> dtoFields = fieldsOf(StudentDatumDetailDto.class);
        Set<String> maskedOnRecord = FieldAccessService.rulesOf(StudentDatum.class).keySet();
        Set<String> maskedOnDto = FieldAccessService.rulesOf(StudentDatumDetailDto.class).keySet();

        Set<String> shouldBeMasked = maskedOnRecord.stream().filter(dtoFields::contains).collect(Collectors.toSet());
        assertThat(shouldBeMasked).as("sanity: the detail DTO carries the masked contact and passport fields")
                .contains("studentPassportNumber", "studentBirthdatePlace", "studentPassportIssueDate",
                        "studentEmail", "studentCellphone", "studentHomeNumber", "amountCurrency");
        assertThat(maskedOnDto).containsAll(shouldBeMasked);
    }
}
