package com.dilaw.mohe.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Where a country's uploaded files live.
 *
 * <pre>
 *   uploads/countries/country_{countryId}/{section}/{name}_{timestamp}_{userId}.{ext}
 * </pre>
 *
 * <p>The country folder is the point: each attaché owns its own data, so a
 * country's files can be located, backed up, handed over or removed as one
 * thing, without a query. {@code section} is what each feature adds beneath it —
 * {@code dataImportXLS} for the quarterly scholarship spreadsheets today, others
 * later — which is why this is a service and not four lines inside one importer.
 *
 * <p>{@code country_{id}} rather than the country's name follows
 * {@link StudentFileStorageService}, which settled on {@code student_{id}} for the
 * same reason: an id is stable, a name is not, and a renamed country must not strand
 * its files in the old folder.
 */
@Service
public class CountryFileStorageService {

    /** Folder under the uploads root that every country folder sits in. */
    public static final String COUNTRIES_ROOT = "countries";

    /** Section for the quarterly scholarship spreadsheets. */
    public static final String SECTION_DATA_IMPORT_XLS = "dataImportXLS";

    private static final DateTimeFormatter UPLOAD_STAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss", Locale.ROOT);

    private final Path uploadRoot;

    public CountryFileStorageService(@Value("${file.upload-dir:uploads/}") String uploadDir) {
        this.uploadRoot = Paths.get(uploadDir).toAbsolutePath().normalize();
    }

    /** The uploads root every stored path is relative to. */
    public Path getUploadRoot() {
        return uploadRoot;
    }

    /**
     * The directory for one country's section, created if it is not there yet.
     *
     * @throws IllegalArgumentException if the country id is not a positive number —
     *         a null or 0 id would otherwise produce a shared {@code country_null}
     *         folder that every attaché writes into, which is the opposite of what
     *         this class is for.
     */
    public Path sectionDir(Integer countryId, String section) throws IOException {
        if (countryId == null || countryId <= 0) {
            throw new IllegalArgumentException("A country is required before a file can be stored.");
        }
        if (section == null || section.isBlank()) {
            throw new IllegalArgumentException("A storage section is required.");
        }
        Path dir = uploadRoot
                .resolve(COUNTRIES_ROOT)
                .resolve("country_" + countryId)
                .resolve(sanitiseSegment(section));
        Files.createDirectories(dir);
        return dir;
    }

    /**
     * {@code {originalName}_{yyyyMMdd_HHmmss}_{userId}.{ext}}, e.g.
     * {@code مصر 20253_20260824_013245_7.xlsx}.
     *
     * <p>The original name is kept because it is how the ministry refers to the
     * file — "مصر 20253" is the attaché and the quarter — and the stamp and user
     * id are what stop a second upload of the same sheet from overwriting the
     * first.
     */
    public String buildFilename(String originalName, Long userId, LocalDateTime uploadedAt) {
        String safe = sanitiseSegment(originalName == null || originalName.isBlank() ? "file" : originalName);
        String base = safe;
        String ext = "";
        int dot = safe.lastIndexOf('.');
        if (dot > 0) {
            base = safe.substring(0, dot);
            ext = safe.substring(dot);
        }
        String stamp = (uploadedAt == null ? LocalDateTime.now() : uploadedAt).format(UPLOAD_STAMP);
        return base + "_" + stamp + "_" + (userId == null ? "0" : userId) + ext;
    }

    /** The path as it should be recorded in the database: relative to the uploads root, forward slashes. */
    public String relativise(Path storedFile) {
        return uploadRoot.relativize(storedFile.toAbsolutePath().normalize())
                .toString().replace('\\', '/');
    }

    /**
     * Reduces a caller-supplied name to a single safe path segment.
     *
     * <p>The name reaching {@link #buildFilename} comes from a browser upload, so
     * it is attacker-controlled: {@code ../../../application.properties} has to
     * become a filename and not a way out of the country folder. Separators and
     * the characters Windows refuses are replaced rather than stripped, so two
     * different names cannot collapse into one.
     */
    private static String sanitiseSegment(String raw) {
        String name = raw.trim();
        // Anything after the last separator is the name the user meant; the rest
        // was a path, which is not theirs to choose.
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        name = name.replaceAll("[\\x00-\\x1f<>:\"|?*]", "_");
        // A name that is only dots ('.', '..') still walks the tree.
        if (name.isBlank() || name.chars().allMatch(c -> c == '.')) {
            name = "file";
        }
        // NTFS silently drops a trailing dot or space, which would make the
        // stored path and the recorded path disagree.
        name = name.replaceAll("[. ]+$", "");
        if (name.isBlank()) {
            name = "file";
        }
        return name.length() > 120 ? name.substring(0, 120) : name;
    }
}
