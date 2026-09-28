package com.dilaw.mohe.notification.service;

import com.dilaw.mohe.notification.dto.NotificationSettingsDto;
import com.dilaw.mohe.notification.model.NotificationSettings;
import com.dilaw.mohe.notification.repository.NotificationSettingsRepository;
import com.dilaw.mohe.ums.service.CountryScopeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@code smsWebhookHeaders} carries the SMS gateway credential
 * ("Authorization: Bearer ..."). Until September 2026 it was returned in clear
 * on every settings read while {@code smtpPassword} was masked. Both are now
 * write-only: null on read, blank-or-null on save means "keep what is stored".
 * The two halves must hold together — masking the read without the keep-on-blank
 * rule would make every save wipe the stored headers.
 */
class NotificationSettingsMaskingTest {

    private NotificationSettingsRepository repo;
    private CountryScopeService scopeService;
    private NotificationSettingsService service;
    private NotificationSettings global;

    @BeforeEach
    void setUp() {
        repo = mock(NotificationSettingsRepository.class);
        scopeService = mock(CountryScopeService.class);
        service = new NotificationSettingsService(repo, scopeService);
        // S-06: the global row is written through CountryOwnedConfigPolicy; a ministry subject here.
        org.springframework.test.util.ReflectionTestUtils.setField(service, "accessControl",
                com.dilaw.mohe.security.policy.TestAccessControl.as(
                        new com.dilaw.mohe.security.policy.AccessSubject(1L, com.dilaw.mohe.ums.model.AdminTier.SUPER_ADMIN, true,
                                List.of(), java.util.Set.of(), java.util.Set.of(), null),
                        new com.dilaw.mohe.security.policy.CountryOwnedConfigPolicy()));

        global = new NotificationSettings();
        global.setId(1);
        global.setSmtpPassword("stored-password");
        global.setSmsWebhookHeaders("[{\"key\":\"Authorization\",\"value\":\"Bearer stored\"}]");

        when(repo.findFirstByCountryIdIsNull()).thenReturn(Optional.of(global));
        when(repo.save(any(NotificationSettings.class))).thenAnswer(inv -> inv.getArgument(0));
        when(scopeService.getScope()).thenReturn(
                new CountryScopeService.CountryScope(1L, true, true, List.of()));
        when(scopeService.canAccessAllCountries()).thenReturn(true);
    }

    @Test
    @DisplayName("reads mask both the SMTP password and the SMS webhook headers")
    void readMasksCredentials() {
        NotificationSettingsDto dto = service.getSettings(null);
        assertThat(dto.getSmtpPassword()).isNull();
        assertThat(dto.getSmsWebhookHeaders()).isNull();
    }

    @Test
    @DisplayName("saving with blank/null headers keeps the stored value")
    void blankKeepsStored() {
        NotificationSettingsDto dto = service.getSettings(null); // headers come back null
        service.updateSettings(dto, 7);
        assertThat(global.getSmsWebhookHeaders()).contains("Bearer stored");
        assertThat(global.getSmtpPassword()).isEqualTo("stored-password");
    }

    @Test
    @DisplayName("saving with a new value replaces the stored one")
    void newValuePersists() {
        NotificationSettingsDto dto = service.getSettings(null);
        dto.setSmsWebhookHeaders("[{\"key\":\"Authorization\",\"value\":\"Bearer NEW\"}]");
        service.updateSettings(dto, 7);
        assertThat(global.getSmsWebhookHeaders()).contains("Bearer NEW");
    }

    @Test
    @DisplayName("the WhatsApp gateway headers are write-only too, and the read says whether one is stored")
    void whatsappHeadersMasked() {
        global.setWhatsappWebhookHeaders("[{\"key\":\"Authorization\",\"value\":\"Bearer wa\"}]");
        NotificationSettingsDto dto = service.getSettings(null);
        assertThat(dto.getWhatsappWebhookHeaders()).isNull();
        assertThat(dto.getWhatsappWebhookHeadersSet()).isTrue();
        assertThat(dto.getSmtpPasswordSet()).isTrue();

        service.updateSettings(dto, 7);
        assertThat(global.getWhatsappWebhookHeaders()).contains("Bearer wa");
    }

    @Test
    @DisplayName("adding a row for a country that has one is a 409; the global row is neither added nor deleted")
    void createAndDeleteRules() {
        NotificationSettings jordan = new NotificationSettings();
        jordan.setId(2);
        jordan.setCountryId(1004);
        when(repo.findByCountryId(1004)).thenReturn(Optional.of(jordan));

        NotificationSettingsDto dto = new NotificationSettingsDto();
        dto.setCountryId(1004);
        assertThatThrownBy(() -> service.create(dto, 7))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("already has its own");

        dto.setCountryId(null);
        assertThatThrownBy(() -> service.create(dto, 7)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.delete(null)).isInstanceOf(ResponseStatusException.class);

        service.delete(1004);
        verify(repo).delete(jordan);
    }

    @Test
    @DisplayName("a new country row is inserted, not merged, and gets the next id")
    void createInserts() {
        when(repo.findAll()).thenReturn(List.of(global));
        when(repo.saveAndFlush(any(NotificationSettings.class))).thenAnswer(inv -> inv.getArgument(0));

        NotificationSettingsDto dto = new NotificationSettingsDto();
        dto.setCountryId(1005);
        dto.setWhatsappEnabled(true);
        dto.setWhatsappWebhookUrl("https://graph.facebook.com/v21.0/123/messages");
        NotificationSettingsDto saved = service.create(dto, 7);

        assertThat(saved.getCountryId()).isEqualTo(1005);
        assertThat(saved.getWhatsappEnabled()).isTrue();
        verify(repo).saveAndFlush(org.mockito.ArgumentMatchers.argThat(s -> s.isNew() && s.getId() == 2));
    }

    @Test
    @DisplayName("a switched-on channel with nowhere to send, or a broken WhatsApp template, is refused")
    void validation() {
        NotificationSettingsDto dto = service.getSettings(null);
        dto.setWhatsappEnabled(true);
        assertThatThrownBy(() -> service.updateSettings(dto, 7)).hasMessageContaining("gateway URL is empty");

        dto.setWhatsappWebhookUrl("https://gw.example.com/send");
        dto.setWhatsappBodyTemplate("{\"to\":{{to}},\"text\":\"{{body}}\"}");
        assertThatThrownBy(() -> service.updateSettings(dto, 7)).hasMessageContaining("not valid JSON");

        dto.setWhatsappBodyTemplate(null);
        dto.setWhatsappMessageMode("AUTO");
        assertThatThrownBy(() -> service.updateSettings(dto, 7)).hasMessageContaining("enter the approved template");

        dto.setWhatsappApprovedTemplate(WhatsAppService.EXAMPLE_APPROVED_TEMPLATE);
        service.updateSettings(dto, 7);
        assertThat(global.getWhatsappMessageMode()).isEqualTo("AUTO");

        dto.setWhatsappMessageMode("SOMETIMES");
        assertThatThrownBy(() -> service.updateSettings(dto, 7)).hasMessageContaining("TEXT, TEMPLATE or AUTO");
    }
}
