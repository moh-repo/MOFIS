package com.dilaw.mohe.util;

import com.dilaw.mohe.dto.LookupOptionDto;
import com.dilaw.mohe.dto.StudentListMetadataDto;
import com.dilaw.mohe.model.MoheQuarter;
import com.dilaw.mohe.model.StudentAgencyType;
import com.dilaw.mohe.model.StudentCurrentLevel;
import com.dilaw.mohe.model.StudentDegree;
import com.dilaw.mohe.model.StudentGender;
import com.dilaw.mohe.model.StudentMajor;
import com.dilaw.mohe.model.StudentMinistryType;
import com.dilaw.mohe.model.StudentStudyStatus;
import com.dilaw.mohe.model.StudentStudyType;
import com.dilaw.mohe.model.StudentUniversityType;
import com.dilaw.mohe.model.StudyCurrencyIn;
import com.dilaw.mohe.model.StudyFinReceivedStatus;
import com.dilaw.mohe.model.StudyFinanceBank;
import com.dilaw.mohe.model.StudyFinanceBankBranch;
import com.dilaw.mohe.model.StudyFinanceDof;
import com.dilaw.mohe.model.StudyReceivedWhomStatus;
import com.dilaw.mohe.repository.BankTransactionRepository;
import com.dilaw.mohe.repository.FinancialAuditRepository;
import com.dilaw.mohe.repository.PaymentSummaryRepository;
import com.dilaw.mohe.repository.ScholarshipTrackingRepository;
import com.dilaw.mohe.repository.StudentAgencyTypeRepository;
import com.dilaw.mohe.repository.StudentCurrentLevelRepository;
import com.dilaw.mohe.repository.StudentDegreeRepository;
import com.dilaw.mohe.repository.StudentGenderRepository;
import com.dilaw.mohe.repository.StudentMajorRepository;
import com.dilaw.mohe.repository.StudentMinistryTypeRepository;
import com.dilaw.mohe.repository.StudentStudyStatusRepository;
import com.dilaw.mohe.repository.StudentStudyTypeRepository;
import com.dilaw.mohe.repository.StudentUniversityTypeRepository;
import com.dilaw.mohe.service.CountryNameService;
import com.dilaw.mohe.service.StudentListMetadataService;
import com.dilaw.mohe.ums.service.CountryScopeService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.data.jpa.repository.Query;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Live QA M13 (25 Sep 2026): on an English screen the report filters (gender, study
 * status, major, payment status, payment type, quarter…) listed Arabic names, and the
 * gender filter offered the inactive "test" row. The report metadata services now build
 * their options with {@link LocalizedLookupOptions}; the reports that filter on the name
 * itself accept either language's name.
 */
class ReportFilterLanguageTest {

    @AfterEach
    void resetLocale() {
        LocaleContextHolder.resetLocaleContext();
    }

    private static StudentGender gender(int id, String ar, String en, Boolean active) {
        StudentGender g = new StudentGender();
        g.setId(id);
        g.setStudentGenderName(ar);
        g.setStudentGenderNameEn(en);
        g.setIsActive(active);
        return g;
    }

    private static final List<StudentGender> GENDERS = List.of(
            gender(1, "ذكر", "Male", true),
            gender(2, "أنثى", "Female", null),
            gender(3, "test", "test111", false));

    private static List<String> names(List<LookupOptionDto> options) {
        return options.stream().map(LookupOptionDto::getName).toList();
    }

    @Nested
    @DisplayName("the options helper")
    class Helper {

        @Test
        @DisplayName("names the rows in the request's language and leaves inactive rows out")
        void languageAndActive() {
            LocaleContextHolder.setLocale(Locale.ENGLISH);
            assertThat(names(LocalizedLookupOptions.of(GENDERS, StudentGender::getId)))
                    .containsExactly("Female", "Male");

            LocaleContextHolder.setLocale(Locale.forLanguageTag("ar"));
            assertThat(LocalizedLookupOptions.of(GENDERS, StudentGender::getId))
                    .extracting(LookupOptionDto::getId).containsExactlyInAnyOrder(1, 2);
            assertThat(names(LocalizedLookupOptions.of(GENDERS, StudentGender::getId)))
                    .containsExactlyInAnyOrder("ذكر", "أنثى");
        }

        @Test
        @DisplayName("falls back to the other language, then to the id")
        void fallback() {
            LocaleContextHolder.setLocale(Locale.ENGLISH);
            List<StudentGender> rows = List.of(gender(7, "ذكر", " ", true), gender(8, null, null, true));
            assertThat(names(LocalizedLookupOptions.of(rows, StudentGender::getId)))
                    .containsExactlyInAnyOrder("ذكر", "8");
        }

        @Test
        @DisplayName("finds the Arabic and English name of every lookup the report filters use")
        void everyFilterTableHasItsTwoNames() {
            List<Class<?>> types = List.of(StudentGender.class, StudentMajor.class, StudentDegree.class,
                    StudentCurrentLevel.class, StudentStudyType.class, StudentStudyStatus.class,
                    StudentUniversityType.class, StudentAgencyType.class, StudentMinistryType.class,
                    StudyFinReceivedStatus.class, StudyReceivedWhomStatus.class, StudyFinanceBank.class,
                    StudyFinanceBankBranch.class, StudyFinanceDof.class, StudyCurrencyIn.class, MoheQuarter.class);
            for (Class<?> type : types) {
                List<Field> fields = LookupNamesRequired.nameFields(type);
                assertThat(fields).as(type.getSimpleName()).hasSize(2);
                assertThat(fields.get(1).getName()).as(type.getSimpleName())
                        .isEqualTo(fields.get(0).getName() + "En");
            }
        }
    }

    @Nested
    @ExtendWith(MockitoExtension.class)
    @MockitoSettings(strictness = Strictness.LENIENT)
    @DisplayName("the Student List report's filters")
    class StudentList {

        @Mock private CountryNameService countryNameService;
        @Mock private CountryScopeService countryScopeService;
        @Mock private StudentGenderRepository genderRepository;
        @Mock private StudentMajorRepository majorRepository;
        @Mock private StudentDegreeRepository degreeRepository;
        @Mock private StudentCurrentLevelRepository currentLevelRepository;
        @Mock private StudentStudyTypeRepository studyTypeRepository;
        @Mock private StudentStudyStatusRepository studyStatusRepository;
        @Mock private StudentUniversityTypeRepository universityTypeRepository;
        @Mock private StudentAgencyTypeRepository agencyTypeRepository;
        @Mock private StudentMinistryTypeRepository ministryTypeRepository;
        @InjectMocks private StudentListMetadataService service;

        @Test
        @DisplayName("are English on an English screen, with no inactive gender")
        void english() {
            when(countryScopeService.getScope()).thenReturn(new CountryScopeService.CountryScope(1L, true, List.of()));
            when(countryNameService.all()).thenReturn(List.of(
                    new CountryNameService.CountryRef(1, "مصر", "Egypt", true),
                    new CountryNameService.CountryRef(1004, "الأردن", "Jordan", true)));
            when(genderRepository.findAll()).thenReturn(GENDERS);

            LocaleContextHolder.setLocale(Locale.ENGLISH);
            StudentListMetadataDto dto = service.getMetadata();

            assertThat(names(dto.getGenders())).containsExactly("Female", "Male");
            assertThat(names(dto.getCountries())).containsExactly("Egypt", "Jordan");
        }
    }

    @Nested
    @DisplayName("reports that filter on the name")
    class NameFilters {

        private List<String> queries(Class<?> repository) throws Exception {
            List<String> out = new ArrayList<>();
            for (Method m : repository.getDeclaredMethods()) {
                Query q = m.getAnnotation(Query.class);
                if (q != null) out.add(q.value());
            }
            return out;
        }

        private void acceptsBoth(Class<?> repository, String arabic, String english) throws Exception {
            List<String> matching = queries(repository).stream()
                    .filter(q -> q.contains(arabic + " = :")).toList();
            assertThat(matching).as(repository.getSimpleName() + " " + arabic).isNotEmpty();
            for (String q : matching) {
                String param = q.substring(q.indexOf(arabic + " = :") + arabic.length() + 4).split("[ )]")[0];
                assertThat(q).as(repository.getSimpleName() + " " + arabic)
                        .contains(english + " = :" + param);
            }
        }

        @Test
        @DisplayName("accept the English name as well as the Arabic one")
        void bothLanguages() throws Exception {
            acceptsBoth(PaymentSummaryRepository.class, "sfrs.STUDY_FIN_RECEIVED_STATUS_NAME", "sfrs.STUDY_FIN_RECEIVED_STATUS_NAME_EN");
            acceptsBoth(PaymentSummaryRepository.class, "srws.STUDY_RECEIVED_WHOM_STATUS_NAME", "srws.STUDY_RECEIVED_WHOM_STATUS_NAME_EN");
            assertThat(PaymentSummaryRepository.FILTERS).contains("mq.QUARTER_NAME_EN = :quarter");
            for (String f : Arrays.asList("scholarshipType", "scholarshipProvider", "scholarshipStatus")) {
                acceptsBoth(ScholarshipTrackingRepository.class, "s." + f, "s." + f + "En");
            }
            for (String f : Arrays.asList("transactionType", "bankName", "bankBranch", "transactionCurrency")) {
                acceptsBoth(BankTransactionRepository.class, "t." + f, "t." + f + "En");
            }
            for (String f : Arrays.asList("auditStatus", "auditCategory", "bankName", "auditorName", "auditCurrency")) {
                acceptsBoth(FinancialAuditRepository.class, "a." + f, "a." + f + "En");
            }
        }
    }
}
