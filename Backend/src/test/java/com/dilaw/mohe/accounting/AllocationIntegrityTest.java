package com.dilaw.mohe.accounting;

import com.dilaw.mohe.accounting.model.AttacheAllocation;
import com.dilaw.mohe.accounting.model.FiscalYear;
import com.dilaw.mohe.accounting.repository.AttacheAllocationRepository;
import com.dilaw.mohe.accounting.service.AdvanceBalanceService;
import com.dilaw.mohe.accounting.service.AllocationService;
import com.dilaw.mohe.accounting.service.FiscalPeriodService;
import com.dilaw.mohe.ums.service.CountryScopeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Allocations: FIN-05 (a create that merged onto an existing row), FIN-13 (a fiscal
 * year that contradicted the quarter), FIN-12 (money moved in a closed year) and the
 * FIN-03 transfer race.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AllocationIntegrityTest {

    @Mock private AttacheAllocationRepository allocationRepository;
    @Mock private AdvanceBalanceService advanceBalanceService;
    @Mock private JdbcTemplate jdbcTemplate;
    @Mock private CountryScopeService countryScopeService;
    @Mock private FiscalPeriodService fiscalPeriodService;
    @Mock private com.dilaw.mohe.accounting.service.AttacheCountryValidator attacheCountries;
    @InjectMocks private AllocationService service;

    @BeforeEach
    void setUp() {
        org.springframework.test.util.ReflectionTestUtils.setField(service, "accessControl",
                com.dilaw.mohe.security.policy.TestAccessControl.of(new com.dilaw.mohe.security.policy.MakerCheckerPolicy()));
        FiscalYear fy2025 = new FiscalYear();
        fy2025.setId(2);
        when(fiscalPeriodService.requireYearOfQuarter(20253)).thenReturn(fy2025);
        when(countryScopeService.canAccessCountry(anyInt())).thenReturn(true);
        when(countryScopeService.getCurrentUserId()).thenReturn(Optional.of(13L));
        when(allocationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(allocationRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private static AttacheAllocation body() {
        AttacheAllocation a = new AttacheAllocation();
        a.setStudyCountryInId(1);
        a.setMoheQuarterKey(20253);
        a.setCurrencyId(1);
        a.setAmount(new BigDecimal("1000"));
        a.setAllocationType(AttacheAllocation.REGULAR);
        a.setNotes("Q3 stipends");
        return a;
    }

    private static AttacheAllocation approved() {
        AttacheAllocation a = body();
        a.setId(7);
        a.setFiscalYearId(2);
        a.setStatus(AttacheAllocation.APPROVED);
        a.setCreatedBy(13);
        return a;
    }

    @Test
    @DisplayName("FIN-05: a POST carrying an existing id and status creates a new draft; the original is untouched")
    void createNeverMergesOntoAnExistingRow() {
        AttacheAllocation posted = body();
        posted.setId(42);                                   // a TRANSFERRED allocation's id
        posted.setStatus(AttacheAllocation.TRANSFERRED);
        posted.setRowVersion(5L);
        posted.setTransferReference("SWIFT-1");
        posted.setApprovalRequestId(9);

        service.create(posted);

        ArgumentCaptor<AttacheAllocation> saved = ArgumentCaptor.forClass(AttacheAllocation.class);
        verify(allocationRepository).save(saved.capture());
        AttacheAllocation row = saved.getValue();
        assertThat(row).isNotSameAs(posted);
        assertThat(row.getId()).as("no id: persisted as a new row, never merged").isNull();
        assertThat(row.getRowVersion()).isNull();
        assertThat(row.getStatus()).isEqualTo(AttacheAllocation.DRAFT);
        assertThat(row.getTransferReference()).isNull();
        assertThat(row.getApprovalRequestId()).isNull();
        assertThat(row.getCreatedBy()).isEqualTo(13);
        assertThat(row.getAmount()).isEqualByComparingTo("1000");
        assertThat(row.getNotes()).isEqualTo("Q3 stipends");
        verify(allocationRepository, never()).findById(any());
    }

    @Test
    @DisplayName("FIN-13: the fiscal year always comes from the quarter, whatever the body says")
    void fiscalYearFollowsTheQuarter() {
        AttacheAllocation posted = body();
        posted.setFiscalYearId(99);

        AttacheAllocation row = service.create(posted);

        assertThat(row.getFiscalYearId()).isEqualTo(2);
    }

    @Test
    @DisplayName("FIN-12: nothing is drafted, approved or transferred in a closed fiscal year")
    void closedYearRefused() {
        doThrow(new ResponseStatusException(HttpStatus.CONFLICT, "Fiscal year 2025 is closed"))
                .when(fiscalPeriodService).assertYearOpen(2);

        assertConflict(() -> service.create(body()));

        AttacheAllocation draft = approved();
        draft.setStatus(AttacheAllocation.DRAFT);
        when(allocationRepository.findById(7)).thenReturn(Optional.of(draft));
        assertConflict(() -> service.approve(7, 2));

        when(allocationRepository.findById(7)).thenReturn(Optional.of(approved()));
        assertConflict(() -> service.markTransferred(7, null, "ref"));

        verify(allocationRepository, never()).save(any());
        verify(advanceBalanceService, never()).recordTransfer(any(), any(), any(), any());
    }

    @Test
    @DisplayName("FIN-12: a transfer dated in a closed period is refused")
    void transferIntoClosedPeriodRefused() {
        LocalDate march = LocalDate.of(2025, 3, 15);
        doThrow(new ResponseStatusException(HttpStatus.CONFLICT, "Period 3 is closed"))
                .when(fiscalPeriodService).assertPeriodOpen(march);
        when(allocationRepository.findById(7)).thenReturn(Optional.of(approved()));

        assertConflict(() -> service.markTransferred(7, march, "ref"));
        verify(advanceBalanceService, never()).recordTransfer(any(), any(), any(), any());
    }

    @Test
    @DisplayName("FIN-03: the second of two concurrent transfers fails on the version before the advance grows")
    void concurrentTransferBooksOnce() {
        when(allocationRepository.findById(7)).thenReturn(Optional.of(approved()));
        doThrow(new ObjectOptimisticLockingFailureException(AttacheAllocation.class, 7))
                .when(allocationRepository).saveAndFlush(any());

        assertThatThrownBy(() -> service.markTransferred(7, null, "ref"))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
        verify(advanceBalanceService, never()).recordTransfer(any(), any(), any(), any());
    }

    private static void assertConflict(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call).isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
    }
}
