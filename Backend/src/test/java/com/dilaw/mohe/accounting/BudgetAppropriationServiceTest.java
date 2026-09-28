package com.dilaw.mohe.accounting;

import com.dilaw.mohe.accounting.dto.AppropriationView;
import com.dilaw.mohe.accounting.model.AttacheAllocation;
import com.dilaw.mohe.accounting.model.BudgetAppropriation;
import com.dilaw.mohe.accounting.repository.AttacheAllocationRepository;
import com.dilaw.mohe.accounting.repository.BudgetAppropriationRepository;
import com.dilaw.mohe.accounting.service.BudgetAppropriationService;
import com.dilaw.mohe.ums.service.CountryScopeService;
import com.dilaw.mohe.ums.service.CountryScopeService.CountryScope;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Budget appropriations: a null country is the ministry-wide figure and is
 * shared; an approved figure is revised, never overwritten; the person who
 * entered a figure cannot approve it.
 */
@ExtendWith(MockitoExtension.class)
class BudgetAppropriationServiceTest {

    @Mock private BudgetAppropriationRepository appropriationRepository;
    @Mock private AttacheAllocationRepository allocationRepository;
    @Mock private CountryScopeService countryScopeService;
    @Mock private com.dilaw.mohe.accounting.service.AttacheCountryValidator attacheCountries;
    @InjectMocks private BudgetAppropriationService service;

    /** R-16: the maker-checker rule lives in {@code MakerCheckerPolicy}; wire the real one in. */
    @org.junit.jupiter.api.BeforeEach
    void policies() {
        org.springframework.test.util.ReflectionTestUtils.setField(service, "accessControl",
                com.dilaw.mohe.security.policy.TestAccessControl.of(new com.dilaw.mohe.security.policy.MakerCheckerPolicy()));
    }

    private static BudgetAppropriation approp(int id, Integer countryId, String status, String amount, Integer createdBy) {
        BudgetAppropriation a = new BudgetAppropriation();
        a.setId(id);
        a.setFiscalYearId(3);
        a.setFeeTypeId(1);
        a.setCurrencyId(1);
        a.setStudyCountryInId(countryId);
        a.setStatus(status);
        a.setAmount(new BigDecimal(amount));
        a.setCreatedBy(createdBy);
        return a;
    }

    private static Object[] committed(Integer feeType, int currency, int country, String status, String amount) {
        return new Object[] { feeType, currency, country, status, new BigDecimal(amount) };
    }

    @Test
    @DisplayName("an officer sees the ministry-wide line and their own attaché's, not another attaché's")
    void nullCountryIsSharedAndOthersAreHidden() {
        when(countryScopeService.getScope()).thenReturn(new CountryScope(13L, false, List.of(1004)));
        when(appropriationRepository.findByFiscalYearIdOrderByFeeTypeIdAscCurrencyIdAscStudyCountryInIdAsc(3))
                .thenReturn(List.of(
                        approp(1, null, "APPROVED", "1000000", 1),
                        approp(2, 1, "APPROVED", "600000", 1),
                        approp(3, 1004, "APPROVED", "50000", 1)));
        when(allocationRepository.sumCommittedByStream(3)).thenReturn(List.of(
                committed(1, 1, 1, AttacheAllocation.TRANSFERRED, "500000"),
                committed(1, 1, 1004, AttacheAllocation.TRANSFERRED, "20000"),
                committed(1, 1, 1004, AttacheAllocation.APPROVED, "5000"),
                committed(null, 1, 1004, AttacheAllocation.TRANSFERRED, "999")));

        List<AppropriationView> seen = service.forYear(3);

        assertThat(seen).extracting(AppropriationView::id)
                .as("the national line is nobody's secret; Egypt's own line is Egypt's")
                .containsExactly(1, 3);

        AppropriationView national = seen.get(0);
        assertThat(national.allocated())
                .as("against the national line an officer is shown what reached their attachés, not the ministry total")
                .isEqualByComparingTo("25000");
        assertThat(national.transferred()).isEqualByComparingTo("20000");
        assertThat(national.remaining()).isEqualByComparingTo("975000");

        AppropriationView jordan = seen.get(1);
        assertThat(jordan.allocated())
                .as("an allocation with no fee type covers every stream and is attributed to none")
                .isEqualByComparingTo("25000");
    }

    @Test
    @DisplayName("the ministry sees every row and the full drawn-down figure")
    void ministrySeesAll() {
        when(countryScopeService.getScope()).thenReturn(new CountryScope(1L, true, List.of()));
        when(appropriationRepository.findByFiscalYearIdOrderByFeeTypeIdAscCurrencyIdAscStudyCountryInIdAsc(3))
                .thenReturn(List.of(approp(1, null, "APPROVED", "1000000", 1), approp(2, 1, "DRAFT", "600000", 1)));
        when(allocationRepository.sumCommittedByStream(3)).thenReturn(List.of(
                committed(1, 1, 1, AttacheAllocation.TRANSFERRED, "500000"),
                committed(1, 1, 1004, AttacheAllocation.TRANSFERRED, "20000")));

        List<AppropriationView> seen = service.forYear(3);

        assertThat(seen).hasSize(2);
        assertThat(seen.get(0).allocated()).isEqualByComparingTo("520000");
        assertThat(seen.get(1).allocated()).as("Egypt's own line is drawn only by Egypt").isEqualByComparingTo("500000");
    }

    @Test
    @DisplayName("only the ministry may enter a ministry-wide figure; a single-attaché officer gets theirs")
    void officerCannotCreateNullCountry() {
        when(countryScopeService.getScope()).thenReturn(new CountryScope(13L, false, List.of(1004)));
        when(countryScopeService.getCurrentUserId()).thenReturn(Optional.of(13L));
        when(appropriationRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        BudgetAppropriation created = service.create(approp(0, null, "DRAFT", "50000", null));

        assertThat(created.getStudyCountryInId())
                .as("stored as Jordan's, never as the national line")
                .isEqualTo(1004);
        assertThat(created.getStatus()).isEqualTo(BudgetAppropriation.DRAFT);
        assertThat(created.getCreatedBy()).isEqualTo(13);

        when(countryScopeService.canAccessCountry(1)).thenReturn(false);
        assertThatThrownBy(() -> service.create(approp(0, 1, "DRAFT", "50000", null)))
                .as("nor may they file one under somebody else's attaché")
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("editing an approved figure revises it and opens a new draft; the old row survives")
    void updateOfApprovedRevises() {
        when(countryScopeService.getScope()).thenReturn(new CountryScope(1L, true, List.of()));
        when(countryScopeService.getCurrentUserId()).thenReturn(Optional.of(2L));
        BudgetAppropriation approved = approp(5, null, BudgetAppropriation.APPROVED, "1000000", 1);
        when(appropriationRepository.findById(5)).thenReturn(Optional.of(approved));
        when(appropriationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(appropriationRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        BudgetAppropriation changes = new BudgetAppropriation();
        changes.setAmount(new BigDecimal("1200000"));
        changes.setReferenceNumber("MOF/2026/17");

        BudgetAppropriation replacement = service.update(5, changes);

        assertThat(approved.getStatus()).isEqualTo(BudgetAppropriation.REVISED);
        assertThat(approved.getAmount()).as("the approved figure is history, not overwritten").isEqualByComparingTo("1000000");
        assertThat(replacement.getId()).isNull();
        assertThat(replacement.getStatus()).isEqualTo(BudgetAppropriation.DRAFT);
        assertThat(replacement.getAmount()).isEqualByComparingTo("1200000");
        assertThat(replacement.getReferenceNumber()).isEqualTo("MOF/2026/17");
        assertThat(replacement.getCreatedBy()).isEqualTo(2);

        ArgumentCaptor<BudgetAppropriation> saved = ArgumentCaptor.forClass(BudgetAppropriation.class);
        verify(appropriationRepository, times(1)).save(saved.capture());
        assertThat(saved.getValue()).isSameAs(approved);
    }

    @Test
    @DisplayName("a draft is edited in place; a revised row is closed")
    void updateOfDraftAndRevised() {
        when(countryScopeService.getScope()).thenReturn(new CountryScope(1L, true, List.of()));
        when(countryScopeService.getCurrentUserId()).thenReturn(Optional.of(2L));
        when(appropriationRepository.findById(6)).thenReturn(Optional.of(approp(6, null, BudgetAppropriation.DRAFT, "10", 1)));
        when(appropriationRepository.findById(7)).thenReturn(Optional.of(approp(7, null, BudgetAppropriation.REVISED, "10", 1)));
        when(appropriationRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        BudgetAppropriation changes = new BudgetAppropriation();
        changes.setAmount(new BigDecimal("20"));

        BudgetAppropriation draft = service.update(6, changes);
        assertThat(draft.getId()).isEqualTo(6);
        assertThat(draft.getAmount()).isEqualByComparingTo("20");
        assertThat(draft.getUpdatedBy()).isEqualTo(2);

        assertThatThrownBy(() -> service.update(7, changes))
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    @DisplayName("the officer who entered the figure cannot approve it; someone else can")
    void makerCannotApprove() {
        when(countryScopeService.getScope()).thenReturn(new CountryScope(1L, true, List.of()));
        when(appropriationRepository.findById(8)).thenReturn(Optional.of(approp(8, null, BudgetAppropriation.DRAFT, "10", 13)));

        assertThatThrownBy(() -> service.approve(8, 13))
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        verify(appropriationRepository, never()).save(any());

        when(appropriationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        BudgetAppropriation approved = service.approve(8, 2);
        assertThat(approved.getStatus()).isEqualTo(BudgetAppropriation.APPROVED);
        assertThat(approved.getUpdatedBy()).isEqualTo(2);
    }

    @Test
    @DisplayName("FIN-10: A enters, B edits the amount, B cannot approve; a third person can")
    void lastEditorCannotApprove() {
        when(countryScopeService.getScope()).thenReturn(new CountryScope(1L, true, List.of()));
        BudgetAppropriation draft = approp(8, null, BudgetAppropriation.DRAFT, "50000", 13);
        when(appropriationRepository.findById(8)).thenReturn(Optional.of(draft));
        when(countryScopeService.getCurrentUserId()).thenReturn(Optional.of(14L));
        when(appropriationRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        BudgetAppropriation edit = approp(8, null, BudgetAppropriation.DRAFT, "500000", null);
        service.update(8, edit);
        assertThat(draft.getUpdatedBy()).isEqualTo(14);

        assertThatThrownBy(() -> service.approve(8, 14))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("last edited")
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        verify(appropriationRepository, never()).save(any());

        when(appropriationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        assertThat(service.approve(8, 2).getStatus()).isEqualTo(BudgetAppropriation.APPROVED);
    }
}
