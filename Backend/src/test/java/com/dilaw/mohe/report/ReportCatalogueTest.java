package com.dilaw.mohe.report;

import com.dilaw.mohe.exception.LocalizedMessage;
import com.dilaw.mohe.exception.LocalizedStatusException;
import com.dilaw.mohe.report.jasper.ReportCatalogue;
import com.dilaw.mohe.systemsettings.dto.ReportTypeSettingsDto;
import com.dilaw.mohe.systemsettings.model.ReportTypeSettings;
import com.dilaw.mohe.systemsettings.repository.ReportTypeSettingsRepository;
import com.dilaw.mohe.systemsettings.service.ReportSettingsService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * DEC-22 (review M3, plan W4-07): Report Settings' "Add type" picks from the
 * reports the system prints. The catalogue is the codes the controllers hand
 * {@code ReportParamBuilder.build}; this keeps the two equal.
 */
class ReportCatalogueTest {

    private static final Pattern BUILD = Pattern.compile("reportParamBuilder\\.build\\(\"([A-Z0-9_]+)\"");
    private static final Pattern CONSTANT = Pattern.compile("REPORT_CODE\\s*=\\s*\"([A-Z0-9_]+)\"");

    private static Set<String> codesInSource() throws IOException {
        Set<String> codes = new TreeSet<>();
        try (Stream<Path> files = Files.walk(Path.of("src/main/java"))) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                String text = Files.readString(file, StandardCharsets.UTF_8);
                for (Pattern p : List.of(BUILD, CONSTANT)) {
                    Matcher m = p.matcher(text);
                    while (m.find()) {
                        codes.add(m.group(1));
                    }
                }
            }
        }
        return codes;
    }

    @Test
    @DisplayName("the catalogue is exactly the report codes the controllers render")
    void catalogueMatchesTheControllers() throws IOException {
        assertThat(new TreeSet<>(ReportCatalogue.CODES))
                .as("add a new report's code to ReportCatalogue, remove a retired one")
                .isEqualTo(codesInSource());
    }

    @Test
    @DisplayName("a code no report prints is refused, in the reader's language")
    void unknownCodeIsRefused() {
        ReportTypeSettingsRepository repo = mock(ReportTypeSettingsRepository.class);
        ReportSettingsService service = new ReportSettingsService(repo);
        ReportTypeSettingsDto dto = new ReportTypeSettingsDto();
        dto.setReportTypeCode("MY_OWN_REPORT");

        assertThatThrownBy(() -> service.create(dto))
                .isInstanceOf(LocalizedStatusException.class)
                .satisfies(e -> assertThat(((LocalizedMessage) e).messageKey()).isEqualTo("reportSettings.type.unknown"));
        verify(repo, never()).save(any());
    }

    @Test
    @DisplayName("a catalogue code is accepted in any case and stored as the code")
    void catalogueCodeIsAccepted() {
        ReportTypeSettingsRepository repo = mock(ReportTypeSettingsRepository.class);
        when(repo.findByReportTypeCode("STUDENT_LIST")).thenReturn(Optional.empty());
        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));
        ReportSettingsService service = new ReportSettingsService(repo);
        ReportTypeSettingsDto dto = new ReportTypeSettingsDto();
        dto.setReportTypeCode(" student_list ");

        assertThat(service.create(dto).getReportTypeCode()).isEqualTo("STUDENT_LIST");
    }

    @Test
    @DisplayName("the picker offers only the codes that have no settings yet")
    void pickerOffersTheUnconfigured() {
        ReportTypeSettingsRepository repo = mock(ReportTypeSettingsRepository.class);
        ReportTypeSettings existing = new ReportTypeSettings();
        existing.setReportTypeCode("STUDENT_LIST");
        when(repo.findAll()).thenReturn(List.of(existing));

        List<String> offered = new ReportSettingsService(repo).unconfiguredCodes();

        assertThat(offered).doesNotContain("STUDENT_LIST").contains("APPROVALS_REQUESTS").isSorted();
        assertThat(offered).hasSize(ReportCatalogue.CODES.size() - 1);
    }
}
