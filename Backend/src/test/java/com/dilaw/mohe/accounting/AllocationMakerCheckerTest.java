package com.dilaw.mohe.accounting;

import com.dilaw.mohe.accounting.model.AttacheAllocation;
import com.dilaw.mohe.accounting.repository.AttacheAllocationRepository;
import com.dilaw.mohe.accounting.service.FiscalPeriodService;
import com.dilaw.mohe.accounting.service.AdvanceBalanceService;
import com.dilaw.mohe.accounting.service.AllocationService;
import com.dilaw.mohe.ums.service.CountryScopeService;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

/**
 * The officer who drafts an allocation cannot be the one who approves it.
 *
 * <p>{@code ALLOCATION_CREATE} and {@code ALLOCATION_APPROVE} are separate
 * authorities so the split is possible; every administrator holds both, so the
 * service has to enforce it. The creator is stamped from the signed-in user on
 * create (not taken from the request body), and approve compares against it.
 */
@ExtendWith(MockitoExtension.class)
class AllocationMakerCheckerTest {

    @Mock private AttacheAllocationRepository allocationRepository;
    @Mock private AdvanceBalanceService advanceBalanceService;
    @Mock private JdbcTemplate jdbcTemplate;
    @Mock private CountryScopeService countryScopeService;
    @Mock private FiscalPeriodService fiscalPeriodService;
    @Mock private com.dilaw.mohe.accounting.service.AttacheCountryValidator attacheCountries;
    @InjectMocks private AllocationService service;

    /** R-16: the maker-checker rule lives in {@code MakerCheckerPolicy}; wire the real one in. */
    @org.junit.jupiter.api.BeforeEach
    void policies() {
        org.springframework.test.util.ReflectionTestUtils.setField(service, "accessControl",
                com.dilaw.mohe.security.policy.TestAccessControl.of(new com.dilaw.mohe.security.policy.MakerCheckerPolicy()));
    }

    private AttacheAllocation draft(Integer createdBy) {
        AttacheAllocation a = new AttacheAllocation();
        a.setId(7);
        a.setStatus(AttacheAllocation.DRAFT);
        a.setStudyCountryInId(1);
        a.setMoheQuarterKey(20253);
        a.setFiscalYearId(2);
        a.setAmount(new BigDecimal("1000"));
        a.setAllocationType(AttacheAllocation.REGULAR);
        a.setCreatedBy(createdBy);
        return a;
    }

    @Test
    @DisplayName("create stamps the signed-in user as the maker, whatever the body said")
    void createStampsMaker() {
        AttacheAllocation body = draft(999);
        when(countryScopeService.canAccessCountry(1)).thenReturn(true);
        when(countryScopeService.getCurrentUserId()).thenReturn(Optional.of(13L));
        when(allocationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        com.dilaw.mohe.accounting.model.FiscalYear fy = new com.dilaw.mohe.accounting.model.FiscalYear();
        fy.setId(2);
        when(fiscalPeriodService.requireYearOfQuarter(20253)).thenReturn(fy);

        AttacheAllocation saved = service.create(body);
        assertThat(saved.getCreatedBy()).isEqualTo(13);
    }

    @Test
    @DisplayName("the maker cannot approve their own draft")
    void makerCannotApprove() {
        when(allocationRepository.findById(7)).thenReturn(Optional.of(draft(13)));
        when(countryScopeService.canAccessCountry(anyInt())).thenReturn(true);

        assertThatThrownBy(() -> service.approve(7, 13))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("a different user approves it")
    void checkerApproves() {
        when(allocationRepository.findById(7)).thenReturn(Optional.of(draft(13)));
        when(countryScopeService.canAccessCountry(anyInt())).thenReturn(true);
        when(allocationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AttacheAllocation approved = service.approve(7, 2);
        assertThat(approved.getStatus()).isEqualTo(AttacheAllocation.APPROVED);
        assertThat(approved.getUpdatedBy()).isEqualTo(2);
    }
}
