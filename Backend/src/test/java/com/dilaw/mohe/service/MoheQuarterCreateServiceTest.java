package com.dilaw.mohe.service;

import com.dilaw.mohe.model.MoheQuarter;
import com.dilaw.mohe.model.StudentCurrentLevel;
import com.dilaw.mohe.model.StudentDataArch;
import com.dilaw.mohe.model.StudentDatum;
import com.dilaw.mohe.model.StudentFinance;
import com.dilaw.mohe.model.StudentFinanceArch;
import com.dilaw.mohe.model.StudentStudyStatus;
import com.dilaw.mohe.repository.LuQuarterNumberRepository;
import com.dilaw.mohe.repository.MoheQuarterRepository;
import com.dilaw.mohe.repository.StudentCurrentLevelRepository;
import com.dilaw.mohe.repository.StudentDataRepository;
import com.dilaw.mohe.repository.StudentFinanceRepository;
import com.dilaw.mohe.repository.StudentStudyStatusRepository;
import com.dilaw.mohe.ums.service.CountryScopeService;
import com.dilaw.mohe.ums.service.CountryScopeService.CountryScope;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Quarter create: who the new quarter belongs to, and how much it costs to build.
 *
 * <p>A country user who sent a {@code countryId} outside their scope had it silently
 * replaced with their first country — the quarter was created for an attaché the
 * caller never named and the response said 200. It is now refused with 403; only a
 * missing id is defaulted, and only when the caller holds exactly one country.
 * Ministry quarters stay global (NULL), the meaning {@code MoheQuarterRepository}
 * gives that column.
 *
 * <p>The snapshot loop also fetched and saved row by row — a finance {@code findById}
 * and two saves per student — so the finance rows are now loaded once per page and
 * written back once per page.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MoheQuarterCreateServiceTest {

    private static final int JORDAN = 1004;
    private static final int SAUDI = 1005;

    @Mock private MoheQuarterRepository quarterRepository;
    @Mock private StudentDataRepository studentDataRepository;
    @Mock private StudentFinanceRepository studentFinanceRepository;
    @Mock private StudentDataArchiveService studentDataArchiveService;
    @Mock private StudentFinanceArchiveService studentFinanceArchiveService;
    @Mock private StudentStudyStatusRepository studyStatusRepository;
    @Mock private StudentCurrentLevelRepository currentLevelRepository;
    @Mock private LuQuarterNumberRepository quarterNumberRepository;
    @Mock private CountryScopeService countryScopeService;
    @Mock private StudentProfileService studentProfileService;
    @Mock private com.dilaw.mohe.repository.StudyFinReceivedStatusRepository finReceivedStatusRepository;
    @Spy private ObjectMapper objectMapper = new ObjectMapper();
    @Mock private jakarta.persistence.EntityManager entityManager;

    @InjectMocks
    private MoheQuarterCreateService service;

    private MoheQuarter request;
    private StudentDatum s1;
    private StudentDatum s2;
    private StudentFinance finance;

    @BeforeEach
    void setUp() {
        request = new MoheQuarter();
        request.setQuarterYear(2026);
        request.setQuarterNumber(3);

        when(quarterRepository.findMaxYearQuarter()).thenReturn(20262);
        when(quarterRepository.save(any(MoheQuarter.class))).thenAnswer(inv -> {
            MoheQuarter q = inv.getArgument(0);
            q.setId(13);
            return q;
        });

        // Two students on one page, one of them with a finance row.
        s1 = new StudentDatum();
        s1.setStudentId(1);
        s1.setCurrentQuarter("20262");
        s1.setStudentStudyStatusId(2);      // NEW
        s1.setStudentCurrentLevelId(2);     // second year
        s1.setStudentStudyNoOfYears(7);
        s2 = new StudentDatum();
        s2.setStudentId(2);
        s2.setCurrentQuarter("20262");
        when(studentDataRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(s1, s2), PageRequest.of(0, 200), 2));

        finance = new StudentFinance();
        finance.setId(1);
        finance.setMoheQuarterId(12);
        finance.setStudyReceivedWhomId(7);
        finance.setWafarAmount(1500);
        finance.setStudyCheckId("CHK-1");
        when(studentFinanceRepository.findAllById(anyIterable())).thenReturn(List.of(finance));
        // Quarter create archives a page at a time (review Q4); each call reports how
        // many rows it wrote.
        when(studentFinanceArchiveService.archiveAll(anyList()))
                .thenAnswer(inv -> ((List<?>) inv.getArgument(0)).size());
        when(studentDataArchiveService.archiveAll(anyList(), any(), anyMap()))
                .thenAnswer(inv -> ((List<?>) inv.getArgument(0)).size());

        when(studyStatusRepository.findAll()).thenReturn(List.of(status(1, "ACTIVE"), status(2, "NEW")));
        when(currentLevelRepository.findAll()).thenReturn(List.of(level(1, 1), level(2, 2), level(3, 3)));
        when(quarterNumberRepository.findByIsDeletedFalseOrderBySortOrderAsc()).thenReturn(List.of());
    }

    private static StudentStudyStatus status(int id, String code) {
        StudentStudyStatus s = new StudentStudyStatus();
        s.setId(id);
        s.setCode(code);
        return s;
    }

    private static StudentCurrentLevel level(int id, int order) {
        StudentCurrentLevel l = new StudentCurrentLevel();
        l.setId(id);
        l.setStudentCurrentLevelOrderNo(order);
        l.setStudentCurrentLevelStatus(0);
        return l;
    }

    private void scope(boolean ministry, Integer... countries) {
        when(countryScopeService.getScope())
                .thenReturn(new CountryScope(13L, ministry, false, List.of(countries)));
    }

    // ── Who may create (FIN-07) ──────────────────────────────────────────────

    /**
     * MOHE_QUARTER is one calendar for every attaché. A country user's quarter used to
     * roll only their own students, leaving every other country on the old quarter with
     * no way forward; creating a quarter is now the ministry's alone, whatever country
     * the country user names or does not name.
     */
    private void assertCountryUserRefused() {
        assertThatThrownBy(() -> service.createWithSnapshot(request))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        verify(quarterRepository, never()).save(any());
        verify(studentDataRepository, never()).saveAll(anyIterable());
    }

    @Test
    @DisplayName("FIN-07: a country user naming a country outside their scope is refused")
    void outOfScopeCountryIsRefused() {
        scope(false, JORDAN);
        request.setCountryId(SAUDI);

        assertCountryUserRefused();
    }

    @Test
    @DisplayName("a ministry user's quarter is global: COUNTRY_ID stays NULL")
    void ministryQuarterIsGlobal() {
        scope(true, 1, JORDAN, SAUDI);

        Map<String, Object> summary = service.createWithSnapshot(request);

        ArgumentCaptor<MoheQuarter> saved = ArgumentCaptor.forClass(MoheQuarter.class);
        verify(quarterRepository).save(saved.capture());
        assertThat(saved.getValue().getCountryId()).isNull();
        assertThat(summary.get("countryId")).isNull();
    }

    @Test
    @DisplayName("FIN-07: a single-country user who names no country is refused, not stamped with theirs")
    void singleCountryUserIsStamped() {
        scope(false, JORDAN);

        assertCountryUserRefused();
    }

    @Test
    @DisplayName("FIN-07: a country user naming one of their own countries is refused too")
    void inScopeCountryIsKept() {
        scope(false, JORDAN, SAUDI);
        request.setCountryId(SAUDI);

        assertCountryUserRefused();
    }

    @Test
    @DisplayName("FIN-07: a multi-country user who names no country is refused")
    void multiCountryUserMustChoose() {
        scope(false, JORDAN, SAUDI);

        assertCountryUserRefused();
    }

    @Test
    @DisplayName("a user with no country scope cannot create a quarter")
    void noScopeIsRefused() {
        when(countryScopeService.getScope()).thenReturn(CountryScope.noAccess());

        assertThatThrownBy(() -> service.createWithSnapshot(request))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("FIN-07: a ministry officer narrowed to one country by the picker still rolls every student")
    @SuppressWarnings("unchecked")
    void narrowedMinistryRollsEveryone() {
        // The picker turns ministryUser off and leaves one country; the tier is what decides.
        when(countryScopeService.getScope()).thenReturn(new CountryScope(13L, false, true, List.of(JORDAN),
                com.dilaw.mohe.ums.model.AdminTier.MINISTRY_ADMIN));

        service.createWithSnapshot(request);

        ArgumentCaptor<Specification<StudentDatum>> spec = ArgumentCaptor.forClass(Specification.class);
        verify(studentDataRepository).findAll(spec.capture(), any(Pageable.class));
        jakarta.persistence.criteria.Root<StudentDatum> root = org.mockito.Mockito.mock(jakarta.persistence.criteria.Root.class);
        jakarta.persistence.criteria.CriteriaBuilder cb = org.mockito.Mockito.mock(jakarta.persistence.criteria.CriteriaBuilder.class);
        spec.getValue().toPredicate(root, org.mockito.Mockito.mock(jakarta.persistence.criteria.CriteriaQuery.class), cb);
        verify(root, never()).get("studyCountryIn");
    }

    // ── Snapshot loop ────────────────────────────────────────────────────────

    @Test
    @DisplayName("finance rows are fetched once per page and written back once per page")
    @SuppressWarnings("unchecked")
    void financeRowsAreBatchedPerPage() {
        scope(true, 1, JORDAN);

        Map<String, Object> summary = service.createWithSnapshot(request);

        ArgumentCaptor<Iterable<Integer>> ids = ArgumentCaptor.forClass(Iterable.class);
        verify(studentFinanceRepository, times(1)).findAllById(ids.capture());
        assertThat(ids.getValue()).containsExactly(1, 2);
        verify(studentFinanceRepository, never()).findById(anyInt());

        verify(studentFinanceRepository, never()).save(any(StudentFinance.class));
        verify(studentFinanceRepository, times(1)).saveAll(anyIterable());
        verify(studentDataRepository, never()).save(any(StudentDatum.class));
        verify(studentDataRepository, times(1)).saveAll(anyIterable());
        // One archive call per page for each table, each an upsert on its key.
        verify(studentDataArchiveService, times(1)).archiveAll(anyList(), any(), anyMap());
        verify(studentFinanceArchiveService, times(1)).archiveAll(anyList());
        verify(studentDataArchiveService, never()).archive(any(), any(), any());
        // The page is written out and let go before the next one is read.
        verify(entityManager, times(1)).flush();
        verify(entityManager, times(1)).clear();

        assertThat(summary)
                .containsEntry("studentSnapshotsArchived", 2)
                .containsEntry("financeSnapshotsArchived", 1)
                .containsEntry("quarterId", 13);
    }

    // ── The period key ───────────────────────────────────────────────────────

    @Test
    @DisplayName("QUARTER is derived from the year and the number, not taken from the body")
    void quarterKeyIsDerived() {
        scope(true, 1);
        // The screen has no control for it and never sends one; a body that does send
        // one must not be able to contradict the period.
        request.setQuarter(999);

        Map<String, Object> summary = service.createWithSnapshot(request);

        ArgumentCaptor<MoheQuarter> saved = ArgumentCaptor.forClass(MoheQuarter.class);
        verify(quarterRepository).save(saved.capture());
        assertThat(saved.getValue().getQuarter()).isEqualTo(20263);
        assertThat(summary).containsEntry("yyyyQ", 20263);
    }

    @Test
    @DisplayName("a quarter with no year or no number is refused, not offered to a NOT NULL column")
    void periodIsRequired() {
        scope(true, 1);
        request.setQuarterYear(null);

        assertThatThrownBy(() -> service.createWithSnapshot(request))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        verify(quarterRepository, never()).save(any());
    }

    // ── The roll ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("every student is moved into the new quarter, whether or not they have a finance row")
    void studentsAreRolledOver() {
        scope(true, 1);

        service.createWithSnapshot(request);

        assertThat(s1.getCurrentQuarter()).isEqualTo("20263");
        assertThat(s2.getCurrentQuarter()).isEqualTo("20263");
        assertThat(s1.getStudentStudyStatusId()).as("NEW becomes ACTIVE").isEqualTo(1);
        assertThat(s1.getStudentCurrentLevelId()).as("Q3 turns the academic year").isEqualTo(3);
    }

    @Test
    @DisplayName("the archive is written from the row before the roll touches it")
    void theArchiveSeesThePeriodBeingLeft() {
        scope(true, 1);
        // Read at call time, not captured: the archive is handed the live entity and
        // the roll mutates it a moment later, so a captured reference would show the
        // new period and the test would pass however wrong the order was.
        List<String> periodAtArchiveTime = new java.util.ArrayList<>();
        when(studentDataArchiveService.archiveAll(anyList(), any(), anyMap()))
                .thenAnswer(inv -> {
                    List<StudentDatum> page = inv.getArgument(0);
                    page.forEach(sd -> periodAtArchiveTime.add(sd.getCurrentQuarter()));
                    return page.size();
                });

        service.createWithSnapshot(request);

        assertThat(periodAtArchiveTime).containsExactly("20262", "20262");
    }

    @Test
    @DisplayName("last quarter's payment detail does not follow the finance row forward")
    void financeRowIsCleared() {
        scope(true, 1);

        service.createWithSnapshot(request);

        assertThat(finance.getStudyReceivedWhomId()).isZero();
        assertThat(finance.getWafarAmount()).isNull();
        assertThat(finance.getStudyCheckId()).isNull();
    }

    @Test
    @DisplayName("the year of study turns on the third quarter only")
    void levelsHoldOutsideTheThirdQuarter() {
        scope(true, 1);
        request.setQuarterNumber(4);

        Map<String, Object> summary = service.createWithSnapshot(request);

        assertThat(s1.getStudentCurrentLevelId()).isEqualTo(2);
        assertThat(summary).containsEntry("levelsAdvanced", false);
    }

    // ── The Add dialog's defaults ────────────────────────────────────────────

    @Test
    @DisplayName("the next quarter follows the latest one on record, across the year end")
    void nextQuarterDefaultsFollowTheLatest() {
        when(quarterRepository.findMaxYearQuarter()).thenReturn(20253);
        assertThat(service.nextQuarterDefaults())
                .containsEntry("quarter", 20254)
                .containsEntry("quarterYear", 2025)
                .containsEntry("quarterNumber", 4);

        when(quarterRepository.findMaxYearQuarter()).thenReturn(20254);
        assertThat(service.nextQuarterDefaults())
                .containsEntry("quarter", 20261)
                .containsEntry("quarterYear", 2026)
                .containsEntry("quarterNumber", 1);
    }

    @Test
    @DisplayName("the default name is the lookup's own name for the quarter, followed by the year")
    void nextQuarterIsNamedAsTheExistingRowsAre() {
        when(quarterRepository.findMaxYearQuarter()).thenReturn(20253);
        when(quarterNumberRepository.findByIsDeletedFalseOrderBySortOrderAsc())
                .thenReturn(List.of(quarterNumber("3", "الربع الثالث", "Quarter 3"),
                        quarterNumber("4", "الربع الرابع", "Quarter 4")));

        assertThat(service.nextQuarterDefaults())
                .containsEntry("quarterName", "الربع الرابع 2025")
                .containsEntry("quarterNameEn", "Quarter 4 2025");
    }

    private static com.dilaw.mohe.model.LuQuarterNumber quarterNumber(String code, String ar, String en) {
        com.dilaw.mohe.model.LuQuarterNumber q = new com.dilaw.mohe.model.LuQuarterNumber();
        q.setCode(code);
        q.setNameAr(ar);
        q.setNameEn(en);
        return q;
    }

    @Test
    @DisplayName("the finance row is archived under its old quarter and advanced to the new one")
    void financeRowIsAdvanced() {
        scope(true, 1);
        // Read at call time: the roll advances the same object right after.
        List<Integer> quarterAtArchiveTime = new java.util.ArrayList<>();
        when(studentFinanceArchiveService.archiveAll(anyList())).thenAnswer(inv -> {
            List<StudentFinance> page = inv.getArgument(0);
            page.forEach(f -> quarterAtArchiveTime.add(f.getMoheQuarterId()));
            return page.size();
        });

        service.createWithSnapshot(request);

        assertThat(quarterAtArchiveTime).containsExactly(12);
        StudentFinance fin = finance;
        // The period, not the quarter's row id (13): FK_STUDENT_FINANCE_QUARTER
        // references MOHE_QUARTER.QUARTER.
        assertThat(fin.getMoheQuarterId()).isEqualTo(20263);
        assertThat(fin.getStudentFinRecievedYear()).isEqualTo(2026);
        assertThat(fin.getStudentFinRecievedMonth()).isEqualTo(3);
        assertThat(fin.getStudentFinRecievedYearMonth()).isEqualTo(20263);
    }

    @Test
    @DisplayName("progress reports the students in scope and how many are done")
    void progressIsReported() {
        when(studentDataRepository.count(any(Specification.class))).thenReturn(2L);
        CountryScope ministry = new CountryScope(13L, true, false, List.of(1));
        List<String> events = new java.util.ArrayList<>();

        service.createWithSnapshot(request, ministry, new MoheQuarterCreateService.Progress() {
            @Override public void started(long total) { events.add("started " + total); }
            @Override public void advanced(int done) { events.add("done " + done); }
        });

        assertThat(events).containsExactly("started 2", "done 2");
    }

    @Test
    @DisplayName("validate refuses a period that is not after the latest, and writes nothing")
    void validateRefusesAnEarlierPeriod() {
        request.setQuarterNumber(2); // 20262 is the latest on record
        CountryScope ministry = new CountryScope(13L, true, false, List.of(1));

        assertThatThrownBy(() -> service.validate(request, ministry))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        verify(quarterRepository, never()).save(any());
    }
}
