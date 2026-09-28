package com.dilaw.mohe.exception;

import com.dilaw.mohe.util.FileUploadValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;

import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/**
 * Live QA L13 (P30, P31; 25 Sep 2026): with Arabic chosen, the portal's upload,
 * authenticator, inquiry-type and page-size refusals were English, and "10 حرفاً" was
 * the password rule's Arabic. The real bundles are used, so a key missing from either
 * language fails here.
 */
class PortalRefusalsInArabicTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();
    private final ResourceBundleMessageSource messages = new ResourceBundleMessageSource();

    @BeforeEach
    void wireRealBundles() {
        messages.setBasename("messages");
        messages.setDefaultEncoding("UTF-8");
        messages.setFallbackToSystemLocale(false);
        ReflectionTestUtils.setField(handler, "messageSource", messages);
    }

    private static WebRequest request(String acceptLanguage) {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/mohe/api/v1/student-portal/me/documents");
        req.addHeader("Accept-Language", acceptLanguage);
        return new ServletWebRequest(req);
    }

    private static boolean hasArabic(String s) {
        return s.codePoints().anyMatch(c -> Character.UnicodeBlock.of(c) == Character.UnicodeBlock.ARABIC);
    }

    @Test
    @DisplayName("every new refusal key exists in both languages, and the Arabic one is Arabic")
    void everyKeyInBothLanguages() {
        List<String> keys = List.of("upload.typeNotAllowed", "upload.contentMismatch", "upload.unreadable",
                "upload.extensionMismatch", "portal.upload.badType",
                "portal.mfa.alreadyEnabled", "portal.mfa.startFirst", "portal.mfa.codeMismatchSetup",
                "portal.mfa.notEnabled", "portal.mfa.codeMismatch", "portal.mfa.tooMany",
                "codeList.notAllowed", "validation.mobileRequired");
        for (String key : keys) {
            String en = messages.getMessage(key, new Object[]{"x"}, null, Locale.ENGLISH);
            String ar = messages.getMessage(key, new Object[]{"x"}, null, Locale.forLanguageTag("ar"));
            assertThat(en).as(key + " (en)").isNotNull();
            assertThat(ar).as(key + " (ar)").isNotNull().isNotEqualTo(en);
            assertThat(hasArabic(ar)).as(key + " (ar) is Arabic").isTrue();
        }
    }

    @Test
    @DisplayName("P30: a refused upload is answered in Arabic")
    void uploadRefusal() {
        IllegalArgumentException ex = catchThrowableOfType(IllegalArgumentException.class,
                () -> FileUploadValidator.validate(new MockMultipartFile("file", "notes.txt", "text/plain", "hi".getBytes())));
        ResponseEntity<?> answer = handler.illegalArgumentException(ex, request("ar"));
        assertThat(answer.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(hasArabic(((ErrorDetails) answer.getBody()).getMessage())).isTrue();
        assertThat(ex.getMessage()).as("English kept for logs").contains("not allowed");
    }

    @Test
    @DisplayName("P30: a portal authenticator refusal is answered in Arabic")
    void authenticatorRefusal() {
        LocalizedStatusException ex = new LocalizedStatusException(HttpStatus.BAD_REQUEST, "The code did not match",
                "portal.mfa.codeMismatch");
        ResponseEntity<?> answer = handler.responseStatusException(ex, request("ar"));
        assertThat(hasArabic(((ErrorDetails) answer.getBody()).getMessage())).isTrue();
    }

    @Test
    @DisplayName("the password rule reads right for 10 (not \"10 حرفاً\")")
    void arabicCount() {
        // Through the handler, as a refusal is answered: numbers stay Latin digits there.
        LocalizedArgumentException ex = new LocalizedArgumentException("Password too short", "password.tooShort", 10);
        String ar = ((ErrorDetails) handler.illegalArgumentException(ex, request("ar")).getBody()).getMessage();
        assertThat(ar).contains("10").doesNotContain("10 حرفاً").doesNotContain("10 حرفًا");
        String size = messages.getMessage("jakarta.validation.constraints.Size.message", null, Locale.forLanguageTag("ar"));
        assertThat(size).doesNotContain("حرفاً");
    }
}
