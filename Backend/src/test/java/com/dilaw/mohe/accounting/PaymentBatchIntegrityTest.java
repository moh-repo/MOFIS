package com.dilaw.mohe.accounting;

import com.dilaw.mohe.accounting.dto.PaymentBatchCreateRequest;
import com.dilaw.mohe.accounting.model.AttacheAdvanceBalance;
import com.dilaw.mohe.accounting.model.AttacheAllocation;
import com.dilaw.mohe.accounting.model.FiscalYear;
import com.dilaw.mohe.accounting.model.PaymentBatch;
import com.dilaw.mohe.accounting.model.PaymentBatchLine;
import com.dilaw.mohe.accounting.repository.PaymentBatchLineRepository;
import com.dilaw.mohe.accounting.repository.PaymentBatchRepository;
import com.dilaw.mohe.accounting.service.AdvanceBalanceService;
import com.dilaw.mohe.accounting.service.FiscalPeriodService;
import com.dilaw.mohe.accounting.service.PaymentBatchService;
import com.dilaw.mohe.ums.service.CountryScopeService;
import com.dilaw.mohe.ums.service.CountryScopeService.CountryScope;
import jakarta.persistence.Version;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static com.dilaw.mohe.accounting.PaymentBatchServiceTest.sqlContaining;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Payment batches: FIN-03 (paying twice), FIN-04 (paying a student twice, or from the
 * wrong attaché), FIN-12 (paying in a closed year) and FIN-16 (a cancelled batch that
 * read as approved).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PaymentBatchIntegrityTest {

    private static final int CAIRO = 1;

    @Mock private PaymentBatchRepository batchRepository;
    @Mock private PaymentBatchLineRepository lineRepository;
    @Mock private FiscalPeriodService fiscalPeriodService;
    @Mock private AdvanceBalanceService advanceBalanceService;
    @Mock private CountryScopeService countryScopeService;
    @Mock private JdbcTemplate jdbcTemplate;
    @Mock private com.dilaw.mohe.accounting.service.AttacheCountryValidator attacheCountries;
    @InjectMocks private PaymentBatchService service;

    @BeforeEach
    void policies() {
        org.springframework.test.util.ReflectionTestUtils.setField(service, "accessControl",
                com.dilaw.mohe.security.policy.TestAccessControl.of(new com.dilaw.mohe.security.policy.MakerCheckerPolicy()));
    }

    private static PaymentBatch batch(String status) {
        PaymentBatch b = new PaymentBatch();
        b.setId(7);
        b.setFiscalYearId(2);
        b.setMoheQuarterKey(20253);
        b.setStudyCountryInId(CAIRO);
        b.setBatchNo((short) 1);
        b.setCurrencyId(1);
        b.setStatus(status);
        b.setPreparedBy(13);
        return b;
    }

    private static PaymentBatchLine line(int studentId, String amount) {
        PaymentBatchLine l = new PaymentBatchLine();
        l.setStudentId(studentId);
        l.setEntitledAmount(new BigDecimal(amount));
        l.setDisbursedAmount(new BigDecimal(amount));
        return l;
    }

    private static void assertStatus(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, HttpStatus status) {
        assertThatThrownBy(call).isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(status);
    }

    // ── FIN-03 ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("FIN-03: the batch, the allocation and the advance carry a row version")
    void rowsAreVersioned() throws Exception {
        for (Class<?> type : List.of(PaymentBatch.class, AttacheAllocation.class, AttacheAdvanceBalance.class)) {
            assertThat(type.getDeclaredField("rowVersion").isAnnotationPresent(Version.class))
                    .as(type.getSimpleName()).isTrue();
        }
        assertThat(new PaymentBatch().getRowVersion())
                .as("null until persisted, so Spring Data still persists a new row rather than merging it")
                .isNull();
    }

    @Test
    @DisplayName("FIN-03: the second of two concurrent 'mark paid' calls fails on the version, before the advance moves")
    void concurrentPayDebitsOnce() {
        when(batchRepository.findById(7)).thenReturn(Optional.of(batch(PaymentBatch.APPROVED)));
        when(countryScopeService.canAccessCountry(anyInt())).thenReturn(true);
        when(lineRepository.findByBatchIdOrderByIdAsc(7)).thenReturn(List.of(line(10, "300")));
        // What the flush answers when the other request already moved the batch on.
        doThrow(new ObjectOptimisticLockingFailureException(PaymentBatch.class, 7))
                .when(batchRepository).saveAndFlush(any());

        assertThatThrownBy(() -> service.markPaid(7, 2)).isInstanceOf(ObjectOptimisticLockingFailureException.class);
        verify(advanceBalanceService, never()).recordDisbursement(any(), any(), any(), any());
        verify(advanceBalanceService, never()).openBalance(any(), any(), any());
    }

    // ── FIN-04 ───────────────────────────────────────────────────────────────

    private PaymentBatchCreateRequest cairoBatchFor(int... students) {
        when(countryScopeService.getScope()).thenReturn(new CountryScope(1L, true, List.of()));
        when(countryScopeService.canAccessCountry(CAIRO)).thenReturn(true);
        FiscalYear fy = new FiscalYear();
        fy.setId(2);
        when(fiscalPeriodService.requireYearOfQuarter(20253)).thenReturn(fy);
        List<PaymentBatchCreateRequest.Line> lines = java.util.Arrays.stream(students)
                .mapToObj(s -> new PaymentBatchCreateRequest.Line(s, new BigDecimal("100"), null, null)).toList();
        when(jdbcTemplate.queryForList(PaymentBatchServiceTest.existenceQuery(), eq(Integer.class), any(Object[].class)))
                .thenReturn(java.util.Arrays.stream(students).boxed().toList());
        return new PaymentBatchCreateRequest(20253, CAIRO, 1, 1, null, lines);
    }

    @Test
    @DisplayName("FIN-04: a student of another attaché cannot be put on this attaché's batch (400)")
    void foreignStudentRefused() {
        PaymentBatchCreateRequest req = cairoBatchFor(10, 20);
        when(jdbcTemplate.queryForList(sqlContaining("STUDY_COUNTRY_IN, -1) <> ?"), eq(Integer.class), any(Object[].class)))
                .thenReturn(List.of(20));

        assertThatThrownBy(() -> service.create(req))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("20")
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        verify(batchRepository, never()).save(any());
        // The batch's own attaché is what the students are checked against.
        verify(jdbcTemplate).queryForList(sqlContaining("STUDY_COUNTRY_IN, -1) <> ?"), eq(Integer.class),
                eq(new Object[] {10, 20, CAIRO}));
    }

    @Test
    @DisplayName("FIN-04: a student already paid in full this quarter by another batch is refused (409)")
    void alreadyPaidRefused() {
        PaymentBatchCreateRequest req = cairoBatchFor(10, 11);
        when(jdbcTemplate.queryForList(sqlContaining("STUDY_COUNTRY_IN, -1) <> ?"), eq(Integer.class), any(Object[].class)))
                .thenReturn(List.of());
        when(jdbcTemplate.queryForList(sqlContaining("PAYMENT_BATCH_LINE"), eq(Integer.class), any(Object[].class)))
                .thenReturn(List.of(11));

        assertThatThrownBy(() -> service.create(req))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("11")
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
        verify(batchRepository, never()).save(any());
        verify(jdbcTemplate).queryForList(sqlContaining("b.STATUS <> 'CANCELLED'"), eq(Integer.class),
                eq(new Object[] {20253, 10, 11}));
    }

    // ── FIN-12 ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("FIN-12: a batch of a closed fiscal year is neither prepared, approved nor paid")
    void closedYearRefused() {
        doThrow(new ResponseStatusException(HttpStatus.CONFLICT, "Fiscal year 2025 is closed"))
                .when(fiscalPeriodService).assertYearOpen(2);
        PaymentBatchCreateRequest req = cairoBatchFor(10);
        assertStatus(() -> service.create(req), HttpStatus.CONFLICT);

        when(batchRepository.findById(7)).thenReturn(Optional.of(batch(PaymentBatch.OPEN)));
        assertStatus(() -> service.approve(7, 2), HttpStatus.CONFLICT);

        when(batchRepository.findById(7)).thenReturn(Optional.of(batch(PaymentBatch.APPROVED)));
        assertStatus(() -> service.markPaid(7, 2), HttpStatus.CONFLICT);

        verify(batchRepository, never()).save(any());
        verify(batchRepository, never()).saveAndFlush(any());
        verify(advanceBalanceService, never()).recordDisbursement(any(), any(), any(), any());
    }

    @Test
    @DisplayName("FIN-12: paying is dated today, so today's period must be open")
    void payingChecksTodaysPeriod() {
        when(batchRepository.findById(7)).thenReturn(Optional.of(batch(PaymentBatch.APPROVED)));
        when(countryScopeService.canAccessCountry(anyInt())).thenReturn(true);
        doThrow(new ResponseStatusException(HttpStatus.CONFLICT, "Period 9 is closed"))
                .when(fiscalPeriodService).assertPeriodOpen(java.time.LocalDate.now());

        assertStatus(() -> service.markPaid(7, 2), HttpStatus.CONFLICT);
        verify(advanceBalanceService, never()).recordDisbursement(any(), any(), any(), any());
    }

    // ── FIN-16 ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("FIN-16: cancelling records who cancelled, and leaves the approval columns empty")
    void cancelIsNotAnApproval() {
        when(batchRepository.findById(7)).thenReturn(Optional.of(batch(PaymentBatch.OPEN)));
        when(countryScopeService.canAccessCountry(anyInt())).thenReturn(true);
        when(batchRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PaymentBatch cancelled = service.cancel(7, 2);

        assertThat(cancelled.getStatus()).isEqualTo(PaymentBatch.CANCELLED);
        assertThat(cancelled.getCancelledBy()).isEqualTo(2);
        assertThat(cancelled.getCancelledAt()).isNotNull();
        assertThat(cancelled.getApprovedBy()).isNull();
        assertThat(cancelled.getApprovedAt()).isNull();
    }
}
