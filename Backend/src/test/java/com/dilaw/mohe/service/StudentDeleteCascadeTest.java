package com.dilaw.mohe.service;

import com.dilaw.mohe.codes.PortalAccountStatus;
import com.dilaw.mohe.model.StudentDatum;
import com.dilaw.mohe.model.StudentFinancePayment;
import com.dilaw.mohe.repository.StudentDataRepository;
import com.dilaw.mohe.repository.StudentFinancePaymentRepository;
import com.dilaw.mohe.repository.StudentFinanceRepository;
import com.dilaw.mohe.studentportal.model.StudentUser;
import com.dilaw.mohe.studentportal.repository.StudentSessionRepository;
import com.dilaw.mohe.studentportal.repository.StudentUserRepository;
import com.dilaw.mohe.ums.service.CountryScopeService;
import com.dilaw.mohe.ums.service.CountryScopeService.CountryScope;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Live QA H7 (25 Sep 2026), decision D5-a: deleting a student left its finance row,
 * which the finance list, the dashboard trend ("20251") and the finance views kept
 * counting. The delete now removes the finance and payment rows and deactivates
 * the portal account, ending its sessions.
 */
class StudentDeleteCascadeTest {

    private static final int STUDENT = 10116;
    private static final int PORTAL_USER = 77;

    private StudentFinanceRepository finance;
    private StudentFinancePaymentRepository payments;
    private StudentUserRepository portalUsers;
    private StudentSessionRepository sessions;
    private StudentFinanceArchiveService archive;
    private StudentDeletionCascade cascade;

    @BeforeEach
    void setUp() {
        finance = Mockito.mock(StudentFinanceRepository.class);
        payments = Mockito.mock(StudentFinancePaymentRepository.class);
        portalUsers = Mockito.mock(StudentUserRepository.class);
        sessions = Mockito.mock(StudentSessionRepository.class);
        cascade = new StudentDeletionCascade();
        ReflectionTestUtils.setField(cascade, "financeRepository", finance);
        ReflectionTestUtils.setField(cascade, "paymentRepository", payments);
        ReflectionTestUtils.setField(cascade, "studentUserRepository", portalUsers);
        ReflectionTestUtils.setField(cascade, "sessionRepository", sessions);
        archive = Mockito.mock(StudentFinanceArchiveService.class);
        ReflectionTestUtils.setField(cascade, "financeArchive", archive);
    }

    private static com.dilaw.mohe.model.StudentFinance financeRow(Integer year, Integer quarter) {
        com.dilaw.mohe.model.StudentFinance f = new com.dilaw.mohe.model.StudentFinance();
        f.setId(STUDENT);
        f.setStudentFinRecievedYear(year);
        f.setStudentFinRecievedMonth(quarter);
        f.setMoheQuarterId(33);
        f.setStudentFinName("A student");
        f.setStudentFinRecievedYearMonth(year == null || quarter == null ? null : year * 10 + quarter);
        return f;
    }

    private static StudentUser activeAccount() {
        StudentUser u = new StudentUser();
        u.setStudentUserId(PORTAL_USER);
        u.setStudentId(STUDENT);
        u.setStatus(PortalAccountStatus.ACTIVE);
        return u;
    }

    @Test
    @DisplayName("the finance row and its payment rows are deleted")
    void financeAndPaymentsGo() {
        List<StudentFinancePayment> rows = List.of(new StudentFinancePayment(), new StudentFinancePayment());
        when(payments.findByStudentIdOrderByPaymentIdAsc(STUDENT)).thenReturn(rows);
        com.dilaw.mohe.model.StudentFinance row = financeRow(2026, 3);
        when(finance.findById(STUDENT)).thenReturn(Optional.of(row));
        when(portalUsers.findByStudentIdAndIsDeletedFalse(STUDENT)).thenReturn(Optional.empty());

        cascade.onStudentDeleted(STUDENT);

        verify(payments).deleteAll(rows);
        verify(finance).delete(row);
    }

    @Test
    @DisplayName("P5-11: the finance row is archived before it is removed (\"they must be archived even if deleted\")")
    void financeIsArchivedFirst() {
        com.dilaw.mohe.model.StudentFinance row = financeRow(2026, 3);
        when(payments.findByStudentIdOrderByPaymentIdAsc(STUDENT)).thenReturn(List.of());
        when(finance.findById(STUDENT)).thenReturn(Optional.of(row));
        when(portalUsers.findByStudentIdAndIsDeletedFalse(STUDENT)).thenReturn(Optional.empty());

        cascade.onStudentDeleted(STUDENT);

        org.mockito.InOrder order = Mockito.inOrder(archive, finance);
        order.verify(archive).archive(row, 33);
        order.verify(finance).delete(row);
    }

    @Test
    @DisplayName("P5-11: a row the archive cannot key (a YYYYQ year like 20251, no quarter) is removed, not archived")
    void unkeyableRowIsNotArchived() {
        when(payments.findByStudentIdOrderByPaymentIdAsc(STUDENT)).thenReturn(List.of());
        when(portalUsers.findByStudentIdAndIsDeletedFalse(STUDENT)).thenReturn(Optional.empty());
        com.dilaw.mohe.model.StudentFinance noName = financeRow(2026, 2);
        noName.setStudentFinName(null);
        for (com.dilaw.mohe.model.StudentFinance row : List.of(financeRow(20251, 1), financeRow(2026, null), noName)) {
            when(finance.findById(STUDENT)).thenReturn(Optional.of(row));
            cascade.onStudentDeleted(STUDENT);
            verify(finance).delete(row);
        }
        verify(archive, never()).archive(any(), any());
    }

    @Test
    @DisplayName("the portal account is set INACTIVE and its sessions end")
    void portalAccountDeactivated() {
        StudentUser account = activeAccount();
        when(payments.findByStudentIdOrderByPaymentIdAsc(STUDENT)).thenReturn(List.of());
        when(portalUsers.findByStudentIdAndIsDeletedFalse(STUDENT)).thenReturn(Optional.of(account));

        cascade.onStudentDeleted(STUDENT);

        assertThat(account.getStatus()).isEqualTo(PortalAccountStatus.INACTIVE);
        verify(portalUsers).save(account);
        verify(sessions).deleteByStudentUserId(PORTAL_USER);
    }

    @Test
    @DisplayName("a student with no finance row and no portal account is deleted without error")
    void nothingToCascade() {
        when(payments.findByStudentIdOrderByPaymentIdAsc(STUDENT)).thenReturn(List.of());
        when(finance.findById(STUDENT)).thenReturn(Optional.empty());
        when(portalUsers.findByStudentIdAndIsDeletedFalse(STUDENT)).thenReturn(Optional.empty());

        cascade.onStudentDeleted(STUDENT);

        verify(finance, never()).delete(any(com.dilaw.mohe.model.StudentFinance.class));
        verify(archive, never()).archive(any(), any());
        verify(sessions, never()).deleteByStudentUserId(any());
    }

    @Test
    @DisplayName("StudentDataService.delete marks the student deleted and runs the cascade")
    void deleteRunsTheCascade() {
        StudentDataRepository students = Mockito.mock(StudentDataRepository.class);
        CountryScopeService scope = Mockito.mock(CountryScopeService.class);
        StudentDeletionCascade spy = Mockito.mock(StudentDeletionCascade.class);
        StudentDatum s = new StudentDatum();
        s.setStudentId(STUDENT);
        s.setStudyCountryIn(1);
        when(students.findById(STUDENT)).thenReturn(Optional.of(s));
        when(scope.getScope()).thenReturn(new CountryScope(1L, true, List.of()));
        when(scope.canAccessCountry(any())).thenReturn(true);
        StudentDataService service = new StudentDataService();
        ReflectionTestUtils.setField(service, "studentDataRepository", students);
        ReflectionTestUtils.setField(service, "countryScopeService", scope);
        ReflectionTestUtils.setField(service, "deletionCascade", spy);

        service.delete(STUDENT);

        assertThat(s.getIsDeleted()).isTrue();
        verify(students).save(s);
        verify(spy).onStudentDeleted(STUDENT);
    }
}
