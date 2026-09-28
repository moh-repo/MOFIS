package com.dilaw.mohe.systemsettings.service;

import com.dilaw.mohe.systemsettings.dto.ReportTypeSettingsDto;
import com.dilaw.mohe.systemsettings.model.ReportTypeSettings;
import com.dilaw.mohe.systemsettings.repository.ReportTypeSettingsRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.stream.Collectors;
import com.dilaw.mohe.exception.LocalizedStatusException;
import com.dilaw.mohe.report.jasper.ReportCatalogue;

@Service
public class ReportSettingsService {

    private final ReportTypeSettingsRepository repo;

    public ReportSettingsService(ReportTypeSettingsRepository repo) {
        this.repo = repo;
    }

    public List<ReportTypeSettingsDto> getAllReportTypes() {
        return repo.findByIsActiveTrue().stream().map(this::toDto).collect(Collectors.toList());
    }

    public ReportTypeSettingsDto getByCode(String code) {
        return repo.findByReportTypeCode(code)
                .map(this::toDto)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Report type not found: " + code));
    }

    public ReportTypeSettingsDto create(ReportTypeSettingsDto dto) {
        // Only a code a report reads (DEC-22, review M3): any text was accepted, so
        // a settings row could be made that no report would ever use.
        String code = dto.getReportTypeCode() == null ? "" : dto.getReportTypeCode().trim().toUpperCase(java.util.Locale.ROOT);
        if (!ReportCatalogue.CODES.contains(code)) {
            throw new LocalizedStatusException(HttpStatus.BAD_REQUEST,
                    "Not a report code: " + dto.getReportTypeCode(), "reportSettings.type.unknown", code);
        }
        dto.setReportTypeCode(code);
        if (repo.findByReportTypeCode(code).isPresent()) {
            throw new LocalizedStatusException(HttpStatus.CONFLICT,
                    "Report type code already exists: " + code, "reportSettings.type.exists", code);
        }
        ReportTypeSettings entity = new ReportTypeSettings();
        applyDto(dto, entity);
        return toDto(repo.save(entity));
    }

    /** The report codes that have no settings row yet: what "Add type" may offer. */
    public List<String> unconfiguredCodes() {
        java.util.Set<String> configured = repo.findAll().stream()
                .map(ReportTypeSettings::getReportTypeCode)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        return ReportCatalogue.sorted().stream().filter(c -> !configured.contains(c)).toList();
    }

    public ReportTypeSettingsDto update(Integer id, ReportTypeSettingsDto dto) {
        ReportTypeSettings entity = repo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Report type not found: " + id));
        applyDto(dto, entity);
        return toDto(repo.save(entity));
    }

    public void delete(Integer id) {
        ReportTypeSettings entity = repo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Report type not found: " + id));
        entity.setIsActive(false);
        repo.save(entity);
    }

    // ── Mapping helpers ───────────────────────────────────────────────────────

    private void applyDto(ReportTypeSettingsDto dto, ReportTypeSettings e) {
        if (dto.getReportTypeCode() != null) e.setReportTypeCode(dto.getReportTypeCode());
        e.setReportTypeNameAr(dto.getReportTypeNameAr());
        e.setReportTypeNameEn(dto.getReportTypeNameEn());
        if (dto.getIsActive() != null) e.setIsActive(dto.getIsActive());

        e.setTitle01Ar(dto.getTitle01Ar()); e.setTitle01En(dto.getTitle01En());
        e.setTitle02Ar(dto.getTitle02Ar()); e.setTitle02En(dto.getTitle02En());
        e.setTitle03Ar(dto.getTitle03Ar()); e.setTitle03En(dto.getTitle03En());
        e.setTitle04Ar(dto.getTitle04Ar()); e.setTitle04En(dto.getTitle04En());
        e.setTitle05Ar(dto.getTitle05Ar()); e.setTitle05En(dto.getTitle05En());

        e.setFooterTextAr(dto.getFooterTextAr());
        e.setFooterTextEn(dto.getFooterTextEn());

        if (dto.getSignatoryCount() != null) e.setSignatoryCount(dto.getSignatoryCount());

        e.setSignatoryName01Ar(dto.getSignatoryName01Ar()); e.setSignatoryName01En(dto.getSignatoryName01En());
        e.setSignatoryPos01Ar(dto.getSignatoryPos01Ar());   e.setSignatoryPos01En(dto.getSignatoryPos01En());

        e.setSignatoryName02Ar(dto.getSignatoryName02Ar()); e.setSignatoryName02En(dto.getSignatoryName02En());
        e.setSignatoryPos02Ar(dto.getSignatoryPos02Ar());   e.setSignatoryPos02En(dto.getSignatoryPos02En());

        e.setSignatoryName03Ar(dto.getSignatoryName03Ar()); e.setSignatoryName03En(dto.getSignatoryName03En());
        e.setSignatoryPos03Ar(dto.getSignatoryPos03Ar());   e.setSignatoryPos03En(dto.getSignatoryPos03En());

        e.setSignatoryName04Ar(dto.getSignatoryName04Ar()); e.setSignatoryName04En(dto.getSignatoryName04En());
        e.setSignatoryPos04Ar(dto.getSignatoryPos04Ar());   e.setSignatoryPos04En(dto.getSignatoryPos04En());

        e.setSignatoryName05Ar(dto.getSignatoryName05Ar()); e.setSignatoryName05En(dto.getSignatoryName05En());
        e.setSignatoryPos05Ar(dto.getSignatoryPos05Ar());   e.setSignatoryPos05En(dto.getSignatoryPos05En());
    }

    private ReportTypeSettingsDto toDto(ReportTypeSettings e) {
        ReportTypeSettingsDto dto = new ReportTypeSettingsDto();
        dto.setId(e.getId());
        dto.setReportTypeCode(e.getReportTypeCode());
        dto.setReportTypeNameAr(e.getReportTypeNameAr());
        dto.setReportTypeNameEn(e.getReportTypeNameEn());
        dto.setIsActive(e.getIsActive());

        dto.setTitle01Ar(e.getTitle01Ar()); dto.setTitle01En(e.getTitle01En());
        dto.setTitle02Ar(e.getTitle02Ar()); dto.setTitle02En(e.getTitle02En());
        dto.setTitle03Ar(e.getTitle03Ar()); dto.setTitle03En(e.getTitle03En());
        dto.setTitle04Ar(e.getTitle04Ar()); dto.setTitle04En(e.getTitle04En());
        dto.setTitle05Ar(e.getTitle05Ar()); dto.setTitle05En(e.getTitle05En());

        dto.setFooterTextAr(e.getFooterTextAr());
        dto.setFooterTextEn(e.getFooterTextEn());

        dto.setSignatoryCount(e.getSignatoryCount());

        dto.setSignatoryName01Ar(e.getSignatoryName01Ar()); dto.setSignatoryName01En(e.getSignatoryName01En());
        dto.setSignatoryPos01Ar(e.getSignatoryPos01Ar());   dto.setSignatoryPos01En(e.getSignatoryPos01En());

        dto.setSignatoryName02Ar(e.getSignatoryName02Ar()); dto.setSignatoryName02En(e.getSignatoryName02En());
        dto.setSignatoryPos02Ar(e.getSignatoryPos02Ar());   dto.setSignatoryPos02En(e.getSignatoryPos02En());

        dto.setSignatoryName03Ar(e.getSignatoryName03Ar()); dto.setSignatoryName03En(e.getSignatoryName03En());
        dto.setSignatoryPos03Ar(e.getSignatoryPos03Ar());   dto.setSignatoryPos03En(e.getSignatoryPos03En());

        dto.setSignatoryName04Ar(e.getSignatoryName04Ar()); dto.setSignatoryName04En(e.getSignatoryName04En());
        dto.setSignatoryPos04Ar(e.getSignatoryPos04Ar());   dto.setSignatoryPos04En(e.getSignatoryPos04En());

        dto.setSignatoryName05Ar(e.getSignatoryName05Ar()); dto.setSignatoryName05En(e.getSignatoryName05En());
        dto.setSignatoryPos05Ar(e.getSignatoryPos05Ar());   dto.setSignatoryPos05En(e.getSignatoryPos05En());

        return dto;
    }
}
