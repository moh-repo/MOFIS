package com.dilaw.mohe.accounting;

import com.dilaw.mohe.accounting.dto.PaymentBatchCreateRequest;
import com.dilaw.mohe.accounting.dto.PaymentBatchDetail;
import com.dilaw.mohe.accounting.dto.PaymentBatchSummary;
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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The payment batch (الدفعة): who may see it, who may move it, and in what order.
 *
 * <p>Three rules, each of which the screen hides behind a button and which the
 * service therefore has to hold on its own: an attaché reads only its own
 * batches; the preparer cannot approve; and the status flow is
 * OPEN → APPROVED → DISBURSED, with paying being the only step that touches the
 * advance.
 */
@ExtendWith(MockitoExtension.class)
class PaymentBatchServiceTest {

    @Mock private PaymentBatchRepository batchRepository;
    @Mock private PaymentBatchLineRepository lineRepository;
    @Mock private FiscalPeriodService fiscalPeriodService;
    @Mock private AdvanceBalanceService advanceBalanceService;
    @Mock private CountryScopeService countryScopeService;
    @Mock private JdbcTemplate jdbcTemplate;
    @Mock private com.dilaw.mohe.accounting.service.AttacheCountryValidator attacheCountries;
    @InjectMocks private PaymentBatchService service;

    /** R-16: the maker-checker rule lives in {@code MakerCheckerPolicy}; wire the real one in. */
    @org.junit.jupiter.api.BeforeEach
    void policies() {
        org.springframework.test.util.ReflectionTestUtils.setField(service, "accessControl",
                com.dilaw.mohe.security.policy.TestAccessControl.of(new com.dilaw.mohe.security.policy.MakerCheckerPolicy()));
    }

    private static PaymentBatch batch(int id, int countryId, String status, Integer preparedBy) {
        PaymentBatch b = new PaymentBatch();
        b.setId(id);
        b.setFiscalYearId(2);
        b.setMoheQuarterKey(20253);
        b.setStudyCountryInId(countryId);
        b.setBatchNo((short) 1);
        b.setCurrencyId(1);
        b.setStatus(status);
        b.setPreparedBy(preparedBy);
        return b;
    }

    private static PaymentBatchLine line(int studentId, String entitled, String disbursed) {
        PaymentBatchLine l = new PaymentBatchLine();
        l.setStudentId(studentId);
        l.setEntitledAmount(new BigDecimal(entitled));
        l.setDisbursedAmount(new BigDecimal(disbursed));
        return l;
    }

    // ── scope ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("an officer lists only their own attaché's batches for the quarter")
    void listIsCountryScoped() {
        when(countryScopeService.getScope())
                .thenReturn(new CountryScope(13L, false, List.of(1004)));
        when(batchRepository.findByMoheQuarterKeyOrderByStudyCountryInIdAscBatchNoAsc(20253))
                .thenReturn(List.of(batch(1, 1, PaymentBatch.OPEN, null), batch(2, 1004, PaymentBatch.OPEN, null)));
        when(lineRepository.totalsByBatch(anyList()))
                .thenReturn(List.<Object[]>of(new Object[] { 2, 3L, new BigDecimal("900"), new BigDecimal("850") }));

        List<PaymentBatchSummary> seen = service.forQuarter(20253, null);

        assertThat(seen).extracting(PaymentBatchSummary::id)
                .as("how many batches Cairo raised is Cairo's business")
                .containsExactly(2);
        assertThat(seen.get(0).lineCount()).isEqualTo(3);
        assertThat(seen.get(0).disbursedTotal()).isEqualByComparingTo("850");
    }

    @Test
    @DisplayName("another attaché's batch is not found — not forbidden")
    void requireHidesOtherCountries() {
        when(batchRepository.findById(7)).thenReturn(Optional.of(batch(7, 1, PaymentBatch.OPEN, null)));
        when(countryScopeService.canAccessCountry(1)).thenReturn(false);

        assertThatThrownBy(() -> service.require(7))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("a batch cannot be prepared for an attaché outside the caller's scope")
    void createRefusesForeignCountry() {
        when(countryScopeService.getScope())
                .thenReturn(new CountryScope(13L, false, List.of(1004)));
        when(countryScopeService.canAccessCountry(1)).thenReturn(false);

        PaymentBatchCreateRequest req = new PaymentBatchCreateRequest(20253, 1, 1, 1, null,
                List.of(new PaymentBatchCreateRequest.Line(10, new BigDecimal("100"), null, null)));

        assertThatThrownBy(() -> service.create(req))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        verify(batchRepository, never()).save(any());
    }

    // ── create ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("create stamps the signed-in preparer, the next DOF number and the attaché of a single-country officer")
    void createStampsPreparerAndBatchNo() {
        when(countryScopeService.getScope())
                .thenReturn(new CountryScope(13L, false, List.of(1004)));
        when(countryScopeService.getCurrentUserId()).thenReturn(Optional.of(13L));
        FiscalYear fy = new FiscalYear();
        fy.setId(2);
        when(fiscalPeriodService.requireYearOfQuarter(20253)).thenReturn(fy);
        when(batchRepository.highestBatchNo(20253, 1004)).thenReturn((short) 2);
        when(batchRepository.save(any())).thenAnswer(inv -> {
            PaymentBatch b = inv.getArgument(0);
            b.setId(99);
            return b;
        });
        when(lineRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));
        when(jdbcTemplate.queryForList(anyString(), eq(Integer.class), any(Object[].class))).thenReturn(List.of(10, 11));

        // No country in the request: the officer holds exactly one, so it is theirs.
        PaymentBatchCreateRequest req = new PaymentBatchCreateRequest(20253, null, 1, 1, "batch 3",
                List.of(new PaymentBatchCreateRequest.Line(10, new BigDecimal("300"), null, null),
                        new PaymentBatchCreateRequest.Line(11, new BigDecimal("200"), new BigDecimal("150"), "docs missing")));

        PaymentBatchDetail created = service.create(req);

        assertThat(created.batch().status()).isEqualTo(PaymentBatch.OPEN);
        assertThat(created.batch().preparedBy()).isEqualTo(13);
        assertThat(created.batch().batchNo()).isEqualTo((short) 3);
        assertThat(created.batch().studyCountryInId()).isEqualTo(1004);
        assertThat(created.batch().fiscalYearId()).isEqualTo(2);
        assertThat(created.batch().entitledTotal()).isEqualByComparingTo("500");
        assertThat(created.batch().disbursedTotal())
                .as("a line with no disbursed amount is paid in full; a held one is not")
                .isEqualByComparingTo("450");
        assertThat(created.lines()).allSatisfy(l -> assertThat(l.studentId()).isNotNull());
    }

    @Test
    @DisplayName("a line cannot disburse more than it entitles, and a student appears once")
    void createValidatesLines() {
        when(countryScopeService.getScope()).thenReturn(new CountryScope(1L, true, List.of()));
        when(countryScopeService.canAccessCountry(1)).thenReturn(true);
        FiscalYear fy = new FiscalYear();
        fy.setId(2);
        when(fiscalPeriodService.requireYearOfQuarter(20253)).thenReturn(fy);

        PaymentBatchCreateRequest over = new PaymentBatchCreateRequest(20253, 1, 1, 1, null,
                List.of(new PaymentBatchCreateRequest.Line(10, new BigDecimal("100"), new BigDecimal("101"), null)));
        assertThatThrownBy(() -> service.create(over))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("more than it entitles");

        PaymentBatchCreateRequest twice = new PaymentBatchCreateRequest(20253, 1, 1, 1, null,
                List.of(new PaymentBatchCreateRequest.Line(10, new BigDecimal("100"), null, null),
                        new PaymentBatchCreateRequest.Line(10, new BigDecimal("100"), null, null)));
        assertThatThrownBy(() -> service.create(twice))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("appears twice");

        verify(batchRepository, never()).save(any());
    }

    @Test
    @DisplayName("R5-20: a line for a student that does not exist is a 400 naming the student, before any save")
    void createRefusesAMissingStudent() {
        when(countryScopeService.getScope()).thenReturn(new CountryScope(1L, true, List.of()));
        when(countryScopeService.canAccessCountry(1)).thenReturn(true);
        FiscalYear fy = new FiscalYear();
        fy.setId(2);
        when(fiscalPeriodService.requireYearOfQuarter(20253)).thenReturn(fy);
        when(jdbcTemplate.queryForList(anyString(), eq(Integer.class), any(Object[].class))).thenReturn(List.of(10));

        PaymentBatchCreateRequest req = new PaymentBatchCreateRequest(20253, 1, 1, 1, null,
                List.of(new PaymentBatchCreateRequest.Line(10, new BigDecimal("100"), null, null),
                        new PaymentBatchCreateRequest.Line(999999, new BigDecimal("100"), null, null)));
        assertThatThrownBy(() -> service.create(req))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("999999")
                .extracting(e -> ((ResponseStatusException) e).getStatusCode().value()).isEqualTo(400);
        verify(fiscalPeriodService).requireMoheQuarter(20253);
        verify(batchRepository, never()).save(any());
    }

    // ── maker ≠ checker ──────────────────────────────────────────────────────

    @Test
    @DisplayName("the preparer cannot approve their own batch")
    void preparerCannotApprove() {
        when(batchRepository.findById(7)).thenReturn(Optional.of(batch(7, 1, PaymentBatch.OPEN, 13)));
        when(countryScopeService.canAccessCountry(anyInt())).thenReturn(true);

        assertThatThrownBy(() -> service.approve(7, 13))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        verify(batchRepository, never()).save(any());
    }

    @Test
    @DisplayName("a different user approves it")
    void checkerApproves() {
        when(batchRepository.findById(7)).thenReturn(Optional.of(batch(7, 1, PaymentBatch.OPEN, 13)));
        when(countryScopeService.canAccessCountry(anyInt())).thenReturn(true);
        when(batchRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PaymentBatch approved = service.approve(7, 2);

        assertThat(approved.getStatus()).isEqualTo(PaymentBatch.APPROVED);
        assertThat(approved.getApprovedBy()).isEqualTo(2);
        assertThat(approved.getApprovedAt()).isNotNull();
    }

    // ── status flow ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("only an open batch can be approved; only an approved one can be paid")
    void statusOrderIsEnforced() {
        when(countryScopeService.canAccessCountry(anyInt())).thenReturn(true);

        when(batchRepository.findById(1)).thenReturn(Optional.of(batch(1, 1, PaymentBatch.DISBURSED, 13)));
        assertThatThrownBy(() -> service.approve(1, 2))
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);

        when(batchRepository.findById(2)).thenReturn(Optional.of(batch(2, 1, PaymentBatch.OPEN, 13)));
        assertThatThrownBy(() -> service.markPaid(2, 2))
                .as("an unapproved batch pays nobody")
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);

        when(batchRepository.findById(3)).thenReturn(Optional.of(batch(3, 1, PaymentBatch.APPROVED, 13)));
        assertThatThrownBy(() -> service.cancel(3, 2))
                .as("an approved batch is withdrawn by not paying it, not by cancelling")
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);

        verify(advanceBalanceService, never()).recordDisbursement(any(), any(), any(), any());
    }

    @Test
    @DisplayName("paying books the disbursed total — not the entitled total — against the advance")
    void payingMovesTheAdvance() {
        when(countryScopeService.canAccessCountry(anyInt())).thenReturn(true);
        when(batchRepository.findById(7)).thenReturn(Optional.of(batch(7, 1, PaymentBatch.APPROVED, 13)));
        when(lineRepository.findByBatchIdOrderByIdAsc(7))
                .thenReturn(List.of(line(10, "300", "300"), line(11, "200", "150")));
        when(batchRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PaymentBatch paid = service.markPaid(7, 2);

        assertThat(paid.getStatus()).isEqualTo(PaymentBatch.DISBURSED);
        assertThat(paid.getDisbursedAt()).isNotNull();
        // 450 left the attaché; the 50 held back is still in the advance and
        // becomes Wafar at quarter close, not a disbursement now.
        verify(advanceBalanceService).recordDisbursement(eq(2), eq(1), eq(1), eq(new BigDecimal("450")));
    }
}
