package com.dilaw.mohe.service;

import com.dilaw.mohe.dto.EnumOptionDto;
import com.dilaw.mohe.service.codelist.CodeListRegistry;
import com.dilaw.mohe.service.codelist.CodeListService;
import com.dilaw.mohe.service.codelist.CodeRow;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * {@code GET /api/v1/enums/{name}} — the dropdown options of a code list.
 * Since V072 every list comes from {@link CodeListService} (one cached
 * SELECT per list); the registry decides which names exist.
 */
@Service
public class EnumOptionsService {

    private final CodeListService codeLists;

    public EnumOptionsService(CodeListService codeLists) {
        this.codeLists = codeLists;
    }

    /** Active options of a list, labelled for {@code lang}; empty for an unknown list. */
    public List<EnumOptionDto> getOptions(String enumName, String lang) {
        String key = enumName == null ? "" : enumName.trim().toLowerCase(Locale.ROOT);
        if (!CodeListRegistry.exists(key)) {
            return List.of();
        }
        return codeLists.active(key).stream().map(row -> toDto(row, lang)).toList();
    }

    public static EnumOptionDto toDto(CodeRow row, String lang) {
        return new EnumOptionDto(row.name(lang), row.code(), row.nameAr(), row.nameEn(), row.sortOrder(),
                row.uiSeverity(), row.icon(), row.system(), row.active(), row.uiColor(), row.id());
    }

    public Optional<List<String>> listAvailable() {
        return Optional.of(CodeListRegistry.names().stream().sorted().toList());
    }
}
