package com.dilaw.mohe.service;

import com.dilaw.mohe.codes.ReportLabelCode;
import com.dilaw.mohe.dto.EnrollmentStatisticsMetadataDto;
import com.dilaw.mohe.dto.EnrollmentStatisticsRowDto;
import com.dilaw.mohe.dto.LookupOptionDto;
import com.dilaw.mohe.model.EnrollmentStatistics;
import com.dilaw.mohe.model.MoheQuarter;
import com.dilaw.mohe.repository.EnrollmentStatisticsRepository;
import com.dilaw.mohe.repository.MoheQuarterRepository;
import com.dilaw.mohe.service.codelist.CodeListService;
import com.dilaw.mohe.ums.service.ReportCountryScopeHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Live QA R5-15 (M13 residue): the Enrollment Statistics Quarter filter offered 20253,
 * 20251, 20241 and 0 all as "Unspecified", and Degree and Study Type offered a bare "0".
 *
 * <p>The quarter options looked the packed code up by {@code MOHE_QUARTER.ID}, which is
 * the identity column (15 for 20253 in the live data), not by {@code QUARTER}, so no code
 * ever matched. The 0 in Degree and Study Type is the view's "not recorded" sentinel,
 * whose name columns are empty, so the id itself was printed.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EnrollmentStatisticsFilterLabelsTest {

    @Mock private EnrollmentStatisticsRepository repository;
    @Mock private MoheQuarterRepository moheQuarterRepository;
    @Mock private ReportCountryScopeHelper countryScopeHelper;
    @Mock private CodeListService codeLists;

    private EnrollmentStatisticsService rows;
    private EnrollmentStatisticsMetadataService metadata;

    private static final String[] ORDINAL_AR = {"الربع الأول", "الربع الثاني", "الربع الثالث", "الربع الرابع"};

    /** A MOHE_QUARTER row as the live table holds it: ID is not the packed code. */
    private static MoheQuarter quarter(int id, int year, int number) {
        MoheQuarter q = new MoheQuarter();
        q.setId(id);
        q.setQuarter(year * 10 + number);
        q.setQuarterYear(year);
        q.setQuarterNumber(number);
        q.setQuarterName(ORDINAL_AR[number - 1] + " " + year);
        return q;
    }

    private static EnrollmentStatistics row(int quarterCode, int degreeId, int studyTypeId, int count) {
        EnrollmentStatistics e = new EnrollmentStatistics();
        e.setEnrollmentQuarter(quarterCode);
        e.setStudyCountryInId(1);
        e.setStudentCountryId(1);
        e.setStudentMajorId(1);
        e.setStudentDegreeId(degreeId);
        e.setStudentStudyTypeId(studyTypeId);
        e.setStudentCount(count);
        return e;
    }

    @BeforeEach
    void wire() {
        rows = new EnrollmentStatisticsService();
        ReflectionTestUtils.setField(rows, "repository", repository);
        ReflectionTestUtils.setField(rows, "moheQuarterRepository", moheQuarterRepository);
        ReflectionTestUtils.setField(rows, "countryScopeHelper", countryScopeHelper);
        ReflectionTestUtils.setField(rows, "codeLists", codeLists);

        metadata = new EnrollmentStatisticsMetadataService();
        ReflectionTestUtils.setField(metadata, "enrollmentStatisticsRepository", repository);
        ReflectionTestUtils.setField(metadata, "countryScopeHelper", countryScopeHelper);
        ReflectionTestUtils.setField(metadata, "enrollmentStatisticsService", rows);

        // The live dimension (ID 10/13/15 against codes 20241/20251/20253); 20262 has no row.
        when(moheQuarterRepository.findAll()).thenReturn(List.of(
                quarter(10, 2024, 1), quarter(13, 2025, 1), quarter(15, 2025, 3)));
        when(repository.findDistinctQuarters(anyBoolean(), any())).thenReturn(List.of(20262, 20253, 20251, 20241, 0));
        when(repository.findDistinctDegrees(anyBoolean(), any())).thenReturn(List.of(
                new Object[]{0, "", ""},
                new Object[]{1, "جامعية", "Bachelor's Degree"}));
        when(repository.findDistinctStudyTypes(anyBoolean(), any())).thenReturn(List.of(
                new Object[]{0, "", ""},
                new Object[]{999, "غير مبين", "Not Specified"},
                new Object[]{4, "مساعدة - رسوم دراسية", "Assistance - Tuition Fees"}));

        for (int n = 1; n <= 4; n++) {
            when(codeLists.label("quarter-number", String.valueOf(n), "en")).thenReturn("Quarter " + n);
            when(codeLists.label("quarter-number", String.valueOf(n), "ar")).thenReturn(ORDINAL_AR[n - 1]);
        }
        when(codeLists.label("report-label", ReportLabelCode.LBL_UNSPECIFIED, "en")).thenReturn("Unspecified");
        when(codeLists.label("report-label", ReportLabelCode.LBL_UNSPECIFIED, "ar")).thenReturn("غير محدد");
    }

    private static List<String> names(List<LookupOptionDto> options) {
        return options.stream().map(LookupOptionDto::getName).toList();
    }

    @Test
    @DisplayName("quarter options read as real quarters, newest first, with one Unspecified for code 0")
    void quarterOptions() {
        EnrollmentStatisticsMetadataDto en = metadata.getMetadata("en", null, null);
        assertThat(en.getQuarterOptions()).extracting(LookupOptionDto::getId)
                .containsExactly(20262, 20253, 20251, 20241, 0);
        assertThat(names(en.getQuarterOptions())).containsExactly(
                "Quarter 2 2026", "Quarter 3 2025", "Quarter 1 2025", "Quarter 1 2024", "Unspecified");

        EnrollmentStatisticsMetadataDto ar = metadata.getMetadata("ar", null, null);
        assertThat(names(ar.getQuarterOptions())).containsExactly(
                "الربع الثاني 2026", "الربع الثالث 2025", "الربع الأول 2025", "الربع الأول 2024", "غير محدد");
    }

    @Test
    @DisplayName("degree and study type name the not-recorded 0 instead of printing it, and list it last")
    void notRecordedIsNamed() {
        EnrollmentStatisticsMetadataDto en = metadata.getMetadata("en", null, null);
        assertThat(names(en.getDegrees())).containsExactly("Bachelor's Degree", "Unspecified");
        assertThat(names(en.getStudyTypes())).containsExactly("Assistance - Tuition Fees", "Not Specified", "Unspecified");
        assertThat(en.getDegrees()).extracting(LookupOptionDto::getId).containsExactly(1, 0);
        assertThat(en.getStudyTypes()).extracting(LookupOptionDto::getId).containsExactly(4, 999, 0);

        EnrollmentStatisticsMetadataDto ar = metadata.getMetadata("ar", null, null);
        assertThat(names(ar.getDegrees())).containsExactly("جامعية", "غير محدد");
        assertThat(names(ar.getStudyTypes())).last().isEqualTo("غير محدد");
    }

    @Test
    @DisplayName("each quarter option carries the label its rows are printed with")
    void optionMatchesRowLabel() {
        when(repository.search(eq(20253), anyBoolean(), any(), any(), any(), any()))
                .thenReturn(List.of(row(20253, 1, 999, 357)));
        when(repository.search(eq(20262), anyBoolean(), any(), any(), any(), any()))
                .thenReturn(List.of(row(20262, 1, 4, 1)));
        when(repository.search(eq(0), anyBoolean(), any(), any(), any(), any()))
                .thenReturn(List.of(row(0, 0, 0, 3)));

        for (String lang : new String[]{"en", "ar"}) {
            for (LookupOptionDto option : metadata.getMetadata(lang, null, null).getQuarterOptions()) {
                List<EnrollmentStatisticsRowDto> found = rows.search(option.getId(), null, null, null, null, null, lang);
                assertThat(found).allSatisfy(r -> {
                    assertThat(r.getQuarterCode()).isEqualTo(option.getId());
                    assertThat(r.getQuarterLabel()).as("%s, code %s", lang, option.getId()).isEqualTo(option.getName());
                });
            }
        }

        // A code the ministry has no MOHE_QUARTER row for is read from the code itself.
        EnrollmentStatisticsRowDto undeclared = rows.search(20262, null, null, null, null, null, "en").get(0);
        assertThat(undeclared.getEnrollmentYear()).isEqualTo(2026);
        assertThat(undeclared.getEnrollmentQuarter()).isEqualTo(2);
    }
}
