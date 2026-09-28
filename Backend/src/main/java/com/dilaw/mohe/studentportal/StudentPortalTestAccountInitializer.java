package com.dilaw.mohe.studentportal;

import com.dilaw.mohe.model.StudentDatum;
import com.dilaw.mohe.model.StudentPortalAccount;
import com.dilaw.mohe.repository.StudentDataRepository;
import com.dilaw.mohe.repository.StudentPortalAccountRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Optional;

/**
 * Ensures a test Student Portal account exists with a known password (hash generated at runtime
 * so it matches the same BCrypt encoder used at login). Only runs when
 * student-portal.init-test-account=true (e.g. in dev).
 * Test login: teststudent / P@ssw0rd
 */
@Component
@Order(100)
public class StudentPortalTestAccountInitializer implements ApplicationRunner {

    /*
     * O-15 (security plan, 6 Sep 2026): the credentials used to be literals in
     * this file, and the only thing keeping the account out of production was one
     * property line. They now come from properties with no default — the dev
     * profile sets them — and the runner refuses to act outside the dev profile
     * even when the property says yes, the same fail-closed pattern as
     * WebSecurityConfig.rejectDevPatternsOutsideDev.
     */
    @Value("${student-portal.test-account.login-key:}")
    private String TEST_LOGIN_KEY;

    @Value("${student-portal.test-account.password:}")
    private String TEST_PASSWORD;

    @Value("${student-portal.init-test-account:false}")
    private boolean initTestAccount;

    @Autowired
    private org.springframework.core.env.Environment environment;

    @Autowired
    private StudentPortalAccountRepository accountRepository;
    @Autowired
    private StudentDataRepository studentDataRepository;

    private final BCryptPasswordEncoder encoder = com.dilaw.mohe.config.Passwords.encoder();
    private static final Logger log = LoggerFactory.getLogger(StudentPortalTestAccountInitializer.class);

    @Override
    public void run(ApplicationArguments args) {
        if (!initTestAccount) return;
        if (!environment.matchesProfiles("dev")) {
            log.warn("student-portal.init-test-account=true ignored: the test account is only created under the dev profile.");
            return;
        }
        if (TEST_LOGIN_KEY == null || TEST_LOGIN_KEY.isBlank() || TEST_PASSWORD == null || TEST_PASSWORD.isBlank()) {
            log.warn("student-portal.init-test-account=true ignored: set student-portal.test-account.login-key and .password.");
            return;
        }
        try {
            runInit();
        } catch (Exception e) {
            log.warn("Student portal test account init skipped (DB unavailable or error): {}", e.getMessage());
        }
    }

    private void runInit() {
        Integer studentId = studentDataRepository.findAll().stream()
                .map(StudentDatum::getStudentId)
                .findFirst()
                .orElse(null);

        if (studentId == null) {
            return; // no students in DB, skip
        }

        // Find by login key (active or not) so we can fix hash / reactivate
        Optional<StudentPortalAccount> existing = accountRepository.findByLoginKey(TEST_LOGIN_KEY);
        if (existing.isPresent()) {
            StudentPortalAccount acc = existing.get();
            acc.setPasswordHash(encoder.encode(TEST_PASSWORD));
            acc.setStudentId(studentId);
            acc.setIsActive(true);
            accountRepository.save(acc);
            return;
        }

        StudentPortalAccount account = new StudentPortalAccount();
        account.setStudentId(studentId);
        account.setLoginKey(TEST_LOGIN_KEY);
        account.setPasswordHash(encoder.encode(TEST_PASSWORD));
        account.setIsActive(true);
        account.setCreatedAt(Instant.now());
        accountRepository.save(account);
    }
}
