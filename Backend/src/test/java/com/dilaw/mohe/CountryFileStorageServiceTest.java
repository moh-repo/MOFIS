package com.dilaw.mohe;

import com.dilaw.mohe.service.CountryFileStorageService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Where a country's uploads land, and what stops one country's upload from
 * landing anywhere else.
 */
class CountryFileStorageServiceTest {

    private static final LocalDateTime AT = LocalDateTime.of(2026, 8, 24, 1, 32, 45);

    private CountryFileStorageService service(Path root) {
        return new CountryFileStorageService(root.toString());
    }

    @Test
    @DisplayName("Each country gets its own folder, per section")
    void buildsCountrySectionPath(@TempDir Path root) throws Exception {
        Path dir = service(root).sectionDir(3, CountryFileStorageService.SECTION_DATA_IMPORT_XLS);

        assertThat(root.relativize(dir).toString().replace('\\', '/'))
                .isEqualTo("countries/country_3/dataImportXLS");
        assertThat(Files.isDirectory(dir)).isTrue();
    }

    @Test
    @DisplayName("Two countries never share a folder")
    void countriesAreSeparate(@TempDir Path root) throws Exception {
        CountryFileStorageService service = service(root);

        assertThat(service.sectionDir(3, "dataImportXLS"))
                .isNotEqualTo(service.sectionDir(4, "dataImportXLS"));
    }

    @Test
    @DisplayName("Refuses to store anything without a country")
    void requiresACountry(@TempDir Path root) {
        CountryFileStorageService service = service(root);

        // A null or 0 id would otherwise make one shared folder that every
        // attaché writes into — the opposite of what this class is for.
        assertThatThrownBy(() -> service.sectionDir(null, "dataImportXLS"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.sectionDir(0, "dataImportXLS"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Filename keeps the original name and adds the time and the uploader")
    void buildsFilename(@TempDir Path root) {
        String name = service(root).buildFilename("مصر 20253.xlsx", 7L, AT);

        assertThat(name).isEqualTo("مصر 20253_20260824_013245_7.xlsx");
    }

    @Test
    @DisplayName("Two uploads of the same workbook do not overwrite each other")
    void filenamesAreDistinct(@TempDir Path root) {
        CountryFileStorageService service = service(root);

        assertThat(service.buildFilename("q3.xlsx", 7L, AT))
                .isNotEqualTo(service.buildFilename("q3.xlsx", 7L, AT.plusSeconds(1)));
        assertThat(service.buildFilename("q3.xlsx", 7L, AT))
                .isNotEqualTo(service.buildFilename("q3.xlsx", 9L, AT));
    }

    @Test
    @DisplayName("A filename cannot climb out of the country folder")
    void sanitisesTraversal(@TempDir Path root) throws Exception {
        CountryFileStorageService service = service(root);
        Path dir = service.sectionDir(3, "dataImportXLS");

        for (String hostile : new String[] {
                "../../../application.properties",
                "..\\..\\evil.xlsx",
                "..",
                "  " }) {
            String name = service.buildFilename(hostile, 7L, AT);

            assertThat(name).doesNotContain("/").doesNotContain("\\");
            assertThat(dir.resolve(name).normalize().startsWith(dir))
                    .withFailMessage("'%s' escaped the country folder as '%s'", hostile, name)
                    .isTrue();
        }
    }

    @Test
    @DisplayName("Stored paths are recorded relative to the uploads root")
    void relativises(@TempDir Path root) throws Exception {
        CountryFileStorageService service = service(root);
        Path stored = service.sectionDir(3, "dataImportXLS").resolve("q3_20260824_013245_7.xlsx");

        assertThat(service.relativise(stored))
                .isEqualTo("countries/country_3/dataImportXLS/q3_20260824_013245_7.xlsx");
    }
}
