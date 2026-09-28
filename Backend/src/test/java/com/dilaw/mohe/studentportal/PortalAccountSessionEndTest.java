package com.dilaw.mohe.studentportal;

import com.dilaw.mohe.codes.PortalAccountStatus;
import com.dilaw.mohe.studentportal.model.StudentSession;
import com.dilaw.mohe.studentportal.model.StudentUser;
import com.dilaw.mohe.studentportal.repository.StudentSessionRepository;
import com.dilaw.mohe.studentportal.repository.StudentUserRepository;
import com.dilaw.mohe.ums.service.CountryScopeService;
import com.dilaw.mohe.ums.service.CountryScopeService.CountryScope;
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
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Live QA H4 (25 Sep 2026): setting a student's portal account to INACTIVE did not
 * end the student's open sessions; "My data" kept answering 200 for up to seven
 * days. A status change now ends them, and every request re-checks the account.
 */
class PortalAccountSessionEndTest {

    private static final int USER = 3;
    private static final String TOKEN = "token-of-st3";

    private static StudentUser user(String status, boolean deleted) {
        StudentUser u = new StudentUser();
        u.setStudentUserId(USER);
        u.setUsername("student3@mohe.edu.ye");
        u.setStatus(status);
        u.setIsDeleted(deleted);
        return u;
    }

    @Nested
    @ExtendWith(MockitoExtension.class)
    @MockitoSettings(strictness = Strictness.LENIENT)
    @DisplayName("every request")
    class EveryRequest {

        @Mock private StudentSessionRepository sessions;
        @Mock private StudentUserRepository users;
        @InjectMocks private StudentUserAuthService service;

        private void openSession() {
            StudentSession s = new StudentSession();
            s.setStudentUserId(USER);
            s.setStudentId(3);
            s.setStudyCountryInId(1);
            when(sessions.findByTokenAndExpiresAtAfterAndLoggedOutAtIsNull(eq(PortalTokens.hash(TOKEN)), any(Instant.class)))
                    .thenReturn(Optional.of(s));
        }

        @Test
        @DisplayName("an ACTIVE account's session resolves")
        void activeResolves() {
            openSession();
            when(users.findById(USER)).thenReturn(Optional.of(user(PortalAccountStatus.ACTIVE, false)));
            assertThat(service.resolveFromToken(TOKEN)).isPresent();
        }

        @Test
        @DisplayName("an INACTIVE or LOCKED account's session no longer resolves")
        void inactiveRefused() {
            openSession();
            when(users.findById(USER)).thenReturn(Optional.of(user(PortalAccountStatus.INACTIVE, false)));
            assertThat(service.resolveFromToken(TOKEN)).isEmpty();
            when(users.findById(USER)).thenReturn(Optional.of(user(PortalAccountStatus.LOCKED, false)));
            assertThat(service.resolveFromToken(TOKEN)).isEmpty();
        }

        @Test
        @DisplayName("a deleted account's session no longer resolves")
        void deletedRefused() {
            openSession();
            when(users.findById(USER)).thenReturn(Optional.of(user(PortalAccountStatus.ACTIVE, true)));
            assertThat(service.resolveFromToken(TOKEN)).isEmpty();
        }
    }

    @Nested
    @ExtendWith(MockitoExtension.class)
    @MockitoSettings(strictness = Strictness.LENIENT)
    @DisplayName("staff status change")
    class StatusChange {

        @Mock private StudentUserRepository userRepository;
        @Mock private StudentSessionRepository sessionRepository;
        @Mock private CountryScopeService countryScopeService;
        @InjectMocks private StudentPortalAdminService service;

        private void account(String status) {
            when(countryScopeService.getScope()).thenReturn(new CountryScope(1L, true, List.of()));
            when(userRepository.findOne(any(Specification.class))).thenReturn(Optional.of(user(status, false)));
            when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        }

        @Test
        @DisplayName("INACTIVE ends every session of the account")
        void inactiveEndsSessions() {
            account(PortalAccountStatus.ACTIVE);
            assertThat(service.updateStatus(USER, PortalAccountStatus.INACTIVE).getStatus())
                    .isEqualTo(PortalAccountStatus.INACTIVE);
            verify(sessionRepository).deleteByStudentUserId(USER);
        }

        @Test
        @DisplayName("ACTIVE leaves sessions alone")
        void activeKeepsSessions() {
            account(PortalAccountStatus.LOCKED);
            service.updateStatus(USER, PortalAccountStatus.ACTIVE);
            verify(sessionRepository, never()).deleteByStudentUserId(anyInt());
        }

        @Test
        @DisplayName("an administrator's password reset ends every session")
        void resetEndsSessions() {
            service.resetPassword(user(PortalAccountStatus.LOCKED, false), "hash");
            verify(sessionRepository).deleteByStudentUserId(USER);
        }
    }

    @Nested
    @ExtendWith(MockitoExtension.class)
    @MockitoSettings(strictness = Strictness.LENIENT)
    @DisplayName("self-service password change")
    class SelfService {

        @Mock private StudentSessionRepository sessionRepository;
        @Mock private StudentUserRepository studentUserRepository;
        @InjectMocks private StudentUserAuthService service;

        @Test
        @DisplayName("ends the other sessions and keeps the current one")
        void endsOtherSessions() {
            StudentUser u = user(PortalAccountStatus.ACTIVE, false);
            u.setPasswordHash(new BCryptPasswordEncoder().encode("Current-Pass-123"));
            when(studentUserRepository.findById(USER)).thenReturn(Optional.of(u));

            service.changePassword(USER, "Current-Pass-123", "Brand-New-Pass-456", TOKEN);

            verify(sessionRepository).deleteOtherSessions(USER, PortalTokens.hash(TOKEN));
            verify(sessionRepository, never()).deleteByStudentUserId(anyInt());
        }
    }
}
