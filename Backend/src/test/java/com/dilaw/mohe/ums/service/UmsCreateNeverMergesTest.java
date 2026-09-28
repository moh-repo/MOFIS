package com.dilaw.mohe.ums.service;

import com.dilaw.mohe.ums.model.UmsGroup;
import com.dilaw.mohe.ums.model.UmsPage;
import com.dilaw.mohe.ums.model.UmsPageSection;
import com.dilaw.mohe.ums.model.UmsPermission;
import com.dilaw.mohe.ums.model.UmsReport;
import com.dilaw.mohe.ums.model.UmsRole;
import com.dilaw.mohe.ums.model.UmsUser;
import com.dilaw.mohe.ums.repository.UmsFieldRepository;
import com.dilaw.mohe.ums.repository.UmsGroupRepository;
import com.dilaw.mohe.ums.repository.UmsPageRepository;
import com.dilaw.mohe.ums.repository.UmsPageSectionRepository;
import com.dilaw.mohe.ums.repository.UmsPermissionRepository;
import com.dilaw.mohe.ums.repository.UmsReportRepository;
import com.dilaw.mohe.ums.repository.UmsRoleRepository;
import com.dilaw.mohe.ums.repository.UmsUserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * SEC-01: a create endpoint never merges onto an existing row.
 *
 * <p>The UMS create endpoints bind the JPA entity itself. With a writable
 * {@code id}, a body carrying the id of an existing row made {@code save()} a
 * merge: {@code POST /ums/users/ {"id": <victim>, "userName": "x", "password": ...}}
 * replaced the victim's user name and password hash (account takeover), and the
 * same on {@code /ums/roles/} could rename a role to {@code MINISTRY_ADMIN}. Two
 * layers now refuse it: the id of a user, role, group and permission is read-only
 * to Jackson, and every create clears the id before it saves.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UmsCreateNeverMergesTest {

    private static final long VICTIM_ID = 1L;
    private static final long OPERATOR = 2L;

    @Mock private UmsUserRepository userRepository;
    @Mock private UmsRoleRepository roleRepository;
    @Mock private UmsGroupRepository groupRepository;
    @Mock private UmsPermissionRepository permissionRepository;
    @Mock private UmsReportRepository reportRepository;
    @Mock private UmsPageRepository pageRepository;
    @Mock private UmsPageSectionRepository sectionRepository;
    @Mock private UmsFieldRepository fieldRepository;
    @Mock private UmsAuditLogService auditLogService;
    @Mock private UmsPrivilegeGuard privilegeGuard;
    @Mock private UserAuthorityCache authorityCache;

    @InjectMocks private UmsUserService userService;
    @InjectMocks private UmsRoleService roleService;
    @InjectMocks private UmsGroupService groupService;
    @InjectMocks private UmsPermissionService permissionService;
    @InjectMocks private UmsReportService reportService;
    @InjectMocks private UmsPageService pageService;

    @ParameterizedTest(name = "{0}")
    @ValueSource(classes = {UmsUser.class, UmsRole.class, UmsGroup.class, UmsPermission.class})
    @DisplayName("an id in a request body is not bound")
    void idIsNotBindable(Class<?> type) throws Exception {
        Object bound = new ObjectMapper().readValue("{\"id\": " + VICTIM_ID + "}", type);

        assertThat(type.getMethod("getId").invoke(bound)).isNull();
    }

    @Test
    @DisplayName("a user created with a victim's id is saved as a new row, and the victim is never loaded or merged")
    void userCreateClearsTheId() {
        when(userRepository.save(any(UmsUser.class))).thenAnswer(inv -> inv.getArgument(0));
        UmsUser draft = new UmsUser();
        draft.setId(VICTIM_ID);
        draft.setUserName("attacker");
        draft.setFirstName("A");
        draft.setLastName("B");
        draft.setPassword("Corr3ct-Horse-Battery-9");

        userService.createUser(draft, OPERATOR);

        ArgumentCaptor<UmsUser> saved = ArgumentCaptor.forClass(UmsUser.class);
        verify(userRepository).save(saved.capture());
        assertThat(saved.getValue().getId()).as("a null id makes save() an insert").isNull();
    }

    @Test
    @DisplayName("roles, groups, permissions, reports, pages and sections are inserted, never merged")
    void otherCreatesClearTheId() {
        when(roleRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(groupRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(permissionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(reportRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(pageRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(sectionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UmsRole role = new UmsRole();
        role.setId(VICTIM_ID);
        role.setRoleName("Custom");
        assertThat(roleService.createRole(role, OPERATOR).getId()).isNull();

        UmsGroup group = new UmsGroup();
        group.setId(VICTIM_ID);
        group.setGroupName("Custom");
        assertThat(groupService.createGroup(group, OPERATOR).getId()).isNull();

        UmsPermission permission = new UmsPermission();
        permission.setId(VICTIM_ID);
        permission.setPermissionCode("CUSTOM.VIEW");
        assertThat(permissionService.createPermission(permission, OPERATOR).getId()).isNull();

        UmsReport report = new UmsReport();
        report.setId(VICTIM_ID);
        assertThat(reportService.createReport(report).getId()).isNull();

        UmsPage page = new UmsPage();
        page.setId(VICTIM_ID);
        page.setPageCode("CUSTOM_PAGE");
        assertThat(pageService.createPage(page).getId()).isNull();

        UmsPageSection section = new UmsPageSection();
        section.setId(VICTIM_ID);
        assertThat(pageService.createSection(section).getId()).isNull();
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"MINISTRY_ADMIN", "super_admin", " COUNTRY_ADMIN ", "Ministry_Staff"})
    @DisplayName("a new role may not take a tier role's name (SEC-03)")
    void createWithTierNameIsRefused(String name) {
        UmsRole role = new UmsRole();
        role.setRoleName(name);

        assertThatThrownBy(() -> roleService.createRole(role, OPERATOR))
                .isInstanceOf(com.dilaw.mohe.exception.LocalizedArgumentException.class);
        verify(roleRepository, org.mockito.Mockito.never()).save(any());
    }
}
