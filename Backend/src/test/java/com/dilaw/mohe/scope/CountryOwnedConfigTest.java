package com.dilaw.mohe.scope;

import com.dilaw.mohe.notification.dto.NotificationSettingsDto;
import com.dilaw.mohe.notification.service.NotificationSettingsService;
import com.dilaw.mohe.security.policy.PolicyDenied;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** S-06 on the notification settings: who may write which row. Rolled back. */
@Tag("integration")
@SpringBootTest
@Transactional
class CountryOwnedConfigTest {

    @Autowired private NotificationSettingsService settings;

    private static final int JORDAN = 1004;

    private static NotificationSettingsDto dto(Integer countryId) {
        NotificationSettingsDto d = new NotificationSettingsDto();
        d.setCountryId(countryId);
        d.setEmailEnabled(true);
        d.setSmtpHost("smtp.example.invalid");
        d.setSmtpPort(587);
        d.setSmtpFromAddress("noreply@example.invalid");
        return d;
    }

    @Test
    @DisplayName("Jordan's administrator writes Jordan's row; not the global one, not Saudi's")
    void countryAdmin() {
        assertThat(AsUser.call(AsUser.JO_ADMIN, () -> settings.updateSettings(dto(JORDAN), 13)).getCountryId()).isEqualTo(JORDAN);
        assertThatThrownBy(() -> AsUser.call(AsUser.JO_ADMIN, () -> settings.updateSettings(dto(null), 13)))
                .isInstanceOf(PolicyDenied.class).hasMessageContaining("ministry's");
        assertThatThrownBy(() -> AsUser.call(AsUser.JO_ADMIN, () -> settings.updateSettings(dto(1005), 13)))
                .isInstanceOf(PolicyDenied.class);
    }

    @Test
    @DisplayName("the ministry writes the global row and, for notification settings, Jordan's too; the super admin too")
    void ministry() {
        assertThat(AsUser.call(AsUser.ADMIN, () -> settings.updateSettings(dto(null), 2)).getCountryId()).isNull();
        assertThat(AsUser.call(AsUser.ADMIN, () -> settings.updateSettings(dto(JORDAN), 2)).getCountryId()).isEqualTo(JORDAN);
        assertThat(AsUser.call(AsUser.SUPERADMIN, () -> settings.updateSettings(dto(JORDAN), 1)).getCountryId()).isEqualTo(JORDAN);
        assertThat(AsUser.call(AsUser.ADMIN, () -> settings.getSettings(JORDAN))).isNotNull();
    }

    @Test
    @DisplayName("Jordan's administrator adds and deletes Jordan's row, but not Saudi's")
    void countryAdminAddsAndDeletes() {
        // Start from "Jordan has no row of its own", whatever the database holds.
        try {
            AsUser.run(AsUser.JO_ADMIN, () -> settings.delete(JORDAN));
        } catch (org.springframework.web.server.ResponseStatusException none) {
            // already none
        }
        assertThat(AsUser.call(AsUser.JO_ADMIN, () -> settings.create(dto(JORDAN), 13)).getCountryId()).isEqualTo(JORDAN);
        assertThatThrownBy(() -> AsUser.call(AsUser.JO_ADMIN, () -> settings.create(dto(JORDAN), 13)))
                .hasMessageContaining("already has its own");
        assertThatThrownBy(() -> AsUser.run(AsUser.JO_ADMIN, () -> settings.delete(1005)))
                .isInstanceOf(PolicyDenied.class);
        AsUser.run(AsUser.JO_ADMIN, () -> settings.delete(JORDAN));
        assertThat(AsUser.call(AsUser.JO_ADMIN, () -> settings.getSettings(JORDAN)).getInheritedFromGlobal()).isTrue();
    }
}
