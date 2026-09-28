package com.dilaw.mohe.studentportal;

import com.dilaw.mohe.model.StudentDatum;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Live QA L14 (P21, 25 Sep 2026): the portal's "new change request" dialog showed the
 * current e-mail and phone numbers empty although the record held them. The dialog
 * reads each field's current value from {@code GET /me/data}, which left the four
 * contact fields out.
 */
class PortalMyDataFieldsTest {

    @Test
    @DisplayName("every field a student may ask to change is in the answer the dialog reads")
    void everyChangeableFieldIsThere() {
        for (String field : StudentChangeRequestService.ALLOWED_FIELDS) {
            assertThat(BeanUtils.getPropertyDescriptor(PortalMyDataDto.Student.class, field))
                    .as(field + " is missing from PortalMyDataDto.Student").isNotNull();
        }
    }

    @Test
    @DisplayName("the contact fields are copied from the record")
    void contactFieldsCopied() {
        StudentDatum d = new StudentDatum();
        d.setStudentEmail("st2@example.org");
        d.setStudentCellphone("+20 100 123 4567");
        d.setStudentHomeNumber("+20 2 1234 5678");
        d.setStudentEmergencyContact("+20 111 222 3333");

        PortalMyDataDto.Student s = PortalMyDataDto.of(d, null).getStudent();

        assertThat(s.getStudentEmail()).isEqualTo("st2@example.org");
        assertThat(s.getStudentCellphone()).isEqualTo("+20 100 123 4567");
        assertThat(s.getStudentHomeNumber()).isEqualTo("+20 2 1234 5678");
        assertThat(s.getStudentEmergencyContact()).isEqualTo("+20 111 222 3333");
    }
}
