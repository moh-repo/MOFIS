package com.dilaw.mohe.ums.service;

import com.dilaw.mohe.exception.LocalizedArgumentException;
import com.dilaw.mohe.security.policy.PolicyDenied;
import com.dilaw.mohe.ums.model.UmsPermission;
import com.dilaw.mohe.ums.model.UmsRole;
import com.dilaw.mohe.ums.repository.UmsPermissionRepository;
import com.dilaw.mohe.ums.repository.UmsRoleRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * SEC-02 / SEC-03: the service half of the permission and role hardening.
 */
class PermissionAndRoleEditHardeningTest {

    private static final long ID = 40L;
    private static final long OPERATOR = 2L;

    @Nested
    @ExtendWith(MockitoExtension.class)
    @MockitoSettings(strictness = Strictness.LENIENT)
    @DisplayName("UmsPermissionService (SEC-02)")
    class Permissions {

        @Mock private UmsPermissionRepository permissionRepository;
        @Mock private UserAuthorityCache authorityCache;
        @Mock private UmsAuditLogService auditLogService;
        @InjectMocks private UmsPermissionService service;

        private UmsPermission stored() {
            UmsPermission p = new UmsPermission();
            p.setId(ID);
            p.setPermissionCode("STUDENTS.VIEW");
            p.setPermissionName("View students");
            p.setIsActive(true);
            when(permissionRepository.findById(ID)).thenReturn(Optional.of(p));
            when(permissionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            return p;
        }

        @Test
        @DisplayName("renaming a code (to SYSTEM.RESTORE, say) is refused and nothing is saved")
        void codeIsImmutable() {
            UmsPermission p = stored();
            UmsPermission change = new UmsPermission();
            change.setPermissionCode("SYSTEM.RESTORE");

            assertThatThrownBy(() -> service.updatePermission(ID, change, OPERATOR))
                    .isInstanceOf(LocalizedArgumentException.class);
            assertThat(p.getPermissionCode()).isEqualTo("STUDENTS.VIEW");
            verify(permissionRepository, never()).save(any());
            verifyNoInteractions(authorityCache, auditLogService);
        }

        @Test
        @DisplayName("editing the description keeps the code, evicts every holder's authorities, and is audited")
        void descriptiveEditEvictsAndAudits() {
            UmsPermission p = stored();
            UmsPermission change = new UmsPermission();
            change.setPermissionCode("STUDENTS.VIEW");
            change.setPermissionName("Read students");

            service.updatePermission(ID, change, OPERATOR);

            assertThat(p.getPermissionName()).isEqualTo("Read students");
            assertThat(p.getPermissionCode()).isEqualTo("STUDENTS.VIEW");
            verify(authorityCache).evictAll();
            verify(auditLogService).log(eq(OPERATOR), eq("UPDATE_PERMISSION"), eq("PERMISSION"), eq(ID), any(), any());
        }

        @Test
        @DisplayName("a delete takes effect for every holder at once, and is audited")
        void deleteEvictsAndAudits() {
            UmsPermission p = stored();

            service.deletePermission(ID, OPERATOR);

            assertThat(p.getIsActive()).isFalse();
            verify(authorityCache).evictAll();
            verify(auditLogService).log(eq(OPERATOR), eq("DELETE_PERMISSION"), eq("PERMISSION"), eq(ID),
                    eq("STUDENTS.VIEW"), any());
        }
    }

    @Nested
    @ExtendWith(MockitoExtension.class)
    @MockitoSettings(strictness = Strictness.LENIENT)
    @DisplayName("UmsRoleService (SEC-03)")
    class Roles {

        @Mock private UmsRoleRepository roleRepository;
        @Mock private UserAuthorityCache authorityCache;
        @Mock private UmsAuditLogService auditLogService;
        @Mock private UmsPrivilegeGuard privilegeGuard;
        @InjectMocks private UmsRoleService service;

        private UmsRole stored() {
            UmsRole r = new UmsRole();
            r.setId(ID);
            r.setRoleName("Registrars");
            r.setIsSystem(false);
            r.setIsActive(true);
            when(roleRepository.findById(ID)).thenReturn(Optional.of(r));
            when(roleRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            return r;
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {"MINISTRY_ADMIN", "SUPER_ADMIN", "COUNTRY_ADMIN", "MINISTRY_STAFF", "ministry_admin"})
        @DisplayName("a role may not be renamed to a tier role's name")
        void renameToTierIsRefused(String tierName) {
            UmsRole r = stored();
            UmsRole change = new UmsRole();
            change.setRoleName(tierName);

            assertThatThrownBy(() -> service.updateRole(ID, change, OPERATOR))
                    .isInstanceOf(LocalizedArgumentException.class);
            assertThat(r.getRoleName()).isEqualTo("Registrars");
            verify(roleRepository, never()).save(any());
        }

        @Test
        @DisplayName("an edit the privilege guard refuses changes nothing")
        void guardRefusalStopsTheEdit() {
            UmsRole r = stored();
            doThrow(new PolicyDenied("Role", "EDIT", "no")).when(privilegeGuard).assertMayEditRole(ID);
            UmsRole change = new UmsRole();
            change.setIsActive(false);

            assertThatThrownBy(() -> service.updateRole(ID, change, OPERATOR)).isInstanceOf(PolicyDenied.class);
            assertThatThrownBy(() -> service.deleteRole(ID, OPERATOR)).isInstanceOf(PolicyDenied.class);
            assertThat(r.getIsActive()).isTrue();
            verify(roleRepository, never()).save(any());
            verifyNoInteractions(authorityCache);
        }

        @Test
        @DisplayName("a permitted edit evicts every holder's authorities")
        void permittedEditEvicts() {
            stored();
            UmsRole change = new UmsRole();
            change.setIsActive(false);

            service.updateRole(ID, change, OPERATOR);

            verify(privilegeGuard).assertMayEditRole(ID);
            verify(authorityCache).evictAll();
            verify(auditLogService).log(eq(OPERATOR), eq("UPDATE_ROLE"), eq("ROLE"), eq(ID), anyString(), anyString());
        }
    }
}
