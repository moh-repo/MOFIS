package com.dilaw.mohe.studentportal;

import com.dilaw.mohe.model.StudentExtension;
import com.dilaw.mohe.service.StudentExtensionLocalizationService;
import com.dilaw.mohe.service.codelist.CodeListService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.MessageSource;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The student portal in the reader's language (plan W3-04; QA SP-007, SP-009,
 * live #26).
 */
@Tag("integration")
@SpringBootTest
class PortalLanguageTest {

    @Autowired private ObjectMapper objectMapper;
    @Autowired private CodeListService codeLists;
    @Autowired private MessageSource messages;
    @Autowired private StudentExtensionLocalizationService extensionLocalization;

    @Test
    @DisplayName("an extension keeps its own fields and gains its type's and status's code and name")
    void extensionViewIsFlat() throws Exception {
        StudentExtension e = new StudentExtension();
        e.setExtensionType("Academic Extension");
        e.setExtensionStatus("In Progress");

        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(
                new PortalExtensionView(e, "ACADEMIC", "اكاديمي", "IN_PROGRESS", "قيد المعالجة")));

        assertThat(json.path("extensionType").asText()).as("the row stays flat").isEqualTo("Academic Extension");
        assertThat(json.path("extensionStatus").asText()).isEqualTo("In Progress");
        assertThat(json.path("extensionTypeCode").asText()).isEqualTo("ACADEMIC");
        assertThat(json.path("extensionTypeLabel").asText()).isEqualTo("اكاديمي");
        assertThat(json.path("extensionStatusCode").asText()).isEqualTo("IN_PROGRESS");
        assertThat(json.path("extensionStatusLabel").asText()).isEqualTo("قيد المعالجة");
        assertThat(json.has("extension")).as("not nested under 'extension'").isFalse();
    }

    @Test
    @DisplayName("the stored English status resolves to its code through the live lookup (QA SP-009)")
    void storedStatusNameResolvesToCode() {
        StudentExtensionLocalizationService.Localizer names = extensionLocalization.localizer("ar");

        assertThat(names.statusCode("In Progress")).isEqualTo("IN_PROGRESS");
        assertThat(names.typeCode("Financial Extension")).isEqualTo("FINANCIAL");
        assertThat(codeLists.label("extension-status", "IN_PROGRESS", "ar")).isNotEqualTo("In Progress");
        assertThat(codeLists.label("extension-type", "FINANCIAL", "en")).isEqualTo("Financial Extension");
    }

    @Test
    @DisplayName("an information-request kind has an English name (V123)")
    void infoRequestKindInEnglish() {
        assertThat(codeLists.label("info-request-kind", "STATUS_CERT", "en")).isEqualTo("Study status certificate");
        assertThat(codeLists.label("info-request-kind", "STATUS_CERT", "ar")).isNotEqualTo("Study status certificate");
    }

    @Test
    @DisplayName("a refused phone number is explained in both languages, naming the field")
    void phoneRefusalIsLocalized() {
        Object[] args = {"رقم الهاتف", "12", 9, 15};
        String en = messages.getMessage("changeRequest.invalid.PHONE", args, Locale.ENGLISH);
        String ar = messages.getMessage("changeRequest.invalid.PHONE", args, Locale.forLanguageTag("ar"));

        assertThat(en).startsWith("رقم الهاتف").contains("9 to 15 digits");
        assertThat(ar).startsWith("رقم الهاتف").isNotEqualTo(en);
    }
}
