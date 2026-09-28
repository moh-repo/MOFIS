package com.dilaw.mohe.studentportal;

import com.dilaw.mohe.notification.model.NotificationSettings;
import com.dilaw.mohe.notification.service.EmailService;
import com.dilaw.mohe.notification.service.NotificationSettingsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * One-time codes for the student-portal password reset.
 *
 * <p>In memory, ten minutes, single use — and since August 2026, a bounded
 * number of wrong guesses. Six digits is a million possibilities; with the login
 * throttle that was supposed to cover this endpoint not actually matching it (see
 * {@code LoginRateLimitFilter}), and no attempt counter here, a code could be
 * brute-forced comfortably inside its ten-minute life. Now each wrong guess
 * counts, and the fifth invalidates the code; the caller has to ask for a new
 * one, which is itself throttled.
 *
 * <p>Delivery: the code goes by e-mail through the notification settings of the
 * student's country ({@link NotificationSettingsService#resolveForSend}), the
 * same SMTP configuration the scheduled reports use. When no mail is configured
 * for that country (or the global row), nothing is sent and the request still
 * succeeds — the student is told to contact the ministry officer, who can reset
 * the password from Student User Management. The code is written to the log only
 * when {@code mohe.student-portal.otp.log-to-console} is on, which the dev
 * profile sets and production must not: a log line holding a live reset code is
 * a credential.
 */
@Service
public class StudentPortalOtpService {

    private static final Logger log = LoggerFactory.getLogger(StudentPortalOtpService.class);
    private static final int OTP_DIGITS = 6;
    private static final int TTL_MINUTES = 10;

    private final SecureRandom random = new SecureRandom();
    private final Map<String, OtpEntry> store = new ConcurrentHashMap<>();

    private final EmailService emailService;
    private final NotificationSettingsService notificationSettingsService;
    private final boolean logToConsole;
    private final int maxAttempts;

    public StudentPortalOtpService(EmailService emailService,
                                   NotificationSettingsService notificationSettingsService,
                                   @Value("${mohe.student-portal.otp.log-to-console:false}") boolean logToConsole,
                                   @Value("${mohe.student-portal.otp.max-attempts:5}") int maxAttempts) {
        this.emailService = emailService;
        this.notificationSettingsService = notificationSettingsService;
        this.logToConsole = logToConsole;
        this.maxAttempts = Math.max(1, maxAttempts);
    }

    /**
     * Generates a new OTP for the given loginKey, replacing any previous one, and
     * delivers it. Returns whether an e-mail was actually sent.
     *
     * @param countryId the student's study country, used to pick the SMTP settings;
     *                  null falls back to the global row
     */
    public boolean generate(String loginKey, String email, Integer countryId) {
        String otp = String.format("%0" + OTP_DIGITS + "d", random.nextInt((int) Math.pow(10, OTP_DIGITS)));
        store.put(loginKey, new OtpEntry(otp, Instant.now().plus(TTL_MINUTES, ChronoUnit.MINUTES), new AtomicInteger()));

        boolean sent = deliver(email, countryId, otp);
        if (logToConsole) {
            log.info("PASSWORD RESET OTP for loginKey='{}': {} (expires in {} min, sent by e-mail: {})",
                    loginKey, otp, TTL_MINUTES, sent);
        } else if (!sent) {
            log.warn("Password-reset OTP generated for loginKey='{}' but no e-mail could be sent "
                    + "(no SMTP configured for country {}); the code was not logged.", loginKey, countryId);
        }
        return sent;
    }

    /** Kept for callers that have no country to hand; uses the global mail settings. */
    public String generate(String loginKey, String email) {
        generate(loginKey, email, null);
        return null;
    }

    /**
     * Returns true and consumes the OTP if it is valid and not expired. A wrong
     * guess counts; at {@code maxAttempts} the code is discarded.
     */
    public boolean validate(String loginKey, String otp) {
        OtpEntry entry = store.get(loginKey);
        if (entry == null) return false;
        if (Instant.now().isAfter(entry.expiresAt())) {
            store.remove(loginKey);
            return false;
        }
        if (!entry.otp().equals(otp)) {
            if (entry.attempts().incrementAndGet() >= maxAttempts) {
                store.remove(loginKey);
                log.warn("Password-reset OTP for loginKey='{}' invalidated after {} wrong attempts", loginKey, maxAttempts);
            }
            return false;
        }
        store.remove(loginKey);
        return true;
    }

    /** Whether a live code exists for the key — for tests and diagnostics. */
    boolean hasPending(String loginKey) {
        return store.containsKey(loginKey);
    }

    /** The live code for the key, for the package's own tests only; null when none. */
    String peek(String loginKey) {
        OtpEntry entry = store.get(loginKey);
        return entry == null ? null : entry.otp();
    }

    private boolean deliver(String email, Integer countryId, String otp) {
        if (email == null || email.isBlank() || !email.contains("@")) {
            return false;
        }
        try {
            NotificationSettings cfg = notificationSettingsService.resolveForSend(
                    countryId == null ? List.of() : List.of(countryId));
            if (cfg == null || !Boolean.TRUE.equals(cfg.getEmailEnabled())
                    || cfg.getSmtpHost() == null || cfg.getSmtpHost().isBlank()) {
                return false;
            }
            String body = "<p>Your MOHE student portal password reset code is: <b>" + otp + "</b></p>"
                    + "<p>It expires in " + TTL_MINUTES + " minutes. If you did not ask for it, ignore this message.</p>"
                    + "<p dir=\"rtl\">رمز إعادة تعيين كلمة المرور لبوابة الطالب: <b>" + otp + "</b> — صالح لمدة "
                    + TTL_MINUTES + " دقائق.</p>";
            // EmailService.send returns null on success and the error text otherwise.
            String error = emailService.send(cfg, email, "MOHE student portal — password reset code", body);
            return error == null;
        } catch (Exception e) {
            log.warn("Could not e-mail the password-reset OTP to {}: {}", email, e.getMessage());
            return false;
        }
    }

    private record OtpEntry(String otp, Instant expiresAt, AtomicInteger attempts) {}
}
