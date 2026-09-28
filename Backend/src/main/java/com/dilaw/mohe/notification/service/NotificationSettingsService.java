package com.dilaw.mohe.notification.service;

import com.dilaw.mohe.codes.NotificationChannel;
import com.dilaw.mohe.notification.dto.NotificationSettingsDto;
import com.dilaw.mohe.notification.dto.NotificationTestRequest;
import com.dilaw.mohe.notification.dto.NotificationTestResult;
import com.dilaw.mohe.notification.model.NotificationSettings;
import com.dilaw.mohe.notification.repository.NotificationSettingsRepository;
import com.dilaw.mohe.security.policy.CountryOwnedConfig;
import com.dilaw.mohe.service.CountryNameService;
import com.dilaw.mohe.ums.service.CountryScopeService;
import com.dilaw.mohe.util.PhoneNumbers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import com.dilaw.mohe.exception.LocalizedStatusException;

@Service
public class NotificationSettingsService {

    private static final int GLOBAL_ID = 1;
    private static final String WHAT = "notification settings";
    private static final Logger log = LoggerFactory.getLogger(NotificationSettingsService.class);
    private static final java.util.regex.Pattern EMAIL_SHAPE =
            java.util.regex.Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final NotificationSettingsRepository repo;
    private final CountryScopeService countryScopeService;

    @org.springframework.beans.factory.annotation.Autowired
    private com.dilaw.mohe.security.policy.AccessControl accessControl;

    @org.springframework.beans.factory.annotation.Autowired
    private EmailService emailService;

    @org.springframework.beans.factory.annotation.Autowired
    private SmsService smsService;

    @org.springframework.beans.factory.annotation.Autowired
    private WhatsAppService whatsAppService;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private CountryNameService countryNameService;

    public NotificationSettingsService(NotificationSettingsRepository repo,
                                       CountryScopeService countryScopeService) {
        this.repo = repo;
        this.countryScopeService = countryScopeService;
    }

    /** Whether a deliverable channel is switched on in a settings row. */
    public static boolean isEnabled(NotificationSettings cfg, String channel) {
        if (cfg == null || channel == null) return false;
        return switch (channel) {
            case NotificationChannel.EMAIL -> Boolean.TRUE.equals(cfg.getEmailEnabled());
            case NotificationChannel.SMS -> Boolean.TRUE.equals(cfg.getSmsEnabled());
            case NotificationChannel.WHATSAPP -> Boolean.TRUE.equals(cfg.getWhatsappEnabled());
            default -> false;
        };
    }

    /**
     * S-06, with the notification-settings exception: the global row is the
     * ministry's, an attaché's row is written by that attaché's staff or by the
     * ministry (see {@link com.dilaw.mohe.security.policy.CountryOwnedConfigPolicy}).
     */
    private static CountryOwnedConfig config(Integer countryId) {
        return new CountryOwnedConfig(WHAT, countryId, true);
    }

    public NotificationSettingsDto getSettings(Integer countryId) {
        CountryScopeService.CountryScope scope = countryScopeService.getScope();
        Integer effectiveCountryId = countryId;
        if (effectiveCountryId == null && !scope.isMinistryUser() && !scope.isAdmin()
                && !scope.getAllowedCountryIds().isEmpty()) {
            effectiveCountryId = scope.getAllowedCountryIds().get(0);
        }
        if (effectiveCountryId != null) {
            countryScopeService.validateCountryAccess(effectiveCountryId);
        }
        NotificationSettings global = getGlobalEntity();
        if (effectiveCountryId == null) {
            return toDto(global, false);
        }
        final Integer resolvedCountryId = effectiveCountryId;
        return repo.findByCountryId(resolvedCountryId)
                .map(e -> toDto(e, false))
                .orElseGet(() -> {
                    NotificationSettingsDto dto = toDto(global, true);
                    dto.setCountryId(resolvedCountryId);
                    dto.setCountryName(countryName(resolvedCountryId));
                    dto.setCanEdit(accessControl.isAllowed(config(resolvedCountryId), "WRITE"));
                    return dto;
                });
    }

    /**
     * Every settings row the caller may see: the global row first, then the
     * attachés' own rows by name. Ministry-level accounts see every attaché's
     * row whatever the country picker says; anyone else sees the attachés in
     * their scope. Secrets come back masked, with a flag saying whether one is set.
     */
    @Transactional(readOnly = true)
    public List<NotificationSettingsDto> list() {
        boolean ministry = countryScopeService.isMinistryLevelActor();
        List<Integer> allowed = countryScopeService.getScope().getAllowedCountryIds();
        List<NotificationSettingsDto> out = new ArrayList<>();
        out.add(toDto(getGlobalEntity(), false));
        repo.findAll().stream()
                .filter(e -> e.getCountryId() != null)
                .filter(e -> ministry || allowed.contains(e.getCountryId()))
                .map(e -> toDto(e, false))
                .sorted(Comparator.comparing(d -> d.getCountryName() == null ? "" : d.getCountryName()))
                .forEach(out::add);
        return out;
    }

    /** Resolve effective SMTP/SMS config for a send batch. */
    public NotificationSettings resolveForSend(java.util.List<Integer> targetCountryIds) {
        if (targetCountryIds != null && targetCountryIds.size() == 1) {
            Integer cid = targetCountryIds.get(0);
            return repo.findByCountryId(cid).orElseGet(this::getGlobalEntity);
        }
        return getGlobalEntity();
    }

    public NotificationSettings getEntity() {
        return getGlobalEntity();
    }

    public NotificationSettings getGlobalEntity() {
        return repo.findFirstByCountryIdIsNull()
                .or(() -> repo.findById(GLOBAL_ID))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification settings not configured"));
    }

    /**
     * Sends one message through the saved settings and reports what the relay or
     * webhook answered (review R7). There was no way to check a configuration
     * short of sending a real notification and reading its log row afterwards.
     *
     * <p>Uses what is stored, not what is on the screen: the SMTP password and the
     * webhook headers are write-only, so the form never holds them. An attaché
     * with no row of its own is tested against the ministry row, as a send would be.
     */
    public NotificationTestResult testSend(NotificationTestRequest req) {
        if (req == null) {
            throw new LocalizedStatusException(HttpStatus.BAD_REQUEST, "channel and to are required",
                    "notification.test.required");
        }
        Integer countryId = req.countryId();
        if (countryId != null) {
            countryScopeService.validateCountryAccess(countryId);
        } else {
            CountryScopeService.CountryScope scope = countryScopeService.getScope();
            // Ministry level by tier, as CountryOwnedConfigPolicy decides it (W4-10): isAdmin()
            // is true for a country admin too, who could test through the ministry's row.
            if (!scope.isMinistryUser() && (scope.getTier() == null || !scope.getTier().ministryLevel())) {
                throw new LocalizedStatusException(HttpStatus.FORBIDDEN, "Only the ministry may test the ministry-wide settings",
                        "notification.test.ministryOnly");
            }
        }
        String channel = req.channel() == null ? "" : req.channel().trim().toUpperCase(java.util.Locale.ROOT);
        String to = req.to() == null ? "" : req.to().trim();
        NotificationSettings cfg = countryId == null ? getGlobalEntity()
                : repo.findByCountryId(countryId).orElseGet(this::getGlobalEntity);

        String error;
        if (NotificationChannel.EMAIL.equals(channel)) {
            if (!EMAIL_SHAPE.matcher(to).matches()) {
                throw new LocalizedStatusException(HttpStatus.BAD_REQUEST, "Enter the email address to send the test to",
                        "notification.test.email");
            }
            error = emailService.send(cfg, to,
                    "رسالة تجريبية - Test message",
                    "<p dir=\"rtl\">هذه رسالة تجريبية من نظام إدارة الطلاب للتحقق من إعدادات البريد.</p>"
                            + "<p dir=\"ltr\">This is a test message from the student management system,"
                            + " sent to check the email settings.</p>");
        } else if (NotificationChannel.SMS.equals(channel) || NotificationChannel.WHATSAPP.equals(channel)) {
            if (to.isEmpty() || !PhoneNumbers.isValid(to, false)) {
                throw new LocalizedStatusException(HttpStatus.BAD_REQUEST, "Enter the phone number to send the test to",
                        "notification.test.phone");
            }
            error = NotificationChannel.SMS.equals(channel)
                    ? smsService.send(cfg, to, "رسالة تجريبية للتحقق من إعدادات الرسائل النصية. Test message.")
                    : whatsAppService.send(cfg, to, "رسالة تجريبية للتحقق من إعدادات واتساب. WhatsApp test message.");
        } else {
            throw new LocalizedStatusException(HttpStatus.BAD_REQUEST, "channel must be EMAIL, SMS or WHATSAPP",
                    "notification.test.channel");
        }
        boolean enabled = isEnabled(cfg, channel);
        log.info("Notification settings test ({}, country {}): {}", channel, countryId, error == null ? "sent" : error);
        return new NotificationTestResult(error == null, enabled, error);
    }

    /**
     * Adds an attaché's own settings row, which from then on is used instead of
     * the global one for sends to that attaché alone. The global row always
     * exists and is edited, never added.
     */
    @Transactional
    public NotificationSettingsDto create(NotificationSettingsDto dto, Integer updatedBy) {
        Integer countryId = dto.getCountryId();
        if (countryId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Choose the country; the settings for all countries already exist and are edited, not added");
        }
        accessControl.assertAllowed(config(countryId), "WRITE");
        if (repo.findByCountryId(countryId).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This country already has its own notification settings; edit them instead");
        }
        validate(dto);
        NotificationSettings entity = newRow(countryId);
        applyDto(entity, dto, updatedBy);
        return toDto(saveNew(entity), false);
    }

    @Transactional
    public NotificationSettingsDto updateSettings(NotificationSettingsDto dto, Integer updatedBy) {
        Integer countryId = dto.getCountryId();
        // S-06: the global row is the ministry's; an attaché's row is the attaché's or the ministry's.
        accessControl.assertAllowed(config(countryId), "WRITE");
        validate(dto);
        if (countryId == null) {
            NotificationSettings entity = getGlobalEntity();
            applyDto(entity, dto, updatedBy);
            entity.setCountryId(null);
            return toDto(repo.save(entity), false);
        }
        NotificationSettings existing = repo.findByCountryId(countryId).orElse(null);
        if (existing == null) {
            // The single-form screen saved an attaché's first row through PUT; still honoured.
            NotificationSettings entity = newRow(countryId);
            applyDto(entity, dto, updatedBy);
            return toDto(saveNew(entity), false);
        }
        applyDto(existing, dto, updatedBy);
        return toDto(repo.save(existing), false);
    }

    /**
     * Removes an attaché's own row; its sends fall back to the global settings.
     * The global row cannot be removed — sign-in codes and scheduled reports are
     * e-mailed through it.
     */
    @Transactional
    public void delete(Integer countryId) {
        if (countryId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The settings for all countries cannot be deleted");
        }
        accessControl.assertAllowed(config(countryId), "WRITE");
        NotificationSettings entity = repo.findByCountryId(countryId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "This country has no notification settings of its own"));
        repo.delete(entity);
    }

    private NotificationSettings newRow(Integer countryId) {
        NotificationSettings s = new NotificationSettings();
        s.setId(nextId());
        s.setCountryId(countryId);
        s.markNew();
        return s;
    }

    /**
     * Inserts, never merges: the id is max + 1, so two first saves at the same
     * moment pick the same one, and a merge would quietly overwrite the other
     * attaché's row. An insert fails on the key instead and the user retries.
     */
    private NotificationSettings saveNew(NotificationSettings entity) {
        try {
            return repo.saveAndFlush(entity);
        } catch (DataIntegrityViolationException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "The settings were saved by someone else at the same moment; reload the page and try again");
        }
    }

    private int nextId() {
        return repo.findAll().stream()
                .mapToInt(NotificationSettings::getId)
                .max()
                .orElse(GLOBAL_ID) + 1;
    }

    /** A switched-on channel must say where to send; a WhatsApp template must fill in to JSON. */
    private static void validate(NotificationSettingsDto dto) {
        if (Boolean.TRUE.equals(dto.getEmailEnabled()) && isBlank(dto.getSmtpHost())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email is enabled but the SMTP host is empty");
        }
        if (Boolean.TRUE.equals(dto.getSmsEnabled()) && isBlank(dto.getSmsWebhookUrl())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "SMS is enabled but the webhook URL is empty");
        }
        if (Boolean.TRUE.equals(dto.getWhatsappEnabled()) && isBlank(dto.getWhatsappWebhookUrl())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "WhatsApp is enabled but the gateway URL is empty");
        }
        String problem = WhatsAppService.templateProblem(dto.getWhatsappBodyTemplate());
        if (problem == null) {
            problem = WhatsAppService.templateProblem(dto.getWhatsappApprovedTemplate(), "WhatsApp approved template");
        }
        if (problem != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, problem);
        }
        String mode = dto.getWhatsappMessageMode();
        if (mode != null && !mode.isBlank() && !WhatsAppService.MODES.contains(mode.trim().toUpperCase(java.util.Locale.ROOT))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "WhatsApp message mode must be TEXT, TEMPLATE or AUTO");
        }
        if (Boolean.TRUE.equals(dto.getWhatsappEnabled()) && !WhatsAppService.MODE_TEXT.equals(normalMode(mode))
                && isBlank(dto.getWhatsappApprovedTemplate())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "This WhatsApp message mode sends the approved template; enter the approved template body");
        }
    }

    private static String normalMode(String mode) {
        return mode == null || mode.isBlank() ? WhatsAppService.MODE_TEXT : mode.trim().toUpperCase(java.util.Locale.ROOT);
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private void applyDto(NotificationSettings entity, NotificationSettingsDto dto, Integer updatedBy) {
        entity.setEmailEnabled(dto.getEmailEnabled() != null ? dto.getEmailEnabled() : false);
        entity.setSmtpHost(dto.getSmtpHost());
        entity.setSmtpPort(dto.getSmtpPort() != null ? dto.getSmtpPort() : 587);
        entity.setSmtpUsername(dto.getSmtpUsername());
        if (dto.getSmtpPassword() != null && !dto.getSmtpPassword().isBlank()) {
            entity.setSmtpPassword(dto.getSmtpPassword());
        }
        entity.setSmtpFromAddress(dto.getSmtpFromAddress());
        entity.setSmtpFromName(dto.getSmtpFromName());
        entity.setSmtpUseTls(dto.getSmtpUseTls() != null ? dto.getSmtpUseTls() : true);
        entity.setSmtpUseSsl(dto.getSmtpUseSsl() != null ? dto.getSmtpUseSsl() : false);
        entity.setSmsEnabled(dto.getSmsEnabled() != null ? dto.getSmsEnabled() : false);
        entity.setSmsWebhookUrl(dto.getSmsWebhookUrl());
        entity.setSmsWebhookMethod(dto.getSmsWebhookMethod() != null ? dto.getSmsWebhookMethod() : "POST");
        // Write-only like smtpPassword: the headers usually carry the SMS gateway
        // credential ("Authorization: Bearer ..."), are masked on read, and a
        // blank value on save means "keep what is stored".
        if (dto.getSmsWebhookHeaders() != null && !dto.getSmsWebhookHeaders().isBlank()) {
            entity.setSmsWebhookHeaders(dto.getSmsWebhookHeaders());
        }
        entity.setSmsFromNumber(dto.getSmsFromNumber());
        entity.setWhatsappEnabled(dto.getWhatsappEnabled() != null ? dto.getWhatsappEnabled() : false);
        entity.setWhatsappWebhookUrl(dto.getWhatsappWebhookUrl());
        // Write-only, for the same reason as the SMS headers: the gateway token.
        if (dto.getWhatsappWebhookHeaders() != null && !dto.getWhatsappWebhookHeaders().isBlank()) {
            entity.setWhatsappWebhookHeaders(dto.getWhatsappWebhookHeaders());
        }
        entity.setWhatsappFromNumber(dto.getWhatsappFromNumber());
        entity.setWhatsappBodyTemplate(isBlank(dto.getWhatsappBodyTemplate()) ? null : dto.getWhatsappBodyTemplate());
        entity.setWhatsappMessageMode(normalMode(dto.getWhatsappMessageMode()));
        entity.setWhatsappApprovedTemplate(isBlank(dto.getWhatsappApprovedTemplate()) ? null : dto.getWhatsappApprovedTemplate());
        entity.setUpdatedBy(updatedBy);
    }

    private NotificationSettingsDto toDto(NotificationSettings e, boolean inheritedFromGlobal) {
        NotificationSettingsDto dto = new NotificationSettingsDto();
        dto.setCountryId(e.getCountryId());
        dto.setCountryName(countryName(e.getCountryId()));
        dto.setInheritedFromGlobal(inheritedFromGlobal);
        dto.setEmailEnabled(e.getEmailEnabled());
        dto.setSmtpHost(e.getSmtpHost());
        dto.setSmtpPort(e.getSmtpPort());
        dto.setSmtpUsername(e.getSmtpUsername());
        dto.setSmtpPassword(null);
        dto.setSmtpPasswordSet(!isBlank(e.getSmtpPassword()));
        dto.setSmtpFromAddress(e.getSmtpFromAddress());
        dto.setSmtpFromName(e.getSmtpFromName());
        dto.setSmtpUseTls(e.getSmtpUseTls());
        dto.setSmtpUseSsl(e.getSmtpUseSsl());
        dto.setSmsEnabled(e.getSmsEnabled());
        dto.setSmsWebhookUrl(e.getSmsWebhookUrl());
        dto.setSmsWebhookMethod(e.getSmsWebhookMethod());
        // Masked like smtpPassword: the headers carry the SMS gateway credential
        // and must not be readable back through the API.
        dto.setSmsWebhookHeaders(null);
        dto.setSmsWebhookHeadersSet(!isBlank(e.getSmsWebhookHeaders()));
        dto.setSmsFromNumber(e.getSmsFromNumber());
        dto.setWhatsappEnabled(e.getWhatsappEnabled());
        dto.setWhatsappWebhookUrl(e.getWhatsappWebhookUrl());
        dto.setWhatsappWebhookHeaders(null);
        dto.setWhatsappWebhookHeadersSet(!isBlank(e.getWhatsappWebhookHeaders()));
        dto.setWhatsappFromNumber(e.getWhatsappFromNumber());
        dto.setWhatsappBodyTemplate(e.getWhatsappBodyTemplate());
        dto.setWhatsappMessageMode(normalMode(e.getWhatsappMessageMode()));
        dto.setWhatsappApprovedTemplate(e.getWhatsappApprovedTemplate());
        dto.setUpdatedAt(e.getUpdatedAt());
        dto.setCanEdit(accessControl != null && accessControl.isAllowed(config(e.getCountryId()), "WRITE"));
        return dto;
    }

    private String countryName(Integer countryId) {
        if (countryId == null || countryNameService == null) return null;
        return countryNameService.name(countryId, true);
    }
}
