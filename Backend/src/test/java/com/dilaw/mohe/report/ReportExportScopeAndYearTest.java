package com.dilaw.mohe.report;

import com.dilaw.mohe.controler.DataVisualizationController;
import com.dilaw.mohe.controler.StudentExtensionStatisticsReportController;
import com.dilaw.mohe.dto.StudentExtensionStatisticsDto;
import com.dilaw.mohe.model.DataVisualization;
import com.dilaw.mohe.model.StudentExtension;
import com.dilaw.mohe.report.jasper.ReportParamBuilder;
import com.dilaw.mohe.repository.DataVisualizationRepository;
import com.dilaw.mohe.repository.StudentExtensionRepository;
import com.dilaw.mohe.service.DataVisualizationService;
import com.dilaw.mohe.service.StudentExtensionStatisticsReportService;
import com.dilaw.mohe.service.codelist.CodeListService;
import com.dilaw.mohe.ums.service.ReportCountryScopeHelper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * RPT-05 (data visualization narrowed in the application, not by RLS alone) and
 * RPT-06 (the student-extension export honours the year it is sent).
 */
class ReportExportScopeAndYearTest {

    // ── RPT-05 ───────────────────────────────────────────────────────────────

    private static ReportCountryScopeHelper scopedTo(List<Integer> ids) {
        ReportCountryScopeHelper helper = mock(ReportCountryScopeHelper.class);
        when(helper.resolveCountryIds(any(), any())).thenReturn(ids);
        when(helper.shouldApplyCountryFilter(any())).thenAnswer(i -> i.getArgument(0) != null);
        when(helper.inClauseIds(any(), anyBoolean())).thenAnswer(i ->
                i.getArgument(1, Boolean.class) ? i.getArgument(0) : List.of(-1));
        return helper;
    }

    private static DataVisualizationService dataVisualization(DataVisualizationRepository repo,
                                                              ReportCountryScopeHelper helper) {
        DataVisualizationService service = new DataVisualizationService();
        ReflectionTestUtils.setField(service, "repository", repo);
        ReflectionTestUtils.setField(service, "countryScopeHelper", helper);
        return service;
    }

    @Test
    @DisplayName("RPT-05: a scoped user's visualization queries are narrowed to their countries")
    void dataVisualizationIsNarrowedForAScopedUser() {
        DataVisualizationRepository repo = mock(DataVisualizationRepository.class);
        DataVisualizationService service = dataVisualization(repo, scopedTo(List.of(7)));

        service.getAllVisualizations(null, null);
        service.getVisualizationsByCategory("status", null, null);
        service.getVisualizationsByChartName("x", null, null);
        service.getVisualizationsByChartType("pie", null, null);
        service.getAllChartNames(null, null);

        verify(repo).findAllOrderedBySortOrder(true, List.of(7));
        verify(repo).findByCategory("status", true, List.of(7));
        verify(repo).findByChartName("x", true, List.of(7));
        verify(repo).findByChartType("pie", true, List.of(7));
        verify(repo).findAllChartNames(true, List.of(7));
    }

    @Test
    @DisplayName("RPT-05: the ministry is not narrowed")
    void dataVisualizationIsWholeForTheMinistry() {
        DataVisualizationRepository repo = mock(DataVisualizationRepository.class);
        dataVisualization(repo, scopedTo(null)).getAllVisualizations();

        verify(repo).findAllOrderedBySortOrder(false, List.of(-1));
    }

    @Test
    @DisplayName("RPT-05: the export passes the requested country to the scoped query")
    void dataVisualizationExportIsScoped() {
        DataVisualizationService service = mock(DataVisualizationService.class);
        List<DataVisualization> rows = List.of(new DataVisualization());
        when(service.getAllVisualizations(7, null)).thenReturn(rows);
        DataVisualizationController controller = new DataVisualizationController();
        ReflectionTestUtils.setField(controller, "service", service);
        ReportParamBuilder params = mock(ReportParamBuilder.class);
        when(params.build(anyString(), anyString())).thenReturn(new HashMap<>());
        ReflectionTestUtils.setField(controller, "reportParamBuilder", params);
        ReportExports exports = mock(ReportExports.class);
        ReflectionTestUtils.setField(controller, "reportExports", exports);

        controller.exportReport(null, 7, null, "ar");

        verify(service).getAllVisualizations(7, null);
        verify(exports).render(eq("data-visualization"), any(), eq(rows), eq("data-visualization"), any());
    }

    // ── RPT-06 ───────────────────────────────────────────────────────────────

    private static StudentExtension extension(String type, LocalDate start) {
        StudentExtension se = new StudentExtension();
        se.setExtensionType(type);
        se.setExtensionStartDate(start);
        return se;
    }

    @Test
    @DisplayName("RPT-06: the statistics for a year count only that year's extensions")
    void statisticsForAYear() {
        StudentExtensionRepository repo = mock(StudentExtensionRepository.class);
        when(repo.findAllScoped(anyBoolean(), any())).thenReturn(List.of(
                extension("Academic", LocalDate.of(2024, 3, 1)),
                extension("Academic", LocalDate.of(2025, 3, 1)),
                extension("Academic", LocalDate.of(2025, 9, 1)),
                extension("Medical", LocalDate.of(2024, 5, 1)),
                extension("Medical", null)));
        CodeListService codes = mock(CodeListService.class);
        when(codes.rows(anyString())).thenReturn(List.of());
        StudentExtensionStatisticsReportService service =
                new StudentExtensionStatisticsReportService(repo, scopedTo(null), codes);

        assertThat(service.statisticsFiltered(null, null, null, 2025))
                .singleElement()
                .satisfies(s -> {
                    assertThat(s.getExtensionType()).isEqualTo("Academic");
                    assertThat(s.getTotalExtensions()).isEqualTo(2L);
                });
        assertThat(service.statisticsFiltered(null, null, "Medical", 2024))
                .singleElement().satisfies(s -> assertThat(s.getTotalExtensions()).isEqualTo(1L));
        assertThat(service.statisticsFiltered(null, null, null, null))
                .as("no year: every extension, as before")
                .extracting(StudentExtensionStatisticsDto::getTotalExtensions).containsExactly(3L, 2L);
    }

    @Test
    @DisplayName("RPT-06: the export hands the year to the service")
    void exportPassesTheYear() {
        StudentExtensionStatisticsReportService service = mock(StudentExtensionStatisticsReportService.class);
        StudentExtensionStatisticsReportController controller = new StudentExtensionStatisticsReportController(service);
        ReportParamBuilder params = mock(ReportParamBuilder.class);
        when(params.build(anyString(), anyString())).thenReturn(new HashMap<>());
        ReflectionTestUtils.setField(controller, "reportParamBuilder", params);
        ReflectionTestUtils.setField(controller, "reportExports", mock(ReportExports.class));

        controller.exportReport(null, null, null, "Academic", 2025, "ar");

        verify(service).statisticsFiltered(null, null, "Academic", 2025);
    }
}
