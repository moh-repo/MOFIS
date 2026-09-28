package com.dilaw.mohe.service;

import com.dilaw.mohe.dto.LocalizedOptionDto;
import com.dilaw.mohe.model.ExtensionStatus;
import com.dilaw.mohe.model.ExtensionType;
import com.dilaw.mohe.repository.ExtensionStatusRepository;
import com.dilaw.mohe.repository.ExtensionTypeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Central place that turns the EXTENSION_TYPE / EXTENSION_STATUS lookup tables into
 * language-aware selectable options and resolves a stored (language-neutral) value back
 * to its display label for a given language. Loading the lookups once per request through
 * {@link #localizer(String)} keeps record mapping free of N+1 queries.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StudentExtensionLocalizationService {

    private final ExtensionTypeRepository extensionTypeRepository;
    private final ExtensionStatusRepository extensionStatusRepository;

    /** True when the caller's language is Arabic. */
    public static boolean isArabic(String lang) {
        return lang != null && lang.trim().toLowerCase(Locale.ROOT).startsWith("ar");
    }

    public Localizer localizer(String lang) {
        boolean arabic = isArabic(lang);

        List<ExtensionType> types = extensionTypeRepository.findAllActiveOrderedByDisplay();
        List<ExtensionStatus> statuses = extensionStatusRepository.findAllActiveOrderedByDisplay();

        List<LocalizedOptionDto> typeOptions = types.stream()
                .map(t -> option(t.getExtensionTypeNameEn(), t.getExtensionTypeNameEn(), t.getExtensionTypeNameAr(), arabic))
                .collect(Collectors.toList());

        List<LocalizedOptionDto> statusOptions = statuses.stream()
                .map(s -> option(s.getExtensionStatusNameEn(), s.getExtensionStatusNameEn(), s.getExtensionStatusNameAr(), arabic))
                .collect(Collectors.toList());

        Map<String, String> typeLabels = new HashMap<>();
        Map<String, String> typeCodes = new HashMap<>();
        for (ExtensionType t : types) {
            registerLabel(typeLabels, t.getExtensionTypeCode(), t.getExtensionTypeNameEn(), t.getExtensionTypeNameAr(), arabic);
            registerCode(typeCodes, t.getExtensionTypeCode(), t.getExtensionTypeNameEn(), t.getExtensionTypeNameAr());
        }

        Map<String, String> statusLabels = new HashMap<>();
        Map<String, String> statusCodes = new HashMap<>();
        for (ExtensionStatus s : statuses) {
            registerLabel(statusLabels, s.getExtensionStatusCode(), s.getExtensionStatusNameEn(), s.getExtensionStatusNameAr(), arabic);
            registerCode(statusCodes, s.getExtensionStatusCode(), s.getExtensionStatusNameEn(), s.getExtensionStatusNameAr());
        }

        return new Localizer(typeOptions, statusOptions, typeLabels, statusLabels, typeCodes, statusCodes);
    }

    private static LocalizedOptionDto option(String value, String nameEn, String nameAr, boolean arabic) {
        String labelEn = StringUtils.hasText(nameEn) ? nameEn : value;
        String labelAr = StringUtils.hasText(nameAr) ? nameAr : labelEn;
        return LocalizedOptionDto.builder()
                .value(value)
                .label(arabic ? labelAr : labelEn)
                .labelEn(labelEn)
                .labelAr(labelAr)
                .build();
    }

    /** Index a lookup row under its code and both names so any stored form resolves to a label. */
    private static void registerLabel(Map<String, String> labels, String code, String nameEn, String nameAr, boolean arabic) {
        String labelEn = StringUtils.hasText(nameEn) ? nameEn : code;
        String labelAr = StringUtils.hasText(nameAr) ? nameAr : labelEn;
        String label = arabic ? labelAr : labelEn;
        putKey(labels, code, label);
        putKey(labels, nameEn, label);
        putKey(labels, nameAr, label);
    }

    /**
     * Index a lookup row's code under its code and both names, so any stored form
     * resolves back to the code. STUDENT_EXTENSION keeps the English name ("In
     * Progress") — the staff form's option value — and a screen that names statuses
     * from the {@code extension-status} code list needs the code (QA SP-009).
     */
    private static void registerCode(Map<String, String> codes, String code, String nameEn, String nameAr) {
        if (!StringUtils.hasText(code)) {
            return;
        }
        putKey(codes, code, code);
        putKey(codes, nameEn, code);
        putKey(codes, nameAr, code);
    }

    private static void putKey(Map<String, String> labels, String key, String label) {
        if (StringUtils.hasText(key)) {
            labels.putIfAbsent(key.trim().toLowerCase(Locale.ROOT), label);
        }
    }

    /** Immutable snapshot of the lookup tables localized for one request. */
    public static final class Localizer {
        private final List<LocalizedOptionDto> typeOptions;
        private final List<LocalizedOptionDto> statusOptions;
        private final Map<String, String> typeLabels;
        private final Map<String, String> statusLabels;
        private final Map<String, String> typeCodes;
        private final Map<String, String> statusCodes;

        private Localizer(List<LocalizedOptionDto> typeOptions, List<LocalizedOptionDto> statusOptions,
                          Map<String, String> typeLabels, Map<String, String> statusLabels,
                          Map<String, String> typeCodes, Map<String, String> statusCodes) {
            this.typeOptions = typeOptions;
            this.statusOptions = statusOptions;
            this.typeLabels = typeLabels;
            this.statusLabels = statusLabels;
            this.typeCodes = typeCodes;
            this.statusCodes = statusCodes;
        }

        public List<LocalizedOptionDto> typeOptions() {
            return typeOptions;
        }

        public List<LocalizedOptionDto> statusOptions() {
            return statusOptions;
        }

        public String typeLabel(String value) {
            return resolve(typeLabels, value);
        }

        public String statusLabel(String value) {
            return resolve(statusLabels, value);
        }

        /** EXTENSION_TYPE_CODE for a stored type (code or either name); null when no active row matches. */
        public String typeCode(String value) {
            return resolveCode(typeCodes, value);
        }

        /** EXTENSION_STATUS_CODE for a stored status (code or either name); null when no active row matches. */
        public String statusCode(String value) {
            return resolveCode(statusCodes, value);
        }

        private static String resolve(Map<String, String> labels, String value) {
            if (!StringUtils.hasText(value)) {
                return value;
            }
            return labels.getOrDefault(value.trim().toLowerCase(Locale.ROOT), value);
        }

        private static String resolveCode(Map<String, String> codes, String value) {
            if (!StringUtils.hasText(value)) {
                return null;
            }
            return codes.get(value.trim().toLowerCase(Locale.ROOT));
        }
    }
}
