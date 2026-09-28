package com.dilaw.mohe.ums.service;

import com.dilaw.mohe.config.SecurityRequestUtils;
import com.dilaw.mohe.ums.model.UmsUser;
import com.dilaw.mohe.ums.model.UmsUserSession;
import com.dilaw.mohe.ums.repository.UmsUserRepository;
import com.dilaw.mohe.ums.repository.UmsUserSessionRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * SEC-10: changing a staff password ends the sessions a stolen token could ride on.
 *
 * <p>Neither the holder's own change ({@code POST /ums/auth/change-password}) nor an
 * administrator's ({@code POST /ums/users/{id}/change-password},
 * {@code POST /ums/auth/reset-password}; all three end in
 * {@code UmsUserService.changePassword}) touched {@code UMSCORE.UserSessions}, so a
 * token taken before the change stayed valid after it.
 */
class PasswordChangeEndsSessionsTest {

    private static final long HOLDER = 5L;
    private static final long ADMIN = 1L;
    private static final String CURRENT_JTI = "jti-current";

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
        RequestContextHolder.resetRequestAttributes();
    }

    private static void signedInAs(long userId, String jti) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(String.valueOf(userId), null, List.of()));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(SecurityRequestUtils.JTI_ATTRIBUTE, jti);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @Nested
    @ExtendWith(MockitoExtension.class)
    @MockitoSettings(strictness = Strictness.LENIENT)
    @DisplayName("UmsUserService.changePassword")
    class Service {

        @Mock private UmsUserRepository userRepository;
        @Mock private CountryScopeService countryScopeService;
        @Mock private UmsPrivilegeGuard privilegeGuard;
        @Mock private MustChangePasswordGate mustChangePasswordGate;
        @Mock private UmsUserSessionService sessionService;
        @Mock private UserAuthorityCache authorityCache;
        @Mock private UmsAuditLogService auditLogService;
        @InjectMocks private UmsUserService service;

        @SuppressWarnings("unchecked")
        private void holderExists() {
            UmsUser user = new UmsUser();
            user.setId(HOLDER);
            user.setUserName("holder");
            when(countryScopeService.getScope()).thenReturn(CountryScopeService.CountryScope.noAccess());
            when(userRepository.findOne(any(Specification.class))).thenReturn(Optional.of(user));
        }

        @Test
        @DisplayName("the holder's own change ends every other session and keeps the one that made it")
        void ownChangeKeepsTheCurrentSession() {
            holderExists();
            signedInAs(HOLDER, CURRENT_JTI);

            service.changePassword(HOLDER, "Brand-New-Passw0rd-1", HOLDER);

            verify(sessionService).endSessionsOfExcept(HOLDER, CURRENT_JTI);
        }

        @Test
        @DisplayName("an administrator's change or reset ends every session of the target")
        void adminChangeEndsAllSessions() {
            holderExists();
            signedInAs(ADMIN, "jti-of-the-admin");

            service.changePassword(HOLDER, "Brand-New-Passw0rd-1", ADMIN);

            verify(sessionService).endSessionsOfExcept(org.mockito.ArgumentMatchers.eq(HOLDER), isNull());
            verify(authorityCache).evict(HOLDER);
        }

        @Test
        @DisplayName("outside a request (no current session known) every session ends")
        void noRequestEndsAllSessions() {
            holderExists();

            service.changePassword(HOLDER, "Brand-New-Passw0rd-1", HOLDER);

            verify(sessionService).endSessionsOfExcept(org.mockito.ArgumentMatchers.eq(HOLDER), isNull());
        }
    }

    @Nested
    @ExtendWith(MockitoExtension.class)
    @DisplayName("UmsUserSessionService.endSessionsOfExcept")
    class Sessions {

        @Mock private UmsUserSessionRepository sessionRepository;
        @InjectMocks private UmsUserSessionService service;

        private UmsUserSession session(String jti) {
            UmsUserSession s = new UmsUserSession();
            s.setTokenHash(jti);
            s.setIsActive(true);
            return s;
        }

        @Test
        @DisplayName("ends every active session but the one named")
        void endsAllButTheKeptOne() {
            UmsUserSession current = session(CURRENT_JTI);
            UmsUserSession stolen = session("jti-stolen");
            UmsUserSession other = session("jti-other-device");
            when(sessionRepository.findByUserIdAndIsActiveTrue(HOLDER)).thenReturn(List.of(current, stolen, other));

            assertThat(service.endSessionsOfExcept(HOLDER, CURRENT_JTI)).isEqualTo(2);

            assertThat(current.getIsActive()).isTrue();
            assertThat(stolen.getIsActive()).isFalse();
            assertThat(other.getIsActive()).isFalse();
        }

        @Test
        @DisplayName("with nothing to keep, ends them all")
        void endsAll() {
            UmsUserSession a = session("a");
            UmsUserSession b = session("b");
            when(sessionRepository.findByUserIdAndIsActiveTrue(HOLDER)).thenReturn(List.of(a, b));

            assertThat(service.endSessionsOfExcept(HOLDER, null)).isEqualTo(2);

            assertThat(a.getIsActive()).isFalse();
            assertThat(b.getIsActive()).isFalse();
        }
    }
}
