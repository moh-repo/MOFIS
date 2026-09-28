package com.dilaw.mohe.studentportal;

import com.dilaw.mohe.notification.model.NotificationSettings;
import com.dilaw.mohe.notification.service.EmailService;
import com.dilaw.mohe.notification.service.NotificationSettingsService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The reset code is guessable a bounded number of times, and it is delivered by
 * e-mail rather than by the log.
 */
class StudentPortalOtpServiceTest {

    private static final int MAX_ATTEMPTS = 5;

    private final EmailService email = mock(EmailService.class);
    private final NotificationSettingsService settings = mock(NotificationSettingsService.class);

    private StudentPortalOtpService service(boolean logToConsole) {
        return new StudentPortalOtpService(email, settings, logToConsole, MAX_ATTEMPTS);
    }

    /** Captures the code out of the e-mail body so the test can use it. */
    private String issue(StudentPortalOtpService svc, String key) {
        NotificationSettings cfg = new NotificationSettings();
        cfg.setEmailEnabled(true);
        cfg.setSmtpHost("smtp.example");
        when(settings.resolveForSend(any())).thenReturn(cfg);
        AtomicReference<String> body = new AtomicReference<>();
        when(email.send(eq(cfg), eq(key), anyString(), anyString())).thenAnswer(inv -> {
            body.set(inv.getArgument(3));
            return null;
        });
        assertThat(svc.generate(key, key, 1)).as("sent by e-mail").isTrue();
        String html = body.get();
        int b = html.indexOf("<b>") + 3;
        return html.substring(b, b + 6);
    }

    @Test
    @DisplayName("the right code, first time, is consumed")
    void correctCodeConsumed() {
        StudentPortalOtpService svc = service(false);
        String otp = issue(svc, "a@x.y");
        assertThat(svc.validate("a@x.y", otp)).isTrue();
        assertThat(svc.validate("a@x.y", otp)).as("single use").isFalse();
    }

    @Test
    @DisplayName("four wrong guesses leave the code alive; the fifth kills it")
    void wrongGuessesAreBounded() {
        StudentPortalOtpService svc = service(false);
        String otp = issue(svc, "b@x.y");
        for (int i = 1; i < MAX_ATTEMPTS; i++) {
            assertThat(svc.validate("b@x.y", "000000".equals(otp) ? "111111" : "000000")).isFalse();
            assertThat(svc.hasPending("b@x.y")).as("still alive after wrong guess " + i).isTrue();
        }
        assertThat(svc.validate("b@x.y", "000000".equals(otp) ? "111111" : "000000")).isFalse();
        assertThat(svc.hasPending("b@x.y")).as("invalidated at the limit").isFalse();
        assertThat(svc.validate("b@x.y", otp)).as("even the right code is refused now").isFalse();
    }

    @Test
    @DisplayName("without SMTP for the country nothing is sent and nothing is logged in production mode")
    void noSmtpNoSend() {
        StudentPortalOtpService svc = service(false);
        NotificationSettings cfg = new NotificationSettings();
        cfg.setEmailEnabled(false);
        when(settings.resolveForSend(any())).thenReturn(cfg);
        assertThat(svc.generate("c@x.y", "c@x.y", 1005)).isFalse();
        verify(email, never()).send(any(), anyString(), anyString(), anyString());
        assertThat(svc.hasPending("c@x.y")).as("the code exists; an officer can still reset the account").isTrue();
    }

    @Test
    @DisplayName("the country's own settings are asked for, not the global row")
    void countrySettingsUsed() {
        StudentPortalOtpService svc = service(false);
        issue(svc, "d@x.y");
        verify(settings).resolveForSend(List.of(1));
    }
}
