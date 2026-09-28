package com.dilaw.mohe.ums.service;

import com.dilaw.mohe.exception.ErrorDetails;
import com.dilaw.mohe.exception.GlobalExceptionHandler;
import com.dilaw.mohe.exception.LocalizedStateException;
import com.dilaw.mohe.ums.model.UmsGroup;
import com.dilaw.mohe.ums.model.UmsRole;
import com.dilaw.mohe.ums.repository.UmsGroupRepository;
import com.dilaw.mohe.ums.repository.UmsRoleRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.context.request.ServletWebRequest;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * A system role or group (IsSystem = 1) is neither edited nor deleted (review M9;
 * plan W4-10).
 *
 * <p>{@code LocalizedRefusalsTest} holds the handler's half: a
 * {@link LocalizedStateException} is a 409 in the reader's language. This holds the
 * services' half — that the refusal is raised at all, with its key, before anything
 * is saved, deactivated, evicted or audited — and runs one refusal through the real
 * handler so the two halves are seen to meet. The answer used to be a 400, as if the
 * request were malformed; it is well formed, and the row is what refuses it.
 */
class SystemRoleAndGroupRefusalTest {

    private static final long SYSTEM_ID = 1L;
    private static final long CUSTOM_ID = 40L;
    private static final long OPERATOR = 1L;

    @ExtendWith(MockitoExtension.class)
    @org.junit.jupiter.api.Nested
    @DisplayName("UmsRoleService")
    class Roles {

        @Mock private UmsRoleRepository roleRepository;
        @Mock private UmsAuditLogService auditLogService;
        @Mock private UserAuthorityCache authorityCache;
        @InjectMocks private UmsRoleService service;

        private UmsRole role(long id, boolean system) {
            UmsRole r = new UmsRole();
            r.setId(id);
            r.setRoleName(system ? "SUPER_ADMIN" : "Custom role");
            r.setIsSystem(system);
            r.setIsActive(true);
            return r;
        }

        @Test
        @DisplayName("editing a system role is refused with ums.role.system, and nothing is saved")
        void systemRoleEditIsRefused() {
            UmsRole stored = role(SYSTEM_ID, true);
            when(roleRepository.findById(SYSTEM_ID)).thenReturn(Optional.of(stored));
            UmsRole change = new UmsRole();
            change.setRoleName("Renamed");

            LocalizedStateException refused = catchThrowableOfType(LocalizedStateException.class,
                    () -> service.updateRole(SYSTEM_ID, change, OPERATOR));

            assertThat(refused).as("refused").isNotNull();
            assertThat(refused.messageKey()).isEqualTo("ums.role.system");
            assertThat(stored.getRoleName()).as("the stored row is untouched").isEqualTo("SUPER_ADMIN");
            verify(roleRepository, never()).save(any());
            verifyNoInteractions(auditLogService);
        }

        @Test
        @DisplayName("deleting a system role is refused too: it stays active and nobody's authorities are evicted")
        void systemRoleDeleteIsRefused() {
            UmsRole stored = role(SYSTEM_ID, true);
            when(roleRepository.findById(SYSTEM_ID)).thenReturn(Optional.of(stored));

            LocalizedStateException refused = catchThrowableOfType(LocalizedStateException.class,
                    () -> service.deleteRole(SYSTEM_ID, OPERATOR));

            assertThat(refused).as("refused").isNotNull();
            assertThat(refused.messageKey()).isEqualTo("ums.role.system");
            assertThat(stored.getIsActive()).isTrue();
            verify(roleRepository, never()).save(any());
            verifyNoInteractions(authorityCache, auditLogService);
        }

        @Test
        @DisplayName("positive control: a role that is not a system role is edited and deleted as before")
        void customRoleIsEditable() {
            UmsRole stored = role(CUSTOM_ID, false);
            when(roleRepository.findById(CUSTOM_ID)).thenReturn(Optional.of(stored));
            when(roleRepository.save(any(UmsRole.class))).thenAnswer(inv -> inv.getArgument(0));
            UmsRole change = new UmsRole();
            change.setRoleName("Renamed");

            assertThat(service.updateRole(CUSTOM_ID, change, OPERATOR).getRoleName()).isEqualTo("Renamed");

            service.deleteRole(CUSTOM_ID, OPERATOR);
            assertThat(stored.getIsActive()).as("a delete deactivates").isFalse();
            verify(authorityCache).evictAll();
        }
    }

    @ExtendWith(MockitoExtension.class)
    @org.junit.jupiter.api.Nested
    @DisplayName("UmsGroupService")
    class Groups {

        @Mock private UmsGroupRepository groupRepository;
        @Mock private UmsAuditLogService auditLogService;
        @Mock private UserAuthorityCache authorityCache;
        @InjectMocks private UmsGroupService service;

        private UmsGroup group(long id, boolean system) {
            UmsGroup g = new UmsGroup();
            g.setId(id);
            g.setGroupName(system ? "Administrators" : "Custom group");
            g.setIsSystem(system);
            g.setIsActive(true);
            return g;
        }

        @Test
        @DisplayName("editing a system group is refused with ums.group.system, and nothing is saved")
        void systemGroupEditIsRefused() {
            UmsGroup stored = group(SYSTEM_ID, true);
            when(groupRepository.findById(SYSTEM_ID)).thenReturn(Optional.of(stored));
            UmsGroup change = new UmsGroup();
            change.setGroupName("Renamed");

            LocalizedStateException refused = catchThrowableOfType(LocalizedStateException.class,
                    () -> service.updateGroup(SYSTEM_ID, change, OPERATOR));

            assertThat(refused).as("refused").isNotNull();
            assertThat(refused.messageKey()).isEqualTo("ums.group.system");
            assertThat(stored.getGroupName()).isEqualTo("Administrators");
            verify(groupRepository, never()).save(any());
            verifyNoInteractions(auditLogService);
        }

        @Test
        @DisplayName("deleting a system group is refused too: it stays active and nobody's authorities are evicted")
        void systemGroupDeleteIsRefused() {
            UmsGroup stored = group(SYSTEM_ID, true);
            when(groupRepository.findById(SYSTEM_ID)).thenReturn(Optional.of(stored));

            LocalizedStateException refused = catchThrowableOfType(LocalizedStateException.class,
                    () -> service.deleteGroup(SYSTEM_ID, OPERATOR));

            assertThat(refused).as("refused").isNotNull();
            assertThat(refused.messageKey()).isEqualTo("ums.group.system");
            assertThat(stored.getIsActive()).isTrue();
            verify(groupRepository, never()).save(any());
            verifyNoInteractions(authorityCache, auditLogService);
        }

        @Test
        @DisplayName("positive control: a group that is not a system group is edited and deleted as before")
        void customGroupIsEditable() {
            UmsGroup stored = group(CUSTOM_ID, false);
            when(groupRepository.findById(CUSTOM_ID)).thenReturn(Optional.of(stored));
            when(groupRepository.save(any(UmsGroup.class))).thenAnswer(inv -> inv.getArgument(0));
            UmsGroup change = new UmsGroup();
            change.setGroupName("Renamed");

            assertThat(service.updateGroup(CUSTOM_ID, change, OPERATOR).getGroupName()).isEqualTo("Renamed");

            service.deleteGroup(CUSTOM_ID, OPERATOR);
            assertThat(stored.getIsActive()).isFalse();
            verify(authorityCache).evictAll();
        }
    }

    @Test
    @DisplayName("the refusal the service raises is answered 409 by the handler, in the reader's language")
    void theServiceRefusalIsA409() {
        UmsRoleRepository roles = org.mockito.Mockito.mock(UmsRoleRepository.class);
        UmsRole stored = new UmsRole();
        stored.setId(SYSTEM_ID);
        stored.setIsSystem(true);
        when(roles.findById(SYSTEM_ID)).thenReturn(Optional.of(stored));
        UmsRoleService service = new UmsRoleService();
        ReflectionTestUtils.setField(service, "roleRepository", roles);
        LocalizedStateException refused = catchThrowableOfType(LocalizedStateException.class,
                () -> service.deleteRole(SYSTEM_ID, OPERATOR));

        ResourceBundleMessageSource messages = new ResourceBundleMessageSource();
        messages.setBasename("messages");
        messages.setDefaultEncoding("UTF-8");
        messages.setFallbackToSystemLocale(false);
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        ReflectionTestUtils.setField(handler, "messageSource", messages);
        MockHttpServletRequest request = new MockHttpServletRequest("DELETE", "/mohe/api/v1/ums/roles/" + SYSTEM_ID);
        request.addHeader("Accept-Language", "en");

        ResponseEntity<?> answer = handler.illegalStateException(refused, new ServletWebRequest(request));

        assertThat(answer.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(((ErrorDetails) answer.getBody()).getMessage()).isNotBlank().doesNotContain("ums.role.system");
    }
}
