package com.dilaw.mohe.service;

import com.dilaw.mohe.repository.StudentQuarterRepository;
import com.dilaw.mohe.repository.StudentTotalReportAllRepository;
import com.dilaw.mohe.repository.StudyWafarTypeRepository;
import com.dilaw.mohe.ums.model.UmsPermission;
import com.dilaw.mohe.ums.repository.UmsPermissionRepository;
import com.dilaw.mohe.ums.service.UmsPermissionService;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mockito;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.Repository;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Round-5 QA R5-22: deleting a record that does not exist answered success (204/200) on
 * Wafar types, permissions, student quarters, backup files and total-report rows, the
 * same kind of answer as round-4 L4. Each now answers 404; an existing record is deleted
 * as before.
 */
class MissingRecordDeleteTest {

    private static void assertNotFound(ThrowingCallable call) {
        assertThatThrownBy(call).isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("Wafar type")
    void wafarType() {
        StudyWafarTypeRepository repo = Mockito.mock(StudyWafarTypeRepository.class);
        StudyWafarTypeService service = new StudyWafarTypeService();
        ReflectionTestUtils.setField(service, "repository", repo);
        when(repo.existsById(7)).thenReturn(true);

        assertNotFound(() -> service.delete(999999));
        verify(repo, never()).deleteById(999999);
        assertThatCode(() -> service.delete(7)).doesNotThrowAnyException();
        verify(repo).deleteById(7);
    }

    @Test
    @DisplayName("student quarter")
    void studentQuarter() {
        StudentQuarterRepository repo = Mockito.mock(StudentQuarterRepository.class);
        StudentQuarterService service = new StudentQuarterService();
        ReflectionTestUtils.setField(service, "repository", repo);
        when(repo.existsById(3)).thenReturn(true);

        assertNotFound(() -> service.delete(999999));
        assertThatCode(() -> service.delete(3)).doesNotThrowAnyException();
        verify(repo).deleteById(3);
    }

    @Test
    @DisplayName("total-report row")
    void totalReportRow() {
        StudentTotalReportAllRepository repo = Mockito.mock(StudentTotalReportAllRepository.class);
        StudentTotalReportAllService service = new StudentTotalReportAllService();
        ReflectionTestUtils.setField(service, "repository", repo);
        when(repo.existsById("R1")).thenReturn(true);

        assertNotFound(() -> service.delete("999999"));
        assertThatCode(() -> service.delete("R1")).doesNotThrowAnyException();
        verify(repo).deleteById("R1");
    }

    @Test
    @DisplayName("permission: a missing id is 404; an existing one is switched off")
    void permission() {
        UmsPermissionRepository repo = Mockito.mock(UmsPermissionRepository.class);
        UmsPermissionService service = new UmsPermissionService();
        ReflectionTestUtils.setField(service, "permissionRepository", repo);
        UmsPermission existing = new UmsPermission();
        existing.setIsActive(true);
        when(repo.findById(999999L)).thenReturn(Optional.empty());
        when(repo.findById(5L)).thenReturn(Optional.of(existing));

        assertNotFound(() -> service.deletePermission(999999L));
        verify(repo, never()).save(any());
        service.deletePermission(5L);
        assertThat(existing.getIsActive()).isFalse();
    }

    @Test
    @DisplayName("backup file: a name with no file is 404; an existing file is removed")
    void backupFile(@TempDir Path dir) throws Exception {
        BackupService service = new BackupService();
        ReflectionTestUtils.setField(service, "backupDirectory", dir.toString());
        Path file = Files.writeString(dir.resolve("MOHE_20260927.bak"), "x");

        assertNotFound(() -> service.deleteBackup("missing.bak"));
        service.deleteBackup("MOHE_20260927.bak");
        assertThat(file).doesNotExist();
    }

    /**
     * Round-6 retest: 15 System Coding tables still answered 200 for a missing id. The
     * study-location lists, country-all, the older finance code lists and alerts had the
     * same bare deleteById and were not probed. Every repository the service holds is
     * mocked, so the child counts of governorate and study country come back empty.
     */
    @ParameterizedTest(name = "{0}")
    @ValueSource(classes = {
            StudentGenderService.class, StudentDegreeService.class, StudentCurrentLevelService.class,
            StudentAgencyTypeService.class, StudentMinistryTypeService.class, StudentStudyTypeService.class,
            StudentStudyStatusService.class, StudyStatusService.class, StudyDegreeInService.class,
            StudentUniversityTypeService.class, StudentMajorService.class, StudyReceivedWhomStatusService.class,
            MoheSubMainService.class, StudentGovService.class, StudentGovMudService.class,
            StudyCountryInService.class, StudyGovInService.class, StudyUniversityInService.class,
            StudyCollegeInService.class, StudyEductionInService.class, StudyMajorInService.class,
            StudyCurrencyInService.class, StudentCountryAllService.class, StudentEductionService.class,
            StudentMinistrySubTypeService.class, StudyFinanceDofYearService.class,
            StudyFinReceivedStatusService.class, StudyFinReceivedWhomStatusService.class,
            StudyPayFeeInStatusService.class, AlertService.class})
    @SuppressWarnings("unchecked")
    void lookupAndCodeLists(Class<?> serviceType) throws Throwable {
        Object service = serviceType.getDeclaredConstructor().newInstance();
        CrudRepository<Object, Integer> repo = null;
        for (Field field : serviceType.getDeclaredFields()) {
            if (Repository.class.isAssignableFrom(field.getType())) {
                Object mock = Mockito.mock(field.getType());
                ReflectionTestUtils.setField(service, field.getName(), mock);
                if (field.getName().equals("repository") || field.getName().equals("alertRepository")) {
                    repo = (CrudRepository<Object, Integer>) mock;
                }
            }
        }
        assertThat(repo).as("the service's own repository").isNotNull();
        Method delete = serviceType.getMethod(
                serviceType == AlertService.class ? "deleteAlert" : "delete", Integer.class);
        when(repo.existsById(7)).thenReturn(true);

        assertNotFound(() -> invoke(delete, service, 999999));
        verify(repo, never()).deleteById(999999);
        invoke(delete, service, 7);
        verify(repo).deleteById(7);
    }

    private static void invoke(Method method, Object target, Integer id) throws Throwable {
        try {
            method.invoke(target, id);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }
}
