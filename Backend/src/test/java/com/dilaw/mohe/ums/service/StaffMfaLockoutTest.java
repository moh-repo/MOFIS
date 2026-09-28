package com.dilaw.mohe.ums.service;

import com.dilaw.mohe.codes.UserStatus;
import com.dilaw.mohe.systemsettings.service.SysParameterService;
import com.dilaw.mohe.ums.model.AuthLockoutPolicy;
import com.dilaw.mohe.ums.model.UmsUser;
import com.dilaw.mohe.ums.model.UmsUserMfa;
import com.dilaw.mohe.ums.repository.AuthLockoutPolicyRepository;
import com.dilaw.mohe.ums.repository.UmsUserMfaRecoveryCodeRepository;
import com.dilaw.mohe.ums.repository.UmsUserMfaRepository;
import com.dilaw.mohe.ums.repository.UmsUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Live QA N16 (26 Sep 2026): the staff second factor never locked. {@code verify} stored
 * the wrong-code count and an MFA_FAILED audit row and then threw inside its transaction,
 * which rolled both back, and the code step is not behind the sign-in throttle.
 *
 * <p>Decided ladder: five wrong codes lock the factor for as long as the password's first
 * lock; five more lock the account, which only an administrator's unlock opens. The
 * password policy here gives its first lock 20 minutes, so the test tells it apart from
 * the authenticator's own 15-minute setting.
 */
class StaffMfaLockoutTest {

    private static final long USER = 5L;
    private static final String RIGHT = "123456";
    private static final int PASSWORD_FIRST_LOCK = 20;

    private final UmsUserMfaRepository mfaRepository = mock(UmsUserMfaRepository.class);
    private final UmsUserRepository userRepository = mock(UmsUserRepository.class);
    private final TotpService totp = mock(TotpService.class);
    private final MfaSecretCipher cipher = mock(MfaSecretCipher.class);
    private final UmsAuditLogService audit = mock(UmsAuditLogService.class);
    private final UmsUserSessionService sessions = mock(UmsUserSessionService.class);
    private final LoginAttemptService loginAttempts = mock(LoginAttemptService.class);
    private final PlatformTransactionManager txManager = mock(PlatformTransactionManager.class);
    private final TransactionStatus tx = mock(TransactionStatus.class);

    private MfaService service;
    private UmsUserMfa row;
    private UmsUser user;

    @BeforeEach
    void setUp() {
        MfaService target = new MfaService(mfaRepository, mock(UmsUserMfaRecoveryCodeRepository.class), userRepository,
                mock(UserAuthorityCache.class), mock(AdminTierService.class), totp, cipher, audit,
                mock(UmsPrivilegeGuard.class), mock(UmsUserService.class), loginAttempts, sessions,
                true, "MOHE", 5, 15);
        ProxyFactory proxy = new ProxyFactory(target);
        proxy.setProxyTargetClass(true);
        proxy.addAdvice(new TransactionInterceptor(txManager, new AnnotationTransactionAttributeSource()));
        service = (MfaService) proxy.getProxy();
        when(txManager.getTransaction(any())).thenReturn(tx);

        row = new UmsUserMfa();
        row.setUserId(USER);
        row.setEnabled(true);
        row.setSecretEncrypted("enc");
        when(mfaRepository.findById(USER)).thenReturn(Optional.of(row));
        user = new UmsUser();
        user.setId(USER);
        user.setStatus(UserStatus.ACTIVE);
        when(userRepository.findById(USER)).thenReturn(Optional.of(user));
        when(cipher.decrypt(any())).thenReturn("SECRET");
        when(totp.currentStep()).thenReturn(100L);
        when(totp.verify(any(), any(), anyLong(), anyInt())).thenReturn(-1L);
        when(totp.verify(any(), eq(RIGHT), anyLong(), anyInt())).thenReturn(100L);

        AuthLockoutPolicy first = new AuthLockoutPolicy();
        first.setMaxAttempts(3);
        first.setLockMinutes(PASSWORD_FIRST_LOCK);
        when(loginAttempts.stages()).thenReturn(List.of(first));
    }

    private String refusal(String code) {
        MfaService.MfaException e = catchThrowableOfType(MfaService.MfaException.class, () -> service.verify(USER, code));
        assertThat(e).as("refused").isNotNull();
        return e.getReason();
    }

    @Test
    @DisplayName("a wrong code is counted and audited, and the transaction commits instead of rolling back")
    void wrongCodeIsKept() {
        assertThat(refusal("000000")).contains("wrong");
        assertThat(row.getFailedAttempts()).isEqualTo(1);
        verify(txManager).commit(tx);
        verify(txManager, never()).rollback(any());
        verify(audit).log(eq(USER), eq("MFA_FAILED"), eq("USER"), eq(USER), isNull(), any());
    }

    @Test
    @DisplayName("five wrong codes lock the factor for the password's first lock; the right code then waits")
    void fiveWrongCodesLockForThePasswordsTime() {
        for (int i = 0; i < 4; i++) refusal("000000");
        assertThat(refusal("000000")).contains("Try again in " + PASSWORD_FIRST_LOCK + " minutes");

        assertThat(row.getLockedUntil()).isAfter(LocalDateTime.now().plusMinutes(PASSWORD_FIRST_LOCK - 1));
        assertThat(refusal(RIGHT)).as("locked: even the right code").contains("Try again");
        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
    }

    @Test
    @DisplayName("after the timed lock, five more wrong codes lock the account and end its sessions")
    void fiveMoreLockTheAccount() {
        for (int i = 0; i < 5; i++) refusal("000000");
        row.setLockedUntil(LocalDateTime.now().minusSeconds(1)); // the timed lock is over

        for (int i = 0; i < 4; i++) refusal("000000");
        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(refusal("000000")).contains("account is locked");

        assertThat(user.getStatus()).isEqualTo(UserStatus.LOCKED);
        verify(sessions).endSessionsOf(USER);
        verify(audit).log(eq(USER), eq("ACCOUNT_LOCKED"), eq("USER"), eq(USER), isNull(), any());
    }

    @Test
    @DisplayName("R6-03: once the account is locked, the right code on the same sign-in is refused and not counted")
    void rightCodeAfterTheAccountLockIsRefused() {
        for (int i = 0; i < 5; i++) refusal("000000");
        row.setLockedUntil(LocalDateTime.now().minusSeconds(1));
        for (int i = 0; i < 5; i++) refusal("000000");
        assertThat(user.getStatus()).isEqualTo(UserStatus.LOCKED);
        assertThat(row.getLockedUntil()).as("the account lock clears the timed one").isNull();

        assertThat(refusal(RIGHT)).contains("locked");
        assertThat(refusal("000000")).as("nor a wrong one").contains("locked");

        assertThat(row.getLastUsedStep()).as("the right code was never accepted").isNull();
        assertThat(row.getFailedAttempts()).as("no code is counted against a locked account").isEqualTo(10);
        verify(sessions).endSessionsOf(USER);
        verify(audit).log(eq(USER), eq("ACCOUNT_LOCKED"), eq("USER"), eq(USER), isNull(), any());
        verify(audit, org.mockito.Mockito.times(2)).log(eq(USER), eq("MFA_FAILED"), eq("USER"), eq(USER), isNull(),
                eq("account " + UserStatus.LOCKED));
    }

    @Test
    @DisplayName("R6-03: an account locked or disabled during its pending sign-in: no code is read")
    void accountClosedDuringThePendingSignIn() {
        user.setStatus(UserStatus.INACTIVE);
        assertThat(refusal(RIGHT)).contains("inactive");
        user.setStatus(UserStatus.LOCKED);
        assertThat(refusal(RIGHT)).contains("locked");
        user.setStatus(UserStatus.ACTIVE);
        user.setIsDeleted(true);
        assertThat(refusal(RIGHT)).contains("Unknown account");

        verify(totp, never()).verify(any(), any(), anyLong(), anyInt());
        assertThat(row.getFailedAttempts()).isZero();
        assertThatThrownBy(() -> service.confirmEnrolment(USER, RIGHT)).isInstanceOf(MfaService.MfaException.class);
        assertThatThrownBy(() -> service.startEnrolment(USER)).isInstanceOf(MfaService.MfaException.class);
    }

    @Test
    @DisplayName("a right code ends the run")
    void rightCodeResets() {
        refusal("000000");
        refusal("000000");
        service.verify(USER, RIGHT);
        assertThat(row.getFailedAttempts()).isZero();
    }

    @Test
    @DisplayName("an administrator's unlock clears the code count and lock as well as the password's")
    void unlockClearsTheCodeLadder() {
        row.setFailedAttempts(10);
        row.setLockedUntil(LocalDateTime.now().plusMinutes(5));
        LoginAttemptService real = new LoginAttemptService(mock(AuthLockoutPolicyRepository.class), userRepository,
                mock(SysParameterService.class), mfaRepository);

        real.clearLock(user);

        assertThat(row.getFailedAttempts()).isZero();
        assertThat(row.getLockedUntil()).isNull();
        verify(mfaRepository).save(row);
    }
}
