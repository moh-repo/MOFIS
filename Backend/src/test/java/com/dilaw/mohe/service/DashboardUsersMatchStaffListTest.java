package com.dilaw.mohe.service;

import com.dilaw.mohe.ums.repository.UmsUserRepository;
import com.dilaw.mohe.ums.service.CountryScopeService;
import com.dilaw.mohe.ums.service.ReportCountryScopeHelper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Live QA L6 (§5.2, 25 Sep 2026), decision D12-a: a Jordan admin's dashboard counted
 * 25 users while their staff list showed 27. The counter took the accounts assigned to
 * any of the admin's countries; the list takes the accounts the admin may administer,
 * unassigned ones included. The counter now counts with the list's own filter.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DashboardUsersMatchStaffListTest {

    @Mock private UmsUserRepository umsUserRepository;
    @Mock private CountryScopeService countryScopeService;
    @Mock private ReportCountryScopeHelper countryScopeHelper;
    @InjectMocks private StatisticsService service;

    @Test
    @DisplayName("a country admin's Users figure is the staff list's count")
    @SuppressWarnings("unchecked")
    void countryAdminCountsTheStaffList() {
        when(countryScopeService.getScope()).thenReturn(
                new CountryScopeService.CountryScope(12L, false, true, List.of(1004)));
        when(umsUserRepository.count(any(Specification.class))).thenReturn(27L);
        when(umsUserRepository.countActiveInCountries(anyList())).thenReturn(25L);

        StatisticsService.UsersSummary summary = service.usersSummary(null);

        assertThat(summary.total()).isEqualTo(27L);
        assertThat(summary.scoped()).isTrue();
        verify(umsUserRepository, never()).countActiveInCountries(anyList());
    }

    @Test
    @DisplayName("the ministry figure and an explicit country are counted as before")
    void ministryAndExplicitCountryUnchanged() {
        when(countryScopeService.getScope()).thenReturn(
                new CountryScopeService.CountryScope(1L, true, true, List.of()));
        when(umsUserRepository.countByIsDeletedFalse()).thenReturn(42L);
        assertThat(service.usersSummary(null).total()).isEqualTo(42L);

        when(umsUserRepository.countActiveInCountries(List.of(1004))).thenReturn(21L);
        assertThat(service.usersSummary(1004).total()).isEqualTo(21L);
    }
}
