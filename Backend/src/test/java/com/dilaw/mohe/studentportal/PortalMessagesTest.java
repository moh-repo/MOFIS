package com.dilaw.mohe.studentportal;

import com.dilaw.mohe.controler.StudentStatusReportController;
import com.dilaw.mohe.ums.service.PasswordPolicy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The texts the sign-in, the password flows and the status report utter exist
 * in both bundles and differ between them (live QA M2, M5, L12 of 24 Sep 2026).
 *
 * <p>A plain {@link ResourceBundleMessageSource} over {@code messages*.properties},
 * no Spring context: the point is the bundles, not the wiring.
 */
class PortalMessagesTest {

    private static final Locale AR = Locale.forLanguageTag("ar");

    private static final List<String> KEYS = List.of(
            "auth.captcha.failed", "auth.failed",
            "portal.login.invalid", "portal.login.emailCodeInvalid", "portal.login.totpRequired",
            "portal.login.totpInvalid", "portal.logout.done",
            "portal.reset.emailRequired", "portal.reset.emailAndOtpRequired", "portal.reset.codeInvalid",
            "portal.reset.accountNotFound", "portal.reset.done", "portal.reset.failed", "portal.reset.requestFailed",
            "portal.password.currentWrong",
            "password.needsLetterDigit", "password.containsUsername", "password.common");

    private static ResourceBundleMessageSource messages() {
        ResourceBundleMessageSource source = new ResourceBundleMessageSource();
        source.setBasename("messages");
        source.setDefaultEncoding("UTF-8");
        source.setFallbackToSystemLocale(false);
        return source;
    }

    @Test
    @DisplayName("every sign-in and password message resolves in English and in Arabic, and the two differ")
    void everyKeyInBothLanguages() {
        ResourceBundleMessageSource source = messages();
        for (String key : KEYS) {
            String en = source.getMessage(key, null, null, Locale.ENGLISH);
            String ar = source.getMessage(key, null, null, AR);
            assertThat(en).as("%s (en)", key).isNotBlank();
            assertThat(ar).as("%s (ar)", key).isNotBlank().isNotEqualTo(en);
        }
    }

    @Test
    @DisplayName("the messages that carry an argument render it in both languages")
    void argumentsRender() {
        ResourceBundleMessageSource source = messages();
        Object[] masked = {"s***2@mohe.edu.ye"};
        assertThat(source.getMessage("portal.login.verifyEmailSent", masked, Locale.ENGLISH)).contains("s***2@mohe.edu.ye");
        assertThat(source.getMessage("portal.login.verifyEmailSent", masked, AR)).contains("s***2@mohe.edu.ye").doesNotContain("{0}");
        assertThat(source.getMessage("portal.reset.sent", masked, AR)).contains("s***2@mohe.edu.ye");
        assertThat(source.getMessage("password.tooShort", new Object[] {"10"}, AR)).contains("10").doesNotContain("{0}");
    }

    @Test
    @DisplayName("a broken password rule is reported in the reader's language, and in English without a source")
    void passwordRuleLocalized() {
        PasswordPolicy.Violation tooShort = PasswordPolicy.violation("short1", "student2@mohe.edu.ye", PasswordPolicy.PORTAL_MIN);
        assertThat(tooShort).isNotNull();
        assertThat(tooShort.key()).isEqualTo("password.tooShort");
        assertThat(tooShort.localized(null, AR)).isEqualTo("Password must be at least 10 characters long");
        assertThat(tooShort.localized(messages(), AR)).contains("10").isNotEqualTo(tooShort.english());
        assertThat(tooShort.localized(messages(), Locale.ENGLISH)).isEqualTo(tooShort.english());

        PasswordPolicy.Violation noDigit = PasswordPolicy.violation("onlyletterslong", null, PasswordPolicy.PORTAL_MIN);
        assertThat(noDigit).isNotNull();
        assertThat(noDigit.key()).isEqualTo("password.needsLetterDigit");
        // The English rendering is the wording the staff screens already show.
        assertThat(PasswordPolicy.problem("onlyletterslong", null, PasswordPolicy.PORTAL_MIN)).isEqualTo(noDigit.english());
        assertThat(PasswordPolicy.violation("Correct-Horse-9", null, PasswordPolicy.PORTAL_MIN)).isNull();
    }

    @Test
    @DisplayName("the student status report labels every category code the view emits, in the report's language")
    void statusCategoryLabels() {
        StudentStatusReportController controller = new StudentStatusReportController();
        ReflectionTestUtils.setField(controller, "messageSource", messages());

        Map<String, String> ar = controller.categoryLabels("ar");
        Map<String, String> en = controller.categoryLabels("en");

        assertThat(ar.keySet()).containsExactlyElementsOf(StudentStatusReportController.CATEGORY_CODES);
        assertThat(en.get("active")).isEqualTo("Active");
        assertThat(ar.get("active")).isEqualTo("نشط");
        assertThat(ar.values()).noneMatch(label -> label.equals(label.toLowerCase(Locale.ROOT)) && label.matches("[a-z]+"));
    }

    @Test
    @DisplayName("without a message source the report prints the code as it is")
    void statusCategoryWithoutSource() {
        assertThat(new StudentStatusReportController().categoryLabels("en")).isEmpty();
    }
}
