package com.dilaw.mohe.notification.service;

import com.dilaw.mohe.exception.LocalizedStatusException;
import com.dilaw.mohe.notification.dto.NotificationTestRequest;
import com.dilaw.mohe.notification.dto.NotificationTestResult;
import com.dilaw.mohe.notification.model.NotificationSettings;
import com.dilaw.mohe.notification.repository.NotificationSettingsRepository;
import com.dilaw.mohe.ums.service.CountryScopeService;
import com.dilaw.mohe.ums.service.CountryScopeService.CountryScope;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * The settings screen's "send a test" button, {@code POST /notification-settings/test}
 * (review R7; plan W4-10).
 *
 * <p>Four things are pinned. A request that could not be sent is refused with a keyed
 * 400 before any relay is touched, so the screen can say what is wrong in the reader's
 * language. The ministry-wide row is the ministry's to test: a country officer asking
 * for it gets 403, not a message through the ministry's relay. An attaché with no row
 * of its own is tested against the ministry row, as a real send would be. And a valid
 * request calls the one sender for its channel exactly once and reports what it
 * answered, together with whether the channel is switched on.
 *
 * <p>The senders are mocks: nothing is ever sent from here.
 */
class NotificationTestSendTest {

    private static final int JORDAN = 1004;

    private NotificationSettingsRepository repo;
    private CountryScopeService scopeService;
    private EmailService email;
    private SmsService sms;
    private WhatsAppService whatsApp;
    private NotificationSettingsService service;
    private NotificationSettings global;

    @BeforeEach
    void setUp() {
        repo = mock(NotificationSettingsRepository.class);
        scopeService = mock(CountryScopeService.class);
        email = mock(EmailService.class);
        sms = mock(SmsService.class);
        whatsApp = mock(WhatsAppService.class);
        service = new NotificationSettingsService(repo, scopeService);
        ReflectionTestUtils.setField(service, "emailService", email);
        ReflectionTestUtils.setField(service, "smsService", sms);
        ReflectionTestUtils.setField(service, "whatsAppService", whatsApp);

        global = new NotificationSettings();
        global.setId(1);
        global.setEmailEnabled(true);
        global.setSmsEnabled(false);
        global.setWhatsappEnabled(true);
        when(repo.findFirstByCountryIdIsNull()).thenReturn(Optional.of(global));
        when(repo.findByCountryId(JORDAN)).thenReturn(Optional.empty());
    }

    private void signedInAsMinistry() {
        when(scopeService.getScope()).thenReturn(new CountryScope(2L, true, true, List.of(1, JORDAN)));
    }

    /** Country staff of one attaché: neither ministry-level nor an administrator. */
    private void signedInAsCountryStaff() {
        when(scopeService.getScope()).thenReturn(new CountryScope(14L, false, false, List.of(JORDAN)));
    }

    private LocalizedStatusException refusal(NotificationTestRequest request) {
        return catchThrowableOfType(LocalizedStatusException.class, () -> service.testSend(request));
    }

    // ── A valid request: one call to one sender, and what it answered ────────

    @Test
    @DisplayName("a valid email test calls the relay once, through the ministry row, and reports that it went")
    void emailTestIsSentOnce() {
        signedInAsMinistry();
        when(email.send(same(global), eq("officer@example.org"), anyString(), anyString())).thenReturn(null);

        // The screen's own spelling: lower case, with the spaces a paste leaves behind.
        NotificationTestResult result = service.testSend(
                new NotificationTestRequest(null, " email ", "  officer@example.org "));

        assertThat(result).isEqualTo(new NotificationTestResult(true, true, null));
        verify(email, times(1)).send(same(global), eq("officer@example.org"), anyString(), anyString());
        verifyNoInteractions(sms, whatsApp);
    }

    @Test
    @DisplayName("a failed SMS test reports the gateway's own error, and that the channel is switched off")
    void smsFailureIsReported() {
        signedInAsMinistry();
        when(sms.send(same(global), eq("+20 100 123 4567"), anyString())).thenReturn("HTTP 401 from the gateway");

        NotificationTestResult result = service.testSend(new NotificationTestRequest(null, "SMS", "+20 100 123 4567"));

        assertThat(result.ok()).isFalse();
        assertThat(result.error()).isEqualTo("HTTP 401 from the gateway");
        assertThat(result.enabled())
                .as("real sends skip a channel that is off; the screen says so beside the result")
                .isFalse();
        verify(sms, times(1)).send(same(global), eq("+20 100 123 4567"), anyString());
        verifyNoInteractions(email, whatsApp);
    }

    @Test
    @DisplayName("a WhatsApp test goes through the WhatsApp gateway and nothing else")
    void whatsAppGoesToItsOwnGateway() {
        signedInAsMinistry();
        when(whatsApp.send(same(global), eq("00201001234567"), anyString())).thenReturn(null);

        NotificationTestResult result = service.testSend(new NotificationTestRequest(null, "WHATSAPP", "00201001234567"));

        assertThat(result).isEqualTo(new NotificationTestResult(true, true, null));
        verify(whatsApp, times(1)).send(same(global), eq("00201001234567"), anyString());
        verifyNoInteractions(email, sms);
    }

    // ── Whose settings are tested ─────────────────────────────────────────────

    @Test
    @DisplayName("an attaché with no row of its own is tested against the ministry row, after its scope is checked")
    void attacheWithoutARowUsesTheMinistryRow() {
        signedInAsCountryStaff();

        service.testSend(new NotificationTestRequest(JORDAN, "EMAIL", "officer@example.org"));

        verify(scopeService).validateCountryAccess(JORDAN);
        verify(email, times(1)).send(same(global), eq("officer@example.org"), anyString(), anyString());
    }

    @Test
    @DisplayName("an attaché with a row of its own is tested against that row, not the ministry's")
    void attacheWithARowUsesItsOwn() {
        signedInAsCountryStaff();
        NotificationSettings jordan = new NotificationSettings();
        jordan.setId(7);
        jordan.setCountryId(JORDAN);
        jordan.setEmailEnabled(false);
        when(repo.findByCountryId(JORDAN)).thenReturn(Optional.of(jordan));
        when(email.send(same(jordan), anyString(), anyString(), anyString())).thenReturn("535 authentication failed");

        NotificationTestResult result = service.testSend(new NotificationTestRequest(JORDAN, "EMAIL", "officer@example.org"));

        assertThat(result).isEqualTo(new NotificationTestResult(false, false, "535 authentication failed"));
        verify(email, never()).send(same(global), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("the ministry-wide row is the ministry's to test: country staff get 403 and nothing is sent")
    void countryStaffCannotTestTheMinistryRow() {
        signedInAsCountryStaff();

        LocalizedStatusException refused = refusal(new NotificationTestRequest(null, "EMAIL", "officer@example.org"));

        assertThat(refused).as("refused").isNotNull();
        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(refused.messageKey()).isEqualTo("notification.test.ministryOnly");
        verify(repo, never()).findFirstByCountryIdIsNull();
        verifyNoInteractions(email, sms, whatsApp);
    }

    @Test
    @DisplayName("a country admin may not test the ministry-wide row either (the admin flag is not ministry level)")
    void countryAdminCannotTestTheMinistryRow() {
        when(scopeService.getScope()).thenReturn(new CountryScope(13L, false, true, List.of(JORDAN),
                com.dilaw.mohe.ums.model.AdminTier.COUNTRY_ADMIN));

        LocalizedStatusException refused = refusal(new NotificationTestRequest(null, "EMAIL", "anyone@example.org"));

        assertThat(refused).as("refused").isNotNull();
        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(refused.messageKey()).isEqualTo("notification.test.ministryOnly");
        verifyNoInteractions(email, sms, whatsApp);
    }

    // ── Requests that could not be sent ───────────────────────────────────────

    @ParameterizedTest(name = "[{index}] {0} to \"{1}\" -> {2}")
    @CsvSource(delimiter = '|', value = {
            "FAX      | officer@example.org | notification.test.channel",
            "''       | officer@example.org | notification.test.channel",
            "EMAIL    | not-an-address      | notification.test.email",
            "EMAIL    | ''                  | notification.test.email",
            "EMAIL    | a@b                 | notification.test.email",
            "SMS      | ''                  | notification.test.phone",
            "SMS      | call me             | notification.test.phone",
            "SMS      | 1234567             | notification.test.phone",
            "WHATSAPP | ''                  | notification.test.phone",
    })
    @DisplayName("a request that cannot be sent is a keyed 400, and no relay is touched")
    void unsendableRequestsAreRefused(String channel, String to, String key) {
        signedInAsMinistry();

        LocalizedStatusException refused = refusal(new NotificationTestRequest(null, channel, to));

        assertThat(refused).as("refused").isNotNull();
        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(refused.messageKey()).isEqualTo(key);
        verifyNoInteractions(email, sms, whatsApp);
    }

    @Test
    @DisplayName("no body at all is a keyed 400 too")
    void missingBodyIsRefused() {
        LocalizedStatusException refused = refusal(null);

        assertThat(refused).as("refused").isNotNull();
        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(refused.messageKey()).isEqualTo("notification.test.required");
        verifyNoInteractions(email, sms, whatsApp);
    }
}
