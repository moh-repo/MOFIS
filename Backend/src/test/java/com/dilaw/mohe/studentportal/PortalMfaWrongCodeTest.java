package com.dilaw.mohe.studentportal;

import com.dilaw.mohe.studentportal.model.StudentUser;
import com.dilaw.mohe.studentportal.repository.StudentUserRepository;
import com.dilaw.mohe.ums.service.MfaSecretCipher;
import com.dilaw.mohe.ums.service.TotpService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

/**
 * Live QA M16 (25 Sep 2026): a wrong authenticator code when turning two-factor
 * authentication on or off answered 401, and the portal ends the session on any 401, so a
 * mistyped code signed the student out. It is now a 400, like a wrong current password.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PortalMfaWrongCodeTest {

    private static final int USER = 42;

    @Mock private StudentUserRepository users;
    @Mock private TotpService totp;
    @Mock private MfaSecretCipher cipher;

    private StudentPortalMfaService service;
    private StudentUser user;

    @BeforeEach
    void setUp() {
        service = new StudentPortalMfaService(users, totp, cipher, "MOHE", 5, 15);
        user = new StudentUser();
        user.setTotpSecretEnc("enc");
        when(users.findById(USER)).thenReturn(Optional.of(user));
        when(cipher.decrypt(any())).thenReturn("SECRET");
        when(totp.currentStep()).thenReturn(100L);
        when(totp.verify(any(), any(), anyLong(), anyInt())).thenReturn(-1L);
    }

    private static HttpStatus status(Throwable e) {
        return HttpStatus.valueOf(((ResponseStatusException) e).getStatusCode().value());
    }

    @Test
    @DisplayName("turning it off with a wrong code: 400, so the session stays")
    void disableWrongCode() {
        user.setTotpEnabled(true);
        assertThatThrownBy(() -> service.disable(USER, "000000"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(HttpStatus.BAD_REQUEST));
        assertThat(user.getTotpEnabled()).isTrue();
    }

    @Test
    @DisplayName("confirming the set-up with a wrong code: 400, so the session stays")
    void confirmWrongCode() {
        user.setTotpEnabled(false);
        assertThatThrownBy(() -> service.confirm(USER, "000000"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(HttpStatus.BAD_REQUEST));
        assertThat(user.getTotpEnabled()).isFalse();
    }
}
