package com.dilaw.mohe.report;

import com.dilaw.mohe.dto.ChartDataDto;
import com.dilaw.mohe.dto.GeoLocationDto;
import com.dilaw.mohe.dto.HierarchyNodeDto;
import com.dilaw.mohe.service.ChartDataService;
import com.dilaw.mohe.ums.service.CountryScopeService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.i18n.LocaleContextHolder;

import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

/**
 * The dashboard charts answer in the language of the request.
 *
 * <p>Three of them take their labels from a VW_* aggregate that carried only the
 * Arabic lookup name, so an English dashboard drew Arabic slice labels while the
 * status pie beside it — which reads the code list — drew English ones. V110
 * gave the three views an English sibling and the service now selects on the
 * request locale, as {@code getStudentsByStudyStatusDistribution} already did.
 */
@Tag("integration")
@SpringBootTest
class ChartDataLanguageTest {

    @Autowired private ChartDataService service;

    @MockBean private CountryScopeService countryScopeService;

    @BeforeEach
    void allowAllCountries() {
        when(countryScopeService.canAccessCountry(anyInt())).thenReturn(true);
        when(countryScopeService.getScope()).thenReturn(
                new CountryScopeService.CountryScope(1L, true, true, List.of()));
    }

    @AfterEach
    void resetLocale() {
        LocaleContextHolder.resetLocaleContext();
    }

    private static boolean hasArabic(String s) {
        return s != null && s.codePoints().anyMatch(c -> c >= 0x0600 && c <= 0x06FF);
    }

    private ChartDataDto in(Locale locale, java.util.function.Supplier<ChartDataDto> call) {
        LocaleContextHolder.setLocale(locale);
        return call.get();
    }

    @Test
    @DisplayName("country, degree and status labels are English for an English request")
    void englishRequestGetsEnglishLabels() {
        List<ChartDataDto> english = List.of(
                in(Locale.ENGLISH, () -> service.getStudentEnrollmentByCountry(null)),
                in(Locale.ENGLISH, () -> service.getStudentsByDegree(null)),
                in(Locale.ENGLISH, () -> service.getStudentStatusDistribution(null)));

        assertThat(english).allSatisfy(chart -> {
            assertThat(chart.getLabels()).as("the chart has rows to label").isNotEmpty();
            assertThat(chart.getLabels())
                    .as("an English dashboard should not be drawing Arabic labels")
                    .noneMatch(ChartDataLanguageTest::hasArabic);
        });
    }

    @Test
    @DisplayName("the same charts are Arabic for an Arabic request")
    void arabicRequestGetsArabicLabels() {
        Locale ar = Locale.forLanguageTag("ar");
        List<ChartDataDto> arabic = List.of(
                in(ar, () -> service.getStudentEnrollmentByCountry(null)),
                in(ar, () -> service.getStudentsByDegree(null)),
                in(ar, () -> service.getStudentStatusDistribution(null)));

        assertThat(arabic)
                .as("at least one label of each chart is a translated lookup name")
                .allSatisfy(chart -> assertThat(chart.getLabels()).anyMatch(ChartDataLanguageTest::hasArabic));
    }

    /** Every label the inline-SQL charts print: the three pies, the three trees, the map, the top countries. */
    private List<String> inlineChartLabels(Locale locale) {
        LocaleContextHolder.setLocale(locale);
        List<String> labels = new java.util.ArrayList<>();
        labels.addAll(service.getStudentsByMajorDistribution(null).getLabels());
        labels.addAll(service.getStudentsByUniversityTypeDistribution(null).getLabels());
        labels.addAll(service.getStudentsByGenderDistribution(null).getLabels());
        for (HierarchyNodeDto root : List.of(service.getLocationHierarchy(null),
                service.getOrganizationHierarchy(null), service.getAcademicHierarchy(null))) {
            collect(root, labels);
        }
        for (GeoLocationDto country : service.getGeographicDistribution(null)) {
            labels.add(country.getCountry());
            country.getGovernorates().forEach(g -> {
                labels.add(g.getName());
                g.getChildren().forEach(m -> labels.add(m.getName()));
            });
        }
        service.topCountries(null).forEach(p -> labels.add(p.getLabel()));
        return labels;
    }

    private static void collect(HierarchyNodeDto node, List<String> out) {
        out.add(node.getLabel());
        node.getChildren().forEach(child -> collect(child, out));
    }

    @Test
    @DisplayName("the inline-SQL charts label in the request's language and never say 'Unknown …' (QA B6)")
    void inlineChartsFollowTheLanguage() {
        // They selected the Arabic name with an English literal fallback, so an
        // English dashboard drew Arabic labels and an Arabic one said "Unknown
        // Country". The Arabic labels themselves must not change.
        List<String> english = inlineChartLabels(Locale.ENGLISH);
        List<String> arabic = inlineChartLabels(Locale.forLanguageTag("ar"));

        assertThat(english).as("the charts have rows to label").isNotEmpty();
        assertThat(english).contains("Egypt", "Human Medicine", "All Countries", "Ministry of Defense", "Unspecified");
        assertThat(arabic).contains("مصر", "جميع الدول", "وزارة الدفاع", "غير محدد");
        assertThat(english).doesNotContain("مصر", "جميع الدول", "وزارة الدفاع", "غير محدد");
        assertThat(english).noneMatch(l -> l != null && l.startsWith("Unknown"));
        assertThat(arabic).noneMatch(l -> l != null && l.startsWith("Unknown"));

        // Majors, countries, governorates, municipalities, statuses and genders are
        // fully translated, so nothing Arabic may survive in those charts. (Four
        // agency types have no English name and fall back to Arabic by design.)
        LocaleContextHolder.setLocale(Locale.ENGLISH);
        List<String> translated = new java.util.ArrayList<>(service.getStudentsByMajorDistribution(null).getLabels());
        collect(service.getLocationHierarchy(null), translated);
        collect(service.getAcademicHierarchy(null), translated);
        assertThat(translated).noneMatch(ChartDataLanguageTest::hasArabic);
    }

    @Test
    @DisplayName("translating a chart's labels does not change its slices")
    void inlineChartsKeepTheirFiguresAcrossLanguages() {
        ChartDataDto english = in(Locale.ENGLISH, () -> service.getStudentsByMajorDistribution(null));
        ChartDataDto arabic = in(Locale.forLanguageTag("ar"), () -> service.getStudentsByMajorDistribution(null));

        assertThat(english.getLabels()).hasSameSizeAs(arabic.getLabels());
        assertThat(english.getDatasets().get(0).getData())
                .as("the English chart groups on the Arabic name, so the counts are the Arabic chart's")
                .isEqualTo(arabic.getDatasets().get(0).getData());
    }

    @Test
    @DisplayName("the quarterly financial chart returns the quarters that have payments")
    void quarterlyChartIsNotEmpty() {
        // It joined MOHE_QUARTER on STUDENT_FINANCE.MOHE_QUARTER_ID, which is NULL
        // on every row and is not the key MOHE_QUARTER.QUARTER holds anyway — the
        // same defect V103 removed from V_Quarterly_Report. The chart drew nothing.
        ChartDataDto chart = service.getFinancialByQuarter(null);

        assertThat(chart.getLabels())
                .as("every payment has a date, so every payment falls in some quarter")
                .isNotEmpty();
        assertThat(chart.getLabels())
                .as("the period is derived from the payment date, as V103 derives it")
                .allMatch(l -> l.matches("\\d{4}-Q[1-4]"));
    }
}
