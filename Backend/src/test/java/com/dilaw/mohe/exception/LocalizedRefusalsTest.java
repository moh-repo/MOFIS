package com.dilaw.mohe.exception;

import com.dilaw.mohe.model.StudentFinance;
import com.dilaw.mohe.service.StudentFinanceInputRules;
import com.dilaw.mohe.util.PhoneNumbers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/**
 * Plan W3-08 (review §2.2): the rules' refusals are answered in the reader's
 * language. They were English sentences on every screen, some starting with the
 * Java field name. The real bundles are used, so a key missing from either
 * language fails here.
 */
class LocalizedRefusalsTest {

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
        MockHttpServletRequest req = new MockHttpServletRequest("PUT", "/mohe/api/v1/x");
        req.addHeader("Accept-Language", acceptLanguage);
        return new ServletWebRequest(req);
    }

    private String answer(IllegalArgumentException ex, String lang) {
        ResponseEntity<?> response = handler.illegalArgumentException(ex, request(lang));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        return ((ErrorDetails) response.getBody()).getMessage();
    }

    private static boolean hasArabic(String s) {
        return s.codePoints().anyMatch(c -> Character.UnicodeBlock.of(c) == Character.UnicodeBlock.ARABIC);
    }

    @Test
    @DisplayName("a refused mobile number names the field as the reader knows it, with plain digits")
    void phoneRefusal() {
        IllegalArgumentException ex = catchThrowableOfType(IllegalArgumentException.class,
                () -> PhoneNumbers.require("studentCellphone", "12", true));

        String en = answer(ex, "en");
        String ar = answer(ex, "ar");

        assertThat(en).startsWith("Mobile number:").contains("9 to 15 digits").doesNotContain("studentCellphone");
        assertThat(ar).startsWith("رقم الجوال").contains("9").contains("15").doesNotContain("studentCellphone");
        assertThat(ar).as("no Arabic-Indic numerals from MessageFormat").doesNotContain("٩", "١٥");
        assertThat(ex.getMessage()).as("the exception keeps its English sentence for logs").contains("studentCellphone");
    }

    @Test
    @DisplayName("an incomplete finance record lists every broken rule in the reader's language")
    void financeRefusal() {
        StudentFinance f = new StudentFinance();
        f.setId(5);
        f.setStudentFinRecievedAmount(0);
        // RECEIVED: money moved, so the missing cheque date is a broken rule too (live
        // QA H2 — a row on NOT_SPECIFIED needs no date, and no status at all is its own
        // problem rather than a reason to demand a date).
        f.setStudyFinRecievedStatus(1);

        IllegalArgumentException ex = catchThrowableOfType(IllegalArgumentException.class,
                () -> StudentFinanceInputRules.require(f));

        String en = answer(ex, "en");
        String ar = answer(ex, "ar");

        assertThat(en).startsWith("The finance record is incomplete or invalid:")
                .contains("the amount (must be greater than 0), the check date, the study country");
        assertThat(hasArabic(en)).isFalse();
        assertThat(ar).startsWith("السجل المالي غير مكتمل").contains("المبلغ").contains("، ").contains("دولة الدراسة");
    }

    @Test
    @DisplayName("an unkeyed refusal is echoed as it stands")
    void plainRefusalUnchanged() {
        assertThat(answer(new IllegalArgumentException("as written"), "ar")).isEqualTo("as written");
    }

    @Test
    @DisplayName("every keyed refusal the services throw has both languages")
    void everyKeyInBothLanguages() {
        String[] keys = {
                "validation.phone", "finance.incomplete",
                "ums.user.password.same", "ums.user.password.recent", "ums.user.email.invalid",
                "ums.user.username.exists", "ums.user.name.required", "ums.user.password.required",
                "ums.user.country.required", "ums.user.group.exists", "ums.user.country.exists",
                "ums.user.permission.exists", "ums.grant.reason.required", "ums.grant.expiry.required",
                "ums.grant.expiry.past", "ums.grant.expiry.tooLong", "ums.group.system", "ums.role.system",
                "quarter.create.running", "quarter.create.failed", "quarter.create.busy",
                "changeRequest.invalid.value", "import.header.notFound", "import.header.noStudentName",
                "field.studentCellphone", "field.studentHomeNumber", "field.studentEmergencyContact", "field.phone",
                "notification.test.required", "notification.test.ministryOnly", "notification.test.email",
                "notification.test.phone", "notification.test.channel", "student.reference.missing",
                "field.studentCountryId", "field.studentPassportIssueCountryId", "field.studentGovId", "field.studentMudId",
                "field.studentGender", "field.studentDegreeId", "field.studentMajorId", "field.studentCurrentLevelId",
                "field.studentStudyStatusId", "field.studentAgencyTypeId", "field.studentMinistryTypeId",
                "field.studentStudyTypeId", "field.studentUniversityTypeId", "field.studyCountryIn", "field.syudyGovIn",
                "field.studyUniversityIn", "field.studyCollegeIn", "field.studyEducationIn", "field.studyMajorIn",
                "approval.pending.exists", "approval.pending.existsNumbered",
                "accounting.fiscalYear.missing", "accounting.fiscalYear.exists", "accounting.fiscalYear.range",
                "reportSettings.type.unknown", "reportSettings.type.exists", "report.frequency.WEEKLY",
                "bankBranch.countryRequired", "bankBranch.countryMismatch"};
        for (String key : keys) {
            String en = messages.getMessage(key, new Object[] {"a", "b", "c"}, null, Locale.ENGLISH);
            String ar = messages.getMessage(key, new Object[] {"a", "b", "c"}, null, Locale.forLanguageTag("ar"));
            assertThat(en).as(key + " in English").isNotBlank();
            assertThat(ar).as(key + " in Arabic").isNotBlank().isNotEqualTo(en);
            assertThat(hasArabic(ar)).as(key + " is Arabic in Arabic").isTrue();
        }
    }

    @Test
    @DisplayName("a student referring to missing values lists the fields by name, in the reader's language (A2)")
    void referenceRefusal() {
        LocalizedArgumentException ex = new LocalizedArgumentException(
                "These fields refer to a value that does not exist: studyCountryIn, studentGovId",
                "student.reference.missing",
                java.util.List.of(LocalizedMessage.field("studyCountryIn"), LocalizedMessage.field("studentGovId")));

        assertThat(answer(ex, "en")).endsWith(": Study country, Governorate.");
        assertThat(answer(ex, "ar")).endsWith(": دولة الدراسة، المحافظة.");
    }

    @Test
    @DisplayName("a keyed status refusal keeps its status and is answered in the reader's language (R7)")
    void statusRefusal() {
        LocalizedStatusException ex = new LocalizedStatusException(HttpStatus.BAD_REQUEST,
                "Enter the phone number to send the test to", "notification.test.phone");

        ResponseEntity<?> en = handler.responseStatusException(ex, request("en"));
        ResponseEntity<?> ar = handler.responseStatusException(ex, request("ar"));

        assertThat(en.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(((ErrorDetails) en.getBody()).getMessage()).isEqualTo("Enter the phone number to send the test to.");
        assertThat(((ErrorDetails) ar.getBody()).getMessage()).isEqualTo("أدخل رقم الهاتف الذي تُرسل إليه الرسالة التجريبية.");
    }

    @Test
    @DisplayName("a record with a request awaiting approval is a 409 naming the request, on every screen (W4-08)")
    void pendingRequestRefusal() {
        var ex = new com.dilaw.mohe.approval.service.PendingApprovalExistsException(20732L);

        ResponseEntity<?> en = handler.pendingApprovalExists(ex, request("en"));
        ResponseEntity<?> ar = handler.pendingApprovalExists(ex, request("ar"));

        assertThat(en.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        ErrorDetails body = (ErrorDetails) en.getBody();
        assertThat(body.getCode()).isEqualTo("PENDING_EXISTS");
        assertThat(body.getRequestId()).isEqualTo(20732L);
        assertThat(body.getMessage()).contains("20732").doesNotContain("entity");
        assertThat(((ErrorDetails) ar.getBody()).getMessage()).contains("20732").startsWith("لهذا السجل");
    }

    @Test
    @DisplayName("a system role refusal (409) is answered in Arabic too")
    void stateRefusal() {
        LocalizedStateException ex = new LocalizedStateException("A system role cannot be edited or deleted.", "ums.role.system");

        ResponseEntity<?> ar = handler.illegalStateException(ex, request("ar"));

        assertThat(ar.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(((ErrorDetails) ar.getBody()).getMessage()).isEqualTo("لا يمكن تعديل دور نظام أو حذفه.");
    }
}
