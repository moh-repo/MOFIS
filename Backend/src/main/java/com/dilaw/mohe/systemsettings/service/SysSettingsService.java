package com.dilaw.mohe.systemsettings.service;

import com.dilaw.mohe.service.FileEncryptionService;
import com.dilaw.mohe.systemsettings.dto.SysSettingsDto;
import com.dilaw.mohe.systemsettings.model.SysSettings;
import com.dilaw.mohe.systemsettings.repository.SysSettingsRepository;
import com.dilaw.mohe.util.FileUploadValidator;
import com.dilaw.mohe.util.SafePathUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

@Service
public class SysSettingsService {

    private static final Logger log = LoggerFactory.getLogger(SysSettingsService.class);
    private static final int SINGLETON_ID = 1;

    /** The stored name, without extension; the extension is the detected type's. */
    static final String LOGO_BASENAME = "system-logo";

    private final SysSettingsRepository repo;
    private final FileEncryptionService fileEncryptionService;

    @Value("${file.upload-dir:uploads/}")
    private String uploadDir;

    public SysSettingsService(SysSettingsRepository repo, FileEncryptionService fileEncryptionService) {
        this.repo = repo;
        this.fileEncryptionService = fileEncryptionService;
    }

    public SysSettingsDto getSettings() {
        return repo.findById(SINGLETON_ID)
                .map(this::toDto)
                .orElseGet(SysSettingsDto::new);
    }

    public SysSettingsDto updateSettings(SysSettingsDto dto) {
        SysSettings entity = repo.findById(SINGLETON_ID).orElseGet(() -> {
            SysSettings s = new SysSettings();
            s.setId(SINGLETON_ID);
            return s;
        });
        entity.setSysNameAr(dto.getSysNameAr());
        entity.setSysNameEn(dto.getSysNameEn());
        return toDto(repo.save(entity));
    }

    /**
     * Stores the system logo.
     *
     * <p>The file goes through {@link FileUploadValidator#detectLogoExtension} like
     * every other upload in the system: declared type, signature bytes, and — for
     * an SVG — no script, event handler or entity. The stored extension is the
     * detected type's, so the content type {@link #getLogoContentType} later
     * chooses from that extension is the type the bytes actually are. The path is
     * resolved under the upload root by {@link SafePathUtils}. See the validator
     * for what went wrong before August 2026 (revision item 1.11).
     */
    public SysSettingsDto uploadLogo(MultipartFile file) {
        String ext;
        try {
            ext = FileUploadValidator.detectLogoExtension(file);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }

        String savedFilename = LOGO_BASENAME + "." + ext;
        String relativePath = "logos/" + savedFilename;
        Path targetPath = SafePathUtils.resolveUnderRoot(Paths.get(uploadDir), relativePath);
        try {
            Files.createDirectories(targetPath.getParent());
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not create logos directory");
        }

        try {
            fileEncryptionService.encryptMultipartFile(file, targetPath);
        } catch (IOException e) {
            log.error("Failed to save logo file", e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to save logo file");
        }
        removeOtherLogoFiles(targetPath);

        SysSettings entity = repo.findById(SINGLETON_ID).orElseGet(() -> {
            SysSettings s = new SysSettings();
            s.setId(SINGLETON_ID);
            return s;
        });
        entity.setSysLogoPath(relativePath);
        entity.setSysLogoFilename(savedFilename);
        return toDto(repo.save(entity));
    }

    /**
     * A logo replaced by one of another type would otherwise leave the old file
     * beside it ({@code system-logo.png} next to {@code system-logo.svg}). Only the
     * one the settings row points at is ever served, so this is tidiness, and
     * best-effort.
     */
    private static void removeOtherLogoFiles(Path current) {
        try (Stream<Path> files = Files.list(current.getParent())) {
            files.filter(p -> !p.equals(current))
                    .filter(p -> p.getFileName().toString().startsWith(LOGO_BASENAME + "."))
                    .forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                        } catch (IOException e) {
                            log.warn("Could not remove superseded logo {}", p);
                        }
                    });
        } catch (IOException e) {
            log.warn("Could not list logos directory {}", current.getParent());
        }
    }

    public byte[] getLogoBytes() {
        SysSettings entity = repo.findById(SINGLETON_ID)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Settings not found"));
        if (entity.getSysLogoPath() == null || entity.getSysLogoPath().isBlank()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No logo uploaded");
        }
        Path logoPath = SafePathUtils.resolveUnderRoot(Paths.get(uploadDir), entity.getSysLogoPath());
        try {
            if (!Files.exists(logoPath)) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Logo file not found");
            }
            return fileEncryptionService.readDecrypted(logoPath);
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to read logo file at {}", logoPath, e);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Logo file not found");
        }
    }

    /**
     * The content type to serve the logo as, from the stored extension — which
     * {@link #uploadLogo} derives from the detected type, so the two agree.
     * {@code webp} is kept for a file stored before the type check existed.
     */
    public String getLogoContentType() {
        SysSettings entity = repo.findById(SINGLETON_ID).orElse(null);
        if (entity == null || entity.getSysLogoFilename() == null) return "image/png";
        String fn = entity.getSysLogoFilename().toLowerCase();
        if (fn.endsWith(".jpg") || fn.endsWith(".jpeg")) return "image/jpeg";
        if (fn.endsWith(".gif")) return "image/gif";
        if (fn.endsWith(".svg")) return "image/svg+xml";
        if (fn.endsWith(".webp")) return "image/webp";
        return "image/png";
    }

    private SysSettingsDto toDto(SysSettings e) {
        SysSettingsDto dto = new SysSettingsDto();
        dto.setSysNameAr(e.getSysNameAr());
        dto.setSysNameEn(e.getSysNameEn());
        dto.setSysLogoPath(e.getSysLogoPath());
        dto.setSysLogoFilename(e.getSysLogoFilename());
        return dto;
    }
}
