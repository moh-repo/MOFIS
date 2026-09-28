package com.dilaw.mohe.studentportal;

import com.dilaw.mohe.studentportal.model.StudentUser;
import com.dilaw.mohe.studentportal.repository.StudentUserRepository;
import com.dilaw.mohe.ums.service.MfaSecretCipher;
import com.dilaw.mohe.ums.service.TotpService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * System test PRT-07: a portal authenticator code is accepted once. The window takes a
 * step either side of now, so without a record of the last step a code could be
 * replayed for about 90 seconds.
 */
class PortalTotpReplayTest {

    private final StudentUserRepository users = mock(StudentUserRepository.class);
    private final TotpService totp = mock(TotpService.class);
    private final MfaSecretCipher cipher = mock(MfaSecretCipher.class);
    private StudentPortalMfaService service;
    private StudentUser user;

    @BeforeEach
    void setUp() {
        service = new StudentPortalMfaService(users, totp, cipher, "MOHE", 5, 15);
        user = new StudentUser();
        user.setStudentUserId(3);
        user.setTotpEnabled(true);
        user.setTotpSecretEnc("enc");
        when(cipher.decrypt(any())).thenReturn("SECRET");
        when(totp.currentStep()).thenReturn(100L);
        when(totp.verify(any(), any(), anyLong(), anyInt())).thenReturn(-1L);
        when(totp.verify(any(), eq("111111"), anyLong(), anyInt())).thenReturn(100L);
        when(totp.verify(any(), eq("222222"), anyLong(), anyInt())).thenReturn(99L);
        when(totp.verify(any(), eq("333333"), anyLong(), anyInt())).thenReturn(101L);
    }

    @Test
    @DisplayName("the same code twice: accepted, then refused and counted as a wrong code")
    void sameCodeTwice() {
        assertThat(service.verifyAtLogin(user, "111111")).isTrue();
        assertThat(user.getTotpLastStep()).isEqualTo(100L);

        assertThat(service.verifyAtLogin(user, "111111")).as("replayed").isFalse();
        assertThat(user.getTotpFailedAttempts()).isEqualTo(1);
    }

    @Test
    @DisplayName("an older step after a newer one is refused; the next step is accepted")
    void olderStepRefusedNewerAccepted() {
        assertThat(service.verifyAtLogin(user, "111111")).isTrue();
        assertThat(service.verifyAtLogin(user, "222222")).as("step 99 after 100").isFalse();
        assertThat(service.verifyAtLogin(user, "333333")).as("step 101").isTrue();
        assertThat(user.getTotpLastStep()).isEqualTo(101L);
        assertThat(user.getTotpFailedAttempts()).isZero();
    }
}
