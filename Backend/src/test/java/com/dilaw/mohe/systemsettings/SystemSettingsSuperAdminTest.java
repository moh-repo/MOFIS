package com.dilaw.mohe.systemsettings;

import com.dilaw.mohe.config.tenant.TenantContext;
import com.dilaw.mohe.scope.AsUser;
import com.dilaw.mohe.systemsettings.controller.SysSettingsController;
import com.dilaw.mohe.systemsettings.dto.SysSettingsDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The installation's own settings — the system's name and logo, the
 * {@code /settings/system-settings} screen — are the super admin's alone (review M2;
 * plan W4-10).
 *
 * <p>They were writable by any {@code SETTINGS.EDIT} holder, which is a settings
 * officer's permission, and so were the name on the login page and the logo every
 * visitor sees. The writes now carry {@code MoheSecurityExpressions.SUPER_ADMIN}.
 * What is pinned: {@code SETTINGS.EDIT} alone is refused, and so is every other admin
 * tier — only {@code ROLE_SUPER_ADMIN} gets through. Reading stays open, since the
 * login page shows both.
 *
 * <p>The one write that goes through does so inside a transaction the test rolls back
 * itself; the logo's positive control is a file the upload refuses, so no file is
 * written either.
 */
@Tag("integration")
@SpringBootTest
class SystemSettingsSuperAdminTest {

    @Autowired private SysSettingsController controller;
    @Autowired private com.dilaw.mohe.systemsettings.repository.SysSettingsRepository repository;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager txManager;

    private static final List<List<String>> NOT_THE_SUPER_ADMIN = List.of(
            List.of("SETTINGS.EDIT"),
            List.of("SETTINGS.VIEW", "SETTINGS.EDIT", "UMS_ADMIN.EDIT"),
            List.of("ROLE_MINISTRY_ADMIN", "SETTINGS.EDIT"),
            List.of("ROLE_COUNTRY_ADMIN", "SETTINGS.EDIT"),
            List.of("ROLE_ADMIN", "SETTINGS.EDIT"));

    private static SysSettingsDto renamed() {
        SysSettingsDto dto = new SysSettingsDto();
        dto.setSysNameAr("اسم تجريبي W4-10");
        dto.setSysNameEn("W4-10 trial name");
        return dto;
    }

    private static MockMultipartFile notAnImage() {
        return new MockMultipartFile("file", "logo.png", "image/png", "not an image".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("SETTINGS.EDIT alone cannot rename the system or replace its logo, nor can any other admin tier")
    void onlyTheSuperAdminWrites() {
        for (List<String> authorities : NOT_THE_SUPER_ADMIN) {
            assertThatThrownBy(() -> AsUser.callWithAuthorities(AsUser.ADMIN, authorities,
                    () -> controller.updateSettings(renamed())))
                    .as("rename with %s", authorities)
                    .isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> AsUser.callWithAuthorities(AsUser.ADMIN, authorities,
                    () -> controller.uploadLogo(notAnImage())))
                    .as("logo with %s", authorities)
                    .isInstanceOf(AccessDeniedException.class);
        }
    }

    @Test
    @DisplayName("ROLE_SUPER_ADMIN renames the system")
    void superAdminRenames() {
        Map<String, Object> stored = TenantContext.runAsSystem(() -> new TransactionTemplate(txManager).execute(status -> {
            status.setRollbackOnly();

            SysSettingsDto answer = AsUser.callWithAuthorities(AsUser.SUPERADMIN, List.of("ROLE_SUPER_ADMIN"),
                    () -> controller.updateSettings(renamed())).getBody();
            assertThat(answer).isNotNull();
            assertThat(answer.getSysNameEn()).isEqualTo("W4-10 trial name");

            repository.flush();
            return jdbc.queryForMap("SELECT SYS_NAME_AR, SYS_NAME_EN FROM MOHECORE.SYS_SETTINGS WHERE SYS_SETTINGS_ID = 1");
        }));

        assertThat(stored.get("SYS_NAME_EN")).isEqualTo("W4-10 trial name");
        assertThat(stored.get("SYS_NAME_AR")).isEqualTo("اسم تجريبي W4-10");
    }

    @Test
    @DisplayName("ROLE_SUPER_ADMIN gets past the gate on the logo too: what stops a bad file is the upload check")
    void superAdminReachesTheLogoCheck() {
        assertThatThrownBy(() -> AsUser.callWithAuthorities(AsUser.SUPERADMIN, List.of("ROLE_SUPER_ADMIN"),
                () -> controller.uploadLogo(notAnImage())))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("reading the settings needs no permission at all: the login page shows the name")
    void readingIsOpen() {
        assertThat(AsUser.callWithAuthorities(AsUser.ADMIN, List.of(), () -> controller.getSettings()).getStatusCode())
                .isEqualTo(HttpStatus.OK);
    }
}
