package com.dilaw.mohe.service;

import com.dilaw.mohe.model.ExtensionStatus;
import com.dilaw.mohe.model.ExtensionType;
import com.dilaw.mohe.model.StudentExtension;
import com.dilaw.mohe.repository.ExtensionStatusRepository;
import com.dilaw.mohe.repository.ExtensionTypeRepository;
import com.dilaw.mohe.studentportal.PortalExtensionView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * A stored extension type or status resolves to its lookup code whatever form it
 * was stored in (QA SP-009).
 *
 * <p>STUDENT_EXTENSION holds the staff form's option value, which is the English
 * name ("In Progress"), and older rows hold codes ("APPROVED") or names nobody
 * uses any more. The portal names both from the shared code lists, so it needs
 * the code; the staff screens keep reading the label.
 */
class StudentExtensionLocalizationServiceTest {

    private StudentExtensionLocalizationService service;

    @BeforeEach
    void lookups() {
        ExtensionTypeRepository types = mock(ExtensionTypeRepository.class);
        ExtensionStatusRepository statuses = mock(ExtensionStatusRepository.class);
        when(types.findAllActiveOrderedByDisplay()).thenReturn(List.of(
                type("ACADEMIC", "Academic Extension", "اكاديمي"),
                type("FINANCIAL", "Financial Extension", "مالي")));
        when(statuses.findAllActiveOrderedByDisplay()).thenReturn(List.of(
                status("APPROVED", "Approved", "موافق عليه"),
                status("IN_PROGRESS", "In Progress", "قيد المعالجة")));
        service = new StudentExtensionLocalizationService(types, statuses);
    }

    @Test
    @DisplayName("the code, the English name and the Arabic name all resolve to the code")
    void anyStoredFormResolvesToTheCode() {
        StudentExtensionLocalizationService.Localizer names = service.localizer("en");

        assertThat(names.statusCode("IN_PROGRESS")).isEqualTo("IN_PROGRESS");
        assertThat(names.statusCode("In Progress")).isEqualTo("IN_PROGRESS");
        assertThat(names.statusCode(" in progress ")).as("case and spacing do not matter").isEqualTo("IN_PROGRESS");
        assertThat(names.statusCode("قيد المعالجة")).isEqualTo("IN_PROGRESS");
        assertThat(names.typeCode("Financial Extension")).isEqualTo("FINANCIAL");
        assertThat(names.typeCode("financial")).isEqualTo("FINANCIAL");
    }

    @Test
    @DisplayName("a value no active row matches has no code, and is still labelled as itself")
    void unknownValuesHaveNoCode() {
        StudentExtensionLocalizationService.Localizer names = service.localizer("ar");

        assertThat(names.statusCode("ACTIVE")).isNull();
        assertThat(names.statusCode(null)).isNull();
        assertThat(names.statusCode("  ")).isNull();
        assertThat(names.statusLabel("ACTIVE")).as("the label falls back to the stored value").isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("the portal's view carries both codes and both labels in the request language")
    void portalViewIsResolved() {
        StudentExtension e = new StudentExtension();
        e.setExtensionType("Financial Extension");
        e.setExtensionStatus("In Progress");

        PortalExtensionView ar = PortalExtensionView.of(e, service.localizer("ar"));
        PortalExtensionView en = PortalExtensionView.of(e, service.localizer("en"));

        assertThat(ar.getExtensionTypeCode()).isEqualTo("FINANCIAL");
        assertThat(ar.getExtensionStatusCode()).isEqualTo("IN_PROGRESS");
        assertThat(ar.getExtensionTypeLabel()).isEqualTo("مالي");
        assertThat(ar.getExtensionStatusLabel()).isEqualTo("قيد المعالجة");
        assertThat(en.getExtensionStatusLabel()).isEqualTo("In Progress");
        assertThat(en.getExtension()).as("the row itself is untouched").isSameAs(e);
    }

    private static ExtensionType type(String code, String en, String ar) {
        ExtensionType t = new ExtensionType();
        t.setExtensionTypeCode(code);
        t.setExtensionTypeNameEn(en);
        t.setExtensionTypeNameAr(ar);
        return t;
    }

    private static ExtensionStatus status(String code, String en, String ar) {
        ExtensionStatus s = new ExtensionStatus();
        s.setExtensionStatusCode(code);
        s.setExtensionStatusNameEn(en);
        s.setExtensionStatusNameAr(ar);
        return s;
    }
}
