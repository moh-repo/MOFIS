package com.dilaw.mohe.wafar;

import com.dilaw.mohe.config.tenant.WafarWriteKey;
import com.dilaw.mohe.repository.StudentDataRepository;
import com.dilaw.mohe.scope.AsUser;
import com.dilaw.mohe.security.policy.AccessControl;
import com.dilaw.mohe.security.policy.AccessSubject;
import com.dilaw.mohe.security.policy.AccessSubjectResolver;
import com.dilaw.mohe.security.policy.WafarPolicy;
import com.dilaw.mohe.ums.model.AdminTier;
import com.dilaw.mohe.ums.service.CountryScopeService;
import com.dilaw.mohe.ums.service.CountryScopeService.CountryScope;
import com.dilaw.mohe.wafar.dto.WafarSpendingCreateDto;
import com.dilaw.mohe.wafar.dto.WafarSpendingDto;
import com.dilaw.mohe.wafar.model.WafarSpending;
import com.dilaw.mohe.wafar.model.WafarSpendingCategory;
import com.dilaw.mohe.wafar.repository.WafarSpendingCategoryRepository;
import com.dilaw.mohe.wafar.repository.WafarSpendingRepository;
import com.dilaw.mohe.wafar.repository.WafarStudentFeePaymentRepository;
import com.dilaw.mohe.wafar.service.WafarSpendingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The officer who records a Wafar spend cannot be the one who approves it, and
 * nobody outside the attaché writes its Wafar at all.
 *
 * <p>Since the R-16 pass (6 Sep 2026) the rules are {@link WafarPolicy}; the
 * service asks {@link AccessControl} and audits what comes back. This test runs
 * the real policy behind a stubbed subject, so it is the policy under test as
 * well as the service's handling of its answer — including the one exemption
 * (decision D-13 b): the super admin may approve, never record, and every use
 * leaves an audit row and raises the row-level write key for the transaction.
 */
@ExtendWith(MockitoExtension.class)
class WafarMakerCheckerTest {

    private static final long MAKER = 13L;
    private static final long CHECKER = 2L;
    private static final long SUPER = 1L;

    @Mock private WafarSpendingRepository spendingRepository;
    @Mock private CountryScopeService countryScopeService;
    @Mock private WafarSpendingCategoryRepository categoryRepository;
    @Mock private WafarStudentFeePaymentRepository feePaymentRepository;
    @Mock private StudentDataRepository studentDataRepository;
    @Mock private com.dilaw.mohe.ums.service.UmsAuditLogService auditLogService;
    @Mock private WafarWriteKey wafarWriteKey;
    @Mock private AccessSubjectResolver subjects;
    @Mock private com.dilaw.mohe.wafar.service.WafarBalanceService balanceService;
    @InjectMocks private WafarSpendingService service;

    @BeforeEach
    void policies() {
        ReflectionTestUtils.setField(service, "accessControl",
                new AccessControl(List.of(new WafarPolicy(true)), subjects));
    }

    private static AccessSubject countryStaff(long userId, Integer... countries) {
        return new AccessSubject(userId, AdminTier.COUNTRY_STAFF, false, List.of(countries),
                Set.of("WAFAR_OFFICER"), Set.of("WAFAR.EDIT"), null);
    }

    private static AccessSubject superAdmin() {
        return new AccessSubject(SUPER, AdminTier.SUPER_ADMIN, true, List.of(), Set.of(), Set.of(), null);
    }

    private static AccessSubject ministryAdmin() {
        return new AccessSubject(CHECKER, AdminTier.MINISTRY_ADMIN, true, List.of(), Set.of(), Set.of("WAFAR.EDIT"), null);
    }

    private static WafarSpendingCategory rent() {
        WafarSpendingCategory c = new WafarSpendingCategory();
        c.setId(1);
        c.setCategoryCode("RENT");
        c.setCategoryNameAr("إيجار");
        c.setCategoryNameEn("Rent");
        c.setIsActive(true);
        return c;
    }

    private static WafarSpending pending(Integer createdBy) {
        WafarSpending s = new WafarSpending();
        s.setId(7);
        s.setCategory(rent());
        s.setStatus("PENDING");
        s.setStudyCountryInId(1);
        s.setAmount(new BigDecimal("100"));
        s.setSpendingDate(LocalDate.now());
        s.setCreatedBy(createdBy);
        return s;
    }

    private static WafarSpendingCreateDto rentInCountryOne() {
        WafarSpendingCreateDto dto = new WafarSpendingCreateDto();
        dto.setCategoryId(1);
        dto.setAmount(new BigDecimal("100"));
        dto.setSpendingDate(LocalDate.now());
        dto.setBeneficiaryType("OTHER");
        dto.setStudyCountryInId(1);
        return dto;
    }

    private static void assertForbidden(ThrowingCallableAlias call) {
        assertThatThrownBy(call::call)
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @FunctionalInterface
    private interface ThrowingCallableAlias {
        void call() throws Throwable;
    }

    @Test
    @DisplayName("create stamps the signed-in user as the maker")
    void createStampsMaker() {
        when(categoryRepository.findById(1)).thenReturn(Optional.of(rent()));
        when(countryScopeService.getScope()).thenReturn(new CountryScope(MAKER, false, List.of(1)));
        when(subjects.current()).thenReturn(countryStaff(MAKER, 1));
        when(spendingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        WafarSpendingDto saved = AsUser.call(MAKER, () -> service.create(rentInCountryOne()));
        assertThat(saved.getCreatedBy()).isEqualTo((int) MAKER);
        assertThat(saved.getStatus()).isEqualTo("PENDING");
    }

    @Test
    @DisplayName("the maker cannot approve their own spend")
    void makerCannotApprove() {
        when(spendingRepository.findById(7)).thenReturn(Optional.of(pending((int) MAKER)));
        when(countryScopeService.canAccessCountry(1)).thenReturn(true);
        when(subjects.current()).thenReturn(countryStaff(MAKER, 1));

        assertForbidden(() -> AsUser.call(MAKER, () -> service.approve(7)));
        verify(spendingRepository, never()).save(any());
    }

    @Test
    @DisplayName("another EDIT holder of the attaché approves it, and is recorded as the approver")
    void checkerApproves() {
        when(spendingRepository.findById(7)).thenReturn(Optional.of(pending((int) MAKER)));
        when(countryScopeService.canAccessCountry(1)).thenReturn(true);
        when(subjects.current()).thenReturn(countryStaff(CHECKER, 1));
        when(spendingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        WafarSpendingDto approved = AsUser.call(CHECKER, () -> service.approve(7));
        assertThat(approved.getStatus()).isEqualTo("APPROVED");
        assertThat(approved.getApprovedBy()).isEqualTo((int) CHECKER);
        assertThat(approved.getCreatedBy()).isEqualTo((int) MAKER);
        verify(wafarWriteKey, never()).elevateCurrentTransaction(anyString());
    }

    @Test
    @DisplayName("a super-admin may approve without owning the attaché; the hatch is audited and raises the write key (D-13)")
    void superAdminIsExemptAndAudited() {
        when(spendingRepository.findById(7)).thenReturn(Optional.of(pending((int) MAKER)));
        when(countryScopeService.canAccessCountry(1)).thenReturn(true);
        when(subjects.current()).thenReturn(superAdmin());
        when(spendingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        WafarSpendingDto approved = AsUser.call(SUPER, () -> service.approve(7));
        assertThat(approved.getStatus()).isEqualTo("APPROVED");
        assertThat(approved.getApprovedBy()).isEqualTo((int) SUPER);
        verify(auditLogService).log(org.mockito.ArgumentMatchers.eq(SUPER),
                org.mockito.ArgumentMatchers.eq("WAFAR_SUPERADMIN_APPROVE"),
                org.mockito.ArgumentMatchers.eq("WAFAR_SPENDING"), org.mockito.ArgumentMatchers.eq(7L),
                any(), any());
        verify(wafarWriteKey).elevateCurrentTransaction(anyString());
    }

    @Test
    @DisplayName("with the hatch switched off (D-13 a) the super admin is refused like any ministry account")
    void hatchCanBeClosed() {
        ReflectionTestUtils.setField(service, "accessControl",
                new AccessControl(List.of(new WafarPolicy(false)), subjects));
        when(spendingRepository.findById(7)).thenReturn(Optional.of(pending((int) MAKER)));
        when(countryScopeService.canAccessCountry(1)).thenReturn(true);
        when(subjects.current()).thenReturn(superAdmin());

        assertForbidden(() -> AsUser.call(SUPER, () -> service.approve(7)));
        verify(spendingRepository, never()).save(any());
        verify(wafarWriteKey, never()).elevateCurrentTransaction(anyString());
    }

    @Test
    @DisplayName("a ministry-level account cannot record a spend, whatever codes it holds (N-04)")
    void ministryCannotRecord() {
        when(categoryRepository.findById(1)).thenReturn(Optional.of(rent()));
        when(subjects.current()).thenReturn(ministryAdmin());

        assertThatThrownBy(() -> AsUser.call(CHECKER, () -> service.create(rentInCountryOne())))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("ministry accounts may only view it");
        verify(spendingRepository, never()).save(any());
    }

    @Test
    @DisplayName("the super admin cannot record a spend either: the hatch is approve-only")
    void superAdminCannotRecord() {
        when(categoryRepository.findById(1)).thenReturn(Optional.of(rent()));
        when(subjects.current()).thenReturn(superAdmin());

        assertForbidden(() -> AsUser.call(SUPER, () -> service.create(rentInCountryOne())));
        verify(spendingRepository, never()).save(any());
    }

    @Test
    @DisplayName("a checker outside the attaché is refused by the ownership rule before the maker check")
    void ownershipGateRunsForOrdinaryCheckers() {
        when(spendingRepository.findById(7)).thenReturn(Optional.of(pending((int) MAKER)));
        when(countryScopeService.canAccessCountry(1)).thenReturn(true);
        when(subjects.current()).thenReturn(countryStaff(CHECKER, 2));

        assertThatThrownBy(() -> AsUser.call(CHECKER, () -> service.approve(7)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("outside your scope");
        verify(spendingRepository, never()).save(any());
    }
}
