package com.dilaw.mohe.service;

import com.dilaw.mohe.dto.LocalizedOptionDto;
import com.dilaw.mohe.repository.StudentFinancialDataFormRepository;
import com.dilaw.mohe.repository.StudentFinancialDataFormV2Repository;
import com.dilaw.mohe.service.codelist.CodeListService;
import com.dilaw.mohe.ums.service.ReportCountryScopeHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.when;

/**
 * Live QA R5-29 (M13 residue): the Financial Data Form v1/v2 Country filter listed the
 * view's Arabic country names on the English screen, because the metadata returned them
 * as bare strings.
 *
 * <p>The options now carry the name in the reader's language as the label. The value
 * stays the Arabic name, because the rows and the PDF are filtered by it.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FinancialDataFormCountryLabelsTest {

    private static final String EGYPT = "مصر";
    private static final String UK = "المملكة المتحدة";
    /** A name the country table does not hold. */
    private static final String UNKNOWN = "بلد قديم";

    @Mock private StudentFinancialDataFormRepository v1Repository;
    @Mock private StudentFinancialDataFormV2Repository v2Repository;
    @Mock private ReportCountryScopeHelper countryScopeHelper;
    @Mock private CodeListService codeLists;
    @Mock private CountryNameService.Loader countryLoader;

    private StudentFinancialDataFormService v1;
    private StudentFinancialDataFormV2Service v2;

    @BeforeEach
    void wire() {
        when(countryLoader.all()).thenReturn(List.of(
                new CountryNameService.CountryRef(1, EGYPT, "Egypt", true),
                new CountryNameService.CountryRef(2, UK, "United Kingdom", true)));
        CountryNameService countryNames = new CountryNameService(countryLoader);

        // Ministry scope: no country filter.
        when(countryScopeHelper.resolveCountryIds(any())).thenReturn(List.of());
        when(countryScopeHelper.shouldApplyCountryFilter(any())).thenReturn(false);
        when(countryScopeHelper.inClauseIds(any(), anyBoolean())).thenReturn(List.of());

        List<String> viewNames = List.of(UK, EGYPT, UNKNOWN);
        when(v1Repository.findDistinctCountriesScoped(anyBoolean(), any())).thenReturn(viewNames);
        when(v2Repository.findDistinctCountriesScoped(anyBoolean(), any())).thenReturn(viewNames);

        v1 = new StudentFinancialDataFormService();
        ReflectionTestUtils.setField(v1, "repository", v1Repository);
        ReflectionTestUtils.setField(v1, "countryNameService", countryNames);
        ReflectionTestUtils.setField(v1, "countryScopeHelper", countryScopeHelper);

        v2 = new StudentFinancialDataFormV2Service(v2Repository, countryScopeHelper, codeLists, countryNames);
    }

    @AfterEach
    void resetLocale() {
        LocaleContextHolder.resetLocaleContext();
    }

    @SuppressWarnings("unchecked")
    private static List<LocalizedOptionDto> countries(Map<String, Object> metadata) {
        return (List<LocalizedOptionDto>) metadata.get("countries");
    }

    @Test
    @DisplayName("English: the labels are English, the values stay the Arabic names the rows are filtered by")
    void english_labelsEnglish_valuesArabic() {
        LocaleContextHolder.setLocale(Locale.ENGLISH);

        for (Map<String, Object> metadata : List.of(v1.getMetadata(null), v2.getMetadata(null))) {
            assertThat(countries(metadata))
                    .extracting(LocalizedOptionDto::getLabel, LocalizedOptionDto::getValue)
                    // Sorted by label; a name the table does not know is shown as it is.
                    .containsExactly(
                            tuple("Egypt", EGYPT),
                            tuple("United Kingdom", UK),
                            tuple(UNKNOWN, UNKNOWN));
        }
    }

    @Test
    @DisplayName("Arabic: the labels are the Arabic names")
    void arabic_labelsArabic() {
        LocaleContextHolder.setLocale(Locale.forLanguageTag("ar"));

        for (Map<String, Object> metadata : List.of(v1.getMetadata(null), v2.getMetadata(null))) {
            assertThat(countries(metadata))
                    .extracting(LocalizedOptionDto::getLabel)
                    .containsExactlyInAnyOrder(EGYPT, UK, UNKNOWN);
            assertThat(countries(metadata)).allSatisfy(o -> assertThat(o.getLabel()).isEqualTo(o.getValue()));
        }
    }

    @Test
    @DisplayName("both names travel, so the page can re-label on a language switch")
    void bothNamesTravel() {
        LocaleContextHolder.setLocale(Locale.ENGLISH);

        assertThat(countries(v1.getMetadata(null)))
                .extracting(LocalizedOptionDto::getLabelEn, LocalizedOptionDto::getLabelAr)
                .contains(tuple("Egypt", EGYPT), tuple("United Kingdom", UK));
    }
}
