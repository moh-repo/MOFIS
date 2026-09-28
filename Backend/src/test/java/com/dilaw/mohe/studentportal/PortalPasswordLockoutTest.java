package com.dilaw.mohe.studentportal;

import com.dilaw.mohe.codes.PortalAccountStatus;
import com.dilaw.mohe.model.StudentDatum;
import com.dilaw.mohe.repository.StudentDataRepository;
import com.dilaw.mohe.studentportal.model.StudentUser;
import com.dilaw.mohe.studentportal.repository.StudentPermissionRepository;
import com.dilaw.mohe.studentportal.repository.StudentSessionRepository;
import com.dilaw.mohe.studentportal.repository.StudentUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * System test PRT-04 (per-account lock for portal passwords) and PRT-06 (the same
 * bcrypt cost whether or not the account exists).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PortalPasswordLockoutTest {

    private static final String EMAIL = "student@example.invalid";
    private static final String RIGHT = "Right-Password-1";
    private static final int COUNTRY = 1004;

    @Mock private StudentUserRepository studentUserRepository;
    @Mock private StudentSessionRepository sessionRepository;
    @Mock private StudentDataRepository studentDataRepository;
    @Mock private StudentPermissionRepository permissionRepository;
    @Mock private StudentPortalOtpService otpService;
    @Mock private StudentPortalMfaService mfaService;
    @InjectMocks private StudentUserAuthService service;

    private BCryptPasswordEncoder encoder;
    private StudentUser user;

    @BeforeEach
    void setUp() {
        // A cheap encoder for the test, spied so the comparisons can be counted.
        encoder = spy(new BCryptPasswordEncoder(4));
        ReflectionTestUtils.setField(service, "passwordEncoder", encoder);
        ReflectionTestUtils.setField(service, "timingDummyHash", encoder.encode("dummy"));

        user = new StudentUser();
        user.setStudentUserId(11);
        user.setStudentId(22);
        user.setUsername(EMAIL);
        user.setPasswordHash(encoder.encode(RIGHT));
        user.setStatus(PortalAccountStatus.ACTIVE);
        user.setEmailVerifiedAt(Instant.now());
        user.setPasswordChangedAt(Instant.now());
        when(studentUserRepository.findByUsernameAndIsDeletedFalse(EMAIL)).thenReturn(Optional.of(user));
        StudentDatum student = new StudentDatum();
        student.setStudentId(22);
        student.setStudyCountryIn(COUNTRY);
        when(studentDataRepository.findById(22)).thenReturn(Optional.of(student));
        when(permissionRepository.findEffectivePermissionsForUser(11)).thenReturn(List.of());
    }

    private StudentUserAuthService.LoginStep login(String password) {
        return service.login(EMAIL, password, COUNTRY, null, null);
    }

    @Test
    @DisplayName("PRT-04: five wrong passwords lock the account; while locked even the right password is refused, and no session is made")
    void fiveFailuresLock() {
        for (int i = 1; i <= 4; i++) {
            assertThat(login("wrong-" + i)).isNull();
            assertThat(user.getFailedLoginAttempts()).isEqualTo(i);
            assertThat(user.getLockedUntil()).isNull();
        }
        assertThat(login("wrong-5")).isNull();
        assertThat(user.getLockedUntil()).isAfter(Instant.now().plus(14, ChronoUnit.MINUTES));

        assertThat(login(RIGHT)).as("locked: refused like any other failure").isNull();
        verify(sessionRepository, never()).save(any());
        verify(studentUserRepository, times(5)).save(user);
    }

    @Test
    @DisplayName("PRT-04: once the lock has run out the right password works, and success clears the count")
    void lockExpiresAndSuccessResets() {
        user.setFailedLoginAttempts(3);
        user.setLockedUntil(Instant.now().minusSeconds(1));
        StudentUserAuthService.LoginStep step = login(RIGHT);
        assertThat(step).isNotNull();
        assertThat(step.step()).isEqualTo(StudentUserAuthService.Step.OK);
        assertThat(user.getFailedLoginAttempts()).isZero();
        assertThat(user.getLockedUntil()).isNull();
    }

    @Test
    @DisplayName("PRT-04: a password reset with a valid code lifts the lock")
    void resetClearsLock() {
        user.setFailedLoginAttempts(2);
        user.setLockedUntil(Instant.now().plus(10, ChronoUnit.MINUTES));
        when(otpService.validate(EMAIL, "123456")).thenReturn(true);
        service.resetPassword(EMAIL, "123456", "A-new-Passw0rd!x");
        assertThat(user.getFailedLoginAttempts()).isZero();
        assertThat(user.getLockedUntil()).isNull();
    }

    @Test
    @DisplayName("PRT-06: an unknown e-mail, an inactive account and a locked one each still cost one bcrypt comparison")
    void everyRefusalSpendsABcrypt() {
        when(studentUserRepository.findByUsernameAndIsDeletedFalse("nobody@example.invalid")).thenReturn(Optional.empty());
        assertThat(service.login("nobody@example.invalid", "whatever", COUNTRY, null, null)).isNull();
        verify(encoder, times(1)).matches(eq("whatever"), anyString());

        user.setStatus(PortalAccountStatus.INACTIVE);
        assertThat(login("inactive-guess")).isNull();
        verify(encoder, times(1)).matches(eq("inactive-guess"), anyString());

        user.setStatus(PortalAccountStatus.ACTIVE);
        user.setLockedUntil(Instant.now().plus(5, ChronoUnit.MINUTES));
        assertThat(login("locked-guess")).isNull();
        verify(encoder, times(1)).matches(eq("locked-guess"), anyString());
        assertThat(user.getFailedLoginAttempts()).as("a try while locked is not counted again").isZero();
    }
}
