package com.dilaw.mohe.service;

import com.dilaw.mohe.model.MoheQuarter;
import com.dilaw.mohe.model.StudentDataArch;
import com.dilaw.mohe.model.StudentDatum;
import com.dilaw.mohe.model.StudentFinance;
import com.dilaw.mohe.model.StudentFinanceArch;
import com.dilaw.mohe.model.StudentFinancePayment;
import com.dilaw.mohe.repository.MoheQuarterRepository;
import com.dilaw.mohe.repository.StudentDataArchRepository;
import com.dilaw.mohe.repository.StudentDataRepository;
import com.dilaw.mohe.repository.StudentFinancePaymentRepository;
import com.dilaw.mohe.repository.StudentFinanceRepository;
import com.dilaw.mohe.ums.service.CountryScopeService;
import com.dilaw.mohe.ums.service.CountryScopeService.CountryScope;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
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
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Closing MOHE quarter 3 (2024 Q2) failed with
 * {@code Cannot insert duplicate key row ... UX_STUDENT_FINANCE_ARCH_STUDENT_QUARTER (1, 2024, 2)}:
 * the archive already held every student's 2024 Q2 row and the close inserted a second
 * one. The close must go through the upsert and write the ledger row only once.
 *
 * <p>The close also used to page {@code STUDENT_DATA} with no country scope and add up
 * the whole live finance table in memory for the budget check, so a country user
 * closing their quarter archived every attache's students and was measured against
 * every attache's money. Both now follow the scope that quarter create applies.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MoheQuarterCloseServiceTest {

    private static final int JORDAN = 1004;

    /**
     * The quarter's period key. Row id 3 and period 20242 are deliberately different
     * numbers here: the finance archive is keyed by the period, because
     * {@code STUDENT_FINANCE.MOHE_QUARTER_ID} is a foreign key to
     * {@code MOHE_QUARTER.QUARTER} rather than to its id column.
     */
    private static final int PERIOD = 20242;

    @Mock private MoheQuarterRepository moheQuarterRepository;
    @Mock private StudentDataRepository studentDataRepository;
    @Mock private StudentDataArchiveService studentDataArchiveService;
    @Mock private StudentFinanceRepository studentFinanceRepository;
    @Mock private StudentFinancePaymentRepository studentFinancePaymentRepository;
    @Mock private StudentFinanceArchiveService studentFinanceArchiveService;
    @Mock private StudentProfileService studentProfileService;
    @Mock private CountryScopeService countryScopeService;
    @Mock private StudyFinReceivedStatusService receivedStatuses;
    @Spy private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private MoheQuarterCloseService service;

    private MoheQuarter quarter;
    private StudentFinance finance;

    @BeforeEach
    void setUp() {
        quarter = new MoheQuarter();
        quarter.setId(3);
        quarter.setQuarter(PERIOD);
        quarter.setQuarterYear(2024);
        quarter.setQuarterNumber(2);
        quarter.setQuarterClosed(false);
        when(moheQuarterRepository.findById(3)).thenReturn(Optional.of(quarter));
        when(moheQuarterRepository.save(any(MoheQuarter.class))).thenAnswer(inv -> inv.getArgument(0));

        // Ministry-wide by default; the scope tests override this.
        when(countryScopeService.getScope()).thenReturn(new CountryScope(1L, true, true, List.of(1, JORDAN)));

        StudentDatum student = new StudentDatum();
        student.setStudentId(1);
        student.setCurrentQuarter("20242");
        Pageable first = PageRequest.of(0, 200);
        when(studentDataRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(student), first, 1));
        when(studentDataArchiveService.archive(any(StudentDatum.class), any(), any()))
                .thenReturn(new StudentDataArchiveService.Result(new StudentDataArch(), false));

        finance = new StudentFinance();
        finance.setId(1);
        finance.setStudentFinName("Student One");
        finance.setStudentFinRecievedYear(2024);
        finance.setStudentFinRecievedMonth(2);
        finance.setStudentFinRecievedYearMonth(20242);
        finance.setStudentFinRecievedAmount(1500);
        when(studentFinanceRepository.findById(1)).thenReturn(Optional.of(finance));
        when(studentFinanceArchiveService.archive(any(StudentFinance.class), any()))
                .thenReturn(new StudentFinanceArchiveService.Result(new StudentFinanceArch(), false));
        when(studentFinancePaymentRepository.save(any(StudentFinancePayment.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    @DisplayName("archive row already present: the close refreshes it and still closes the quarter")
    void closeRefreshesExistingArchiveRow() {
        when(studentFinanceArchiveService.archive(finance, PERIOD))
                .thenReturn(new StudentFinanceArchiveService.Result(new StudentFinanceArch(), true));
        when(studentFinancePaymentRepository.existsByStudentIdAndMoheQuarterId(1, 3)).thenReturn(false);

        Map<String, Object> summary = service.closeQuarter(3, true);

        // The period, not the row id: quarter create fills this column the same way,
        // and the two flows must not put two kinds of number in it.
        verify(studentFinanceArchiveService).archive(finance, PERIOD);
        verify(studentFinancePaymentRepository).save(any(StudentFinancePayment.class));
        assertThat(summary)
                .containsEntry("financeSnapshotsArchived", 1)
                .containsEntry("financeSnapshotsInserted", 0)
                .containsEntry("financeSnapshotsRefreshed", 1)
                .containsEntry("paymentLedgerRowsArchived", 1)
                .containsEntry("paymentLedgerRowsSkipped", 0)
                .containsEntry("quarterClosed", true);
        assertThat(quarter.getQuarterClosed()).isTrue();
    }

    @Test
    @DisplayName("ledger row already written for this student and quarter: not written again")
    void closeDoesNotDuplicateLedgerRow() {
        when(studentFinanceArchiveService.archive(finance, PERIOD))
                .thenReturn(new StudentFinanceArchiveService.Result(new StudentFinanceArch(), false));
        when(studentFinancePaymentRepository.existsByStudentIdAndMoheQuarterId(eq(1), eq(3))).thenReturn(true);

        Map<String, Object> summary = service.closeQuarter(3, true);

        verify(studentFinancePaymentRepository, never()).save(any(StudentFinancePayment.class));
        assertThat(summary)
                .containsEntry("financeSnapshotsInserted", 1)
                .containsEntry("financeSnapshotsRefreshed", 0)
                .containsEntry("paymentLedgerRowsArchived", 0)
                .containsEntry("paymentLedgerRowsSkipped", 1);
    }

    // ── Country scope ───────────────────────────────────────────────────────

    /**
     * Evaluates the captured specification against mocked criteria objects and returns
     * the {@code studyCountryIn} path, so a test can see which countries it restricted to.
     */
    @SuppressWarnings("unchecked")
    private static Path<Object> countryPathOf(Specification<StudentDatum> spec) {
        Root<StudentDatum> root = mock(Root.class);
        Path<Object> countryPath = mock(Path.class);
        CriteriaBuilder cb = mock(CriteriaBuilder.class);
        when(root.get("studyCountryIn")).thenReturn(countryPath);
        when(countryPath.in(anyCollection())).thenReturn(mock(Predicate.class));
        when(cb.conjunction()).thenReturn(mock(Predicate.class));
        spec.toPredicate(root, mock(CriteriaQuery.class), cb);
        return countryPath;
    }

    @Test
    @DisplayName("a country user closes only that country's students, paged in student-id order")
    @SuppressWarnings("unchecked")
    void countryScopedCloseArchivesOnlyInScopeStudents() {
        when(countryScopeService.getScope()).thenReturn(new CountryScope(13L, false, true, List.of(JORDAN)));

        service.closeQuarter(3, true);

        ArgumentCaptor<Specification<StudentDatum>> spec = ArgumentCaptor.forClass(Specification.class);
        ArgumentCaptor<Pageable> page = ArgumentCaptor.forClass(Pageable.class);
        verify(studentDataRepository).findAll(spec.capture(), page.capture());
        verify(studentDataRepository, never()).findAll(any(Pageable.class));

        ArgumentCaptor<Collection<Object>> allowed = ArgumentCaptor.forClass(Collection.class);
        verify(countryPathOf(spec.getValue())).in(allowed.capture());
        assertThat(allowed.getValue()).as("restricted to the caller's study countries").containsExactly(JORDAN);
        assertThat(page.getValue().getSort()).isEqualTo(Sort.by("studentId"));
    }

    @Test
    @DisplayName("a ministry user closes for every student: no country predicate")
    @SuppressWarnings("unchecked")
    void ministryCloseIsUnscoped() {
        service.closeQuarter(3, true);

        ArgumentCaptor<Specification<StudentDatum>> spec = ArgumentCaptor.forClass(Specification.class);
        verify(studentDataRepository).findAll(spec.capture(), any(Pageable.class));
        verify(countryPathOf(spec.getValue()), never()).in(anyCollection());
    }

    @Test
    @DisplayName("a user with no country scope cannot close: nothing would be archived")
    void noScopeIsRefused() {
        when(countryScopeService.getScope()).thenReturn(CountryScope.noAccess());

        assertThatThrownBy(() -> service.closeQuarter(3, true))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        verify(studentDataArchiveService, never()).archive(any(), any(), any());
        assertThat(quarter.getQuarterClosed()).isFalse();
    }

    @Test
    @DisplayName("the student snapshot goes through the upsert, so a close after a create does not duplicate it")
    void studentSnapshotIsUpserted() {
        // Quarter create archives the period being left; a close of the same quarter
        // used to insert a second copy of all 941 students, because it saved
        // unconditionally into a table with no unique index.
        service.closeQuarter(3, true);

        verify(studentDataArchiveService).archive(any(StudentDatum.class), eq(3), any());
    }

    // ── Budget check ────────────────────────────────────────────────────────

    @Test
    @DisplayName("budget check sums the scoped students in the database, not the whole table in memory")
    void budgetCheckUsesScopedRepositorySum() {
        quarter.setBudgetAmount(1000L);
        when(countryScopeService.getScope()).thenReturn(new CountryScope(13L, false, true, List.of(JORDAN)));
        when(receivedStatuses.paidStatusIds()).thenReturn(StudyFinReceivedStatusService.PAID_IDS_FALLBACK);
        when(studentFinanceRepository.findUnpaidForQuarter(eq(3), anyCollection())).thenReturn(List.of());
        when(studentFinanceRepository.sumReceivedAmountForStudyCountries(List.of(JORDAN))).thenReturn(1500L);

        assertThatThrownBy(() -> service.closeQuarter(3, false))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Total paid amount (1500) exceeds quarter budget (1000)");

        verify(studentFinanceRepository).sumReceivedAmountForStudyCountries(List.of(JORDAN));
        verify(studentFinanceRepository, never()).sumReceivedAmount();
        verify(studentFinanceRepository, never()).findAll();
    }

    @Test
    @DisplayName("ministry budget check sums the whole table in the database and records the paid amount")
    void ministryBudgetCheckSumsWholeTable() {
        quarter.setBudgetAmount(5000L);
        when(receivedStatuses.paidStatusIds()).thenReturn(StudyFinReceivedStatusService.PAID_IDS_FALLBACK);
        when(studentFinanceRepository.findUnpaidForQuarter(eq(3), anyCollection())).thenReturn(List.of());
        when(studentFinanceRepository.sumReceivedAmount()).thenReturn(1500L);

        service.closeQuarter(3, false);

        // The unpaid check asks for the paid statuses by CODE, not for "status 0" (live QA H2).
        verify(studentFinanceRepository).findUnpaidForQuarter(3, StudyFinReceivedStatusService.PAID_IDS_FALLBACK);
        verify(studentFinanceRepository).sumReceivedAmount();
        verify(studentFinanceRepository, never()).sumReceivedAmountForStudyCountries(anyCollection());
        verify(studentFinanceRepository, never()).findAll();
        assertThat(quarter.getPaidAmount()).isEqualTo(1500L);
        assertThat(quarter.getQuarterClosed()).isTrue();
    }
}
