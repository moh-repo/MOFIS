package com.dilaw.mohe.studentportal;

import com.dilaw.mohe.codes.PortalAccountStatus;
import com.dilaw.mohe.studentportal.model.StudentSession;
import com.dilaw.mohe.studentportal.model.StudentUser;
import com.dilaw.mohe.studentportal.repository.StudentSessionRepository;
import com.dilaw.mohe.studentportal.repository.StudentUserRepository;
import com.dilaw.mohe.util.PortalTokens;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * QA SEC-001, decision D9-a: every seeded portal account shares one 8-character password
 * and none had to change it (941 of 941 active accounts on dev have never changed theirs).
 * A session of an account that has never chosen its own password is now held to the
 * change: every portal call but the change, the security status and the student's own
 * record answers 403 {@code PORTAL_PASSWORD_CHANGE_REQUIRED}. An administrator's reset is
 * the administrator's password, so it puts the account back in that state.
 */
class PortalMustChangePasswordTest {

    private static final String TOKEN = "token-of-st2";

    @Nested
    @ExtendWith(MockitoExtension.class)
    @MockitoSettings(strictness = Strictness.LENIENT)
    @DisplayName("the token interceptor")
    class Interceptor {

        @Mock private StudentUserAuthService auth;
        @Mock private StudentPortalAuthService legacy;
        @Mock private StudentPortalTenantScope tenantScope;
        @InjectMocks private StudentPortalTokenInterceptor interceptor;

        private MockHttpServletResponse call(boolean mustChange, String method, String path) throws Exception {
            ReflectionTestUtils.setField(interceptor, "allowedOrigins", "http://localhost:4200");
            when(auth.resolveFromToken(TOKEN)).thenReturn(Optional.of(
                    new StudentUserAuthService.ResolvedStudent(2, 7, 1, mustChange)));
            MockHttpServletRequest request = new MockHttpServletRequest(method, "/mohe/api/v1/student-portal" + path);
            request.addHeader(StudentPortalTokenInterceptor.HEADER_TOKEN, TOKEN);
            MockHttpServletResponse response = new MockHttpServletResponse();
            boolean proceeded = interceptor.preHandle(request, response, new Object());
            assertThat(proceeded).isEqualTo(response.getStatus() == 200);
            return response;
        }

        @Test
        @DisplayName("an account that never chose its password is refused its data with the change code")
        void refusedUntilChanged() throws Exception {
            MockHttpServletResponse response = call(true, "GET", "/me/data");
            assertThat(response.getStatus()).isEqualTo(403);
            assertThat(response.getContentAsString()).contains("PORTAL_PASSWORD_CHANGE_REQUIRED");
            assertThat(call(true, "POST", "/me/documents").getStatus()).isEqualTo(403);
            assertThat(call(true, "POST", "/me/security/totp/start").getStatus()).isEqualTo(403);
            assertThat(call(true, "GET", "/change-requests").getStatus()).isEqualTo(403);
        }

        @Test
        @DisplayName("R5-17: the refusal is in the student's language")
        void refusalInTheStudentsLanguage() throws Exception {
            org.springframework.context.support.ResourceBundleMessageSource messages =
                    new org.springframework.context.support.ResourceBundleMessageSource();
            messages.setBasename("messages");
            messages.setDefaultEncoding("UTF-8");
            messages.setFallbackToSystemLocale(false);
            ReflectionTestUtils.setField(interceptor, "messageSource", messages);
            ReflectionTestUtils.setField(interceptor, "allowedOrigins", "http://localhost:4200");
            when(auth.resolveFromToken(TOKEN)).thenReturn(Optional.of(new StudentUserAuthService.ResolvedStudent(2, 7, 1, true)));

            for (String[] lang : new String[][] {{"ar", "اختر كلمة مرور"}, {"en", "Choose your own password"}}) {
                MockHttpServletRequest request = new MockHttpServletRequest("GET", "/mohe/api/v1/student-portal/me/data");
                request.addHeader(StudentPortalTokenInterceptor.HEADER_TOKEN, TOKEN);
                request.addHeader("Accept-Language", lang[0]);
                MockHttpServletResponse response = new MockHttpServletResponse();
                response.setCharacterEncoding("UTF-8");
                interceptor.preHandle(request, response, new Object());
                assertThat(response.getStatus()).isEqualTo(403);
                assertThat(response.getContentAsString()).as(lang[0])
                        .contains("PORTAL_PASSWORD_CHANGE_REQUIRED").contains(lang[1]);
            }
        }

        @Test
        @DisplayName("it may change the password, read its security status and its own record")
        void theChangeItselfIsOpen() throws Exception {
            assertThat(call(true, "POST", "/me/change-password").getStatus()).isEqualTo(200);
            assertThat(call(true, "GET", "/me/security").getStatus()).isEqualTo(200);
            assertThat(call(true, "GET", "/me").getStatus()).isEqualTo(200);
            assertThat(call(true, "GET", "/me/").getStatus()).isEqualTo(200);
        }

        @Test
        @DisplayName("once the student has chosen a password, nothing is held")
        void afterTheChange() throws Exception {
            assertThat(call(false, "GET", "/me/data").getStatus()).isEqualTo(200);
        }
    }

    @Nested
    @ExtendWith(MockitoExtension.class)
    @MockitoSettings(strictness = Strictness.LENIENT)
    @DisplayName("the session")
    class Session {

        @Mock private StudentSessionRepository sessions;
        @Mock private StudentUserRepository users;
        @InjectMocks private StudentUserAuthService service;

        private StudentUserAuthService.ResolvedStudent resolve(Instant passwordChangedAt) {
            StudentSession s = new StudentSession();
            s.setStudentUserId(7);
            s.setStudentId(2);
            s.setStudyCountryInId(1);
            when(sessions.findByTokenAndExpiresAtAfterAndLoggedOutAtIsNull(eq(PortalTokens.hash(TOKEN)), any(Instant.class)))
                    .thenReturn(Optional.of(s));
            StudentUser u = new StudentUser();
            u.setStudentUserId(7);
            u.setStatus(PortalAccountStatus.ACTIVE);
            u.setIsDeleted(false);
            u.setPasswordChangedAt(passwordChangedAt);
            when(users.findById(7)).thenReturn(Optional.of(u));
            return service.resolveFromToken(TOKEN).orElseThrow();
        }

        @Test
        @DisplayName("is marked when the student has never changed the password, and not after")
        void marked() {
            assertThat(resolve(null).mustChangePassword()).isTrue();
            assertThat(resolve(Instant.now()).mustChangePassword()).isFalse();
        }
    }

    @Nested
    @ExtendWith(MockitoExtension.class)
    @MockitoSettings(strictness = Strictness.LENIENT)
    @DisplayName("an administrator's reset")
    class AdminReset {

        @Mock private StudentUserRepository userRepository;
        @Mock private StudentSessionRepository sessionRepository;
        @InjectMocks private StudentPortalAdminService service;

        @Test
        @DisplayName("leaves the account to be changed by the student at the next sign-in")
        void resetMustBeChanged() {
            StudentUser u = new StudentUser();
            u.setStudentUserId(7);
            u.setStatus(PortalAccountStatus.ACTIVE);
            u.setPasswordChangedAt(Instant.parse("2026-09-01T00:00:00Z"));

            service.resetPassword(u, "hash");

            assertThat(u.getPasswordChangedAt()).isNull();
        }
    }
}
