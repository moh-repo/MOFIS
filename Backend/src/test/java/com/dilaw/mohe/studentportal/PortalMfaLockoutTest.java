package com.dilaw.mohe.studentportal;

import com.dilaw.mohe.studentportal.model.StudentUser;
import com.dilaw.mohe.studentportal.repository.StudentUserRepository;
import com.dilaw.mohe.ums.service.MfaSecretCipher;
import com.dilaw.mohe.ums.service.TotpService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Live QA N15 (26 Sep 2026): wrong authenticator codes when a student turns two-factor
 * authentication on or off were counted and then rolled back with the refusal, so those
 * two paths never locked. A signed-in session (one left open) could try codes to remove
 * the factor without being slowed. They now keep the count and share the sign-in lockout:
 * five wrong codes lock the factor for fifteen minutes, whichever of the three paths
 * they come from.
 *
 * <p>The service runs behind Spring's own transaction interceptor, reading the real
 * {@code @Transactional} attributes, with a transaction manager that records whether the
 * transaction was committed or rolled back.
 */
class PortalMfaLockoutTest {

    private static final int USER = 42;
    private static final String RIGHT = "123456";

    private final StudentUserRepository users = mock(StudentUserRepository.class);
    private final TotpService totp = mock(TotpService.class);
    private final MfaSecretCipher cipher = mock(MfaSecretCipher.class);
    private final PlatformTransactionManager txManager = mock(PlatformTransactionManager.class);
    private final TransactionStatus tx = mock(TransactionStatus.class);
    private StudentPortalMfaService service;
    private StudentUser user;

    @BeforeEach
    void setUp() {
        StudentPortalMfaService target = new StudentPortalMfaService(users, totp, cipher, "MOHE", 5, 15);
        ProxyFactory proxy = new ProxyFactory(target);
        proxy.setProxyTargetClass(true);
        proxy.addAdvice(new TransactionInterceptor(txManager, new AnnotationTransactionAttributeSource()));
        service = (StudentPortalMfaService) proxy.getProxy();
        when(txManager.getTransaction(any())).thenReturn(tx);

        user = new StudentUser();
        user.setTotpSecretEnc("enc");
        user.setTotpEnabled(true);
        when(users.findById(USER)).thenReturn(Optional.of(user));
        when(cipher.decrypt(any())).thenReturn("SECRET");
        when(totp.currentStep()).thenReturn(100L);
        when(totp.verify(any(), any(), anyLong(), anyInt())).thenReturn(-1L);
        when(totp.verify(any(), eq(RIGHT), anyLong(), anyInt())).thenReturn(100L);
    }

    private static HttpStatus statusOf(Runnable call) {
        ResponseStatusException e = catchThrowableOfType(ResponseStatusException.class, call::run);
        assertThat(e).as("refused").isNotNull();
        return HttpStatus.valueOf(e.getStatusCode().value());
    }

    @Test
    @DisplayName("a wrong code on turn-off is refused and its count is committed, not rolled back")
    void wrongCodeOnDisableIsKept() {
        assertThat(statusOf(() -> service.disable(USER, "000000"))).isEqualTo(HttpStatus.BAD_REQUEST);
        verify(txManager).commit(tx);
        verify(txManager, never()).rollback(any());
        assertThat(user.getTotpFailedAttempts()).isEqualTo(1);
    }

    @Test
    @DisplayName("a wrong code on turn-on is refused and its count is committed, not rolled back")
    void wrongCodeOnConfirmIsKept() {
        user.setTotpEnabled(false);
        assertThat(statusOf(() -> service.confirm(USER, "000000"))).isEqualTo(HttpStatus.BAD_REQUEST);
        verify(txManager).commit(tx);
        verify(txManager, never()).rollback(any());
    }

    @Test
    @DisplayName("five wrong codes on turn-off lock the factor: the right code is refused, and sign-in too")
    void fiveWrongCodesLock() {
        for (int i = 0; i < 5; i++) {
            assertThat(statusOf(() -> service.disable(USER, "000000"))).isEqualTo(HttpStatus.BAD_REQUEST);
        }
        verify(txManager, times(5)).commit(tx);
        assertThat(user.getTotpLockedUntil()).isAfter(Instant.now().plusSeconds(14 * 60));

        assertThat(statusOf(() -> service.disable(USER, RIGHT))).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(user.getTotpEnabled()).as("still on").isTrue();
        assertThat(statusOf(() -> service.verifyAtLogin(user, RIGHT))).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }
}
