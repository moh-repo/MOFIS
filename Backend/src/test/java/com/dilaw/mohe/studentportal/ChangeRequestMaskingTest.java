package com.dilaw.mohe.studentportal;

import com.dilaw.mohe.security.field.FieldAccessService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Student Change Requests show a field's values only to someone who may see that
 * field on the student record (QA retest N3, decision DEC-05).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ChangeRequestMaskingTest {

    @Mock private FieldAccessService fieldAccessService;
    @InjectMocks private StudentChangeRequestService service;

    private static StudentChangeRequestDto request(String field) {
        StudentChangeRequestDto dto = new StudentChangeRequestDto();
        dto.setFieldName(field);
        dto.setOldValue("A1234567");
        dto.setNewValue("B7654321");
        dto.setAppliedValue("B7654321");
        return dto;
    }

    @Test
    @DisplayName("a passport number is hidden from a reviewer the student record masks it for")
    void maskedFieldIsHidden() {
        when(fieldAccessService.mayView(any(), any())).thenReturn(false);

        StudentChangeRequestDto dto = service.maskValues(request("studentPassportNumber"));

        assertThat(dto.isValuesMasked()).isTrue();
        assertThat(dto.getOldValue()).isNull();
        assertThat(dto.getNewValue()).isNull();
        assertThat(dto.getAppliedValue()).isNull();
    }

    @Test
    @DisplayName("the same field is shown to a reviewer who may see it")
    void visibleToTheEntitled() {
        when(fieldAccessService.mayView(any(), any())).thenReturn(true);

        StudentChangeRequestDto dto = service.maskValues(request("studentBirthdate"));

        assertThat(dto.isValuesMasked()).isFalse();
        assertThat(dto.getNewValue()).isEqualTo("B7654321");
    }

    @Test
    @DisplayName("a field with no masking rule on the record is never hidden")
    void unruledFieldIsShown() {
        when(fieldAccessService.mayView(any(), any())).thenReturn(false);

        StudentChangeRequestDto dto = service.maskValues(request("studentName"));

        assertThat(dto.isValuesMasked()).isFalse();
        assertThat(dto.getOldValue()).isEqualTo("A1234567");
    }
}
