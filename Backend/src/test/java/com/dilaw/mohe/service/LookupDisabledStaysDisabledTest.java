package com.dilaw.mohe.service;

import com.dilaw.mohe.model.StudentGov;
import com.dilaw.mohe.model.StudentGovMud;
import com.dilaw.mohe.model.StudyStatus;
import com.dilaw.mohe.repository.StudentGovMudRepository;
import com.dilaw.mohe.repository.StudentGovRepository;
import com.dilaw.mohe.repository.StudyStatusRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Live QA M12 (25 Sep 2026): editing a disabled Study Status, Governorate or
 * Municipality with a body that left out the Active flag re-enabled it, because the
 * entity initialised the flag to {@code true}. The stored flag is now kept, as the
 * other lookups already do.
 */
class LookupDisabledStaysDisabledTest {

    private static final Instant ADDED = Instant.parse("2024-01-01T00:00:00Z");

    @Test
    @DisplayName("a new entity no longer carries Active = true before it is saved")
    void noInitialiser() {
        assertThat(new StudyStatus().getIsActive()).isNull();
        assertThat(new StudentGov().getIsActive()).isNull();
        assertThat(new StudentGovMud().getIsActive()).isNull();
    }

    @Test
    @DisplayName("Study Status: a disabled row edited without the flag stays disabled")
    void studyStatus() {
        StudyStatusRepository repo = Mockito.mock(StudyStatusRepository.class);
        StudyStatus stored = new StudyStatus();
        stored.setIsActive(false);
        when(repo.findById(5)).thenReturn(Optional.of(stored));
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        StudyStatusService service = new StudyStatusService();
        ReflectionTestUtils.setField(service, "repository", repo);

        StudyStatus body = new StudyStatus();
        body.setStatusName("منتظم");
        body.setStatusNameEn("Regular");
        assertThat(service.update(5, body).getIsActive()).isFalse();

        StudyStatus enable = new StudyStatus();
        enable.setIsActive(true);
        assertThat(service.update(5, enable).getIsActive()).as("the flag, when sent, still wins").isTrue();
    }

    @Test
    @DisplayName("Governorate: the flag and the insert date are kept when the body leaves them out")
    void governorate() {
        StudentGovRepository repo = Mockito.mock(StudentGovRepository.class);
        StudentGov stored = new StudentGov();
        stored.setIsActive(false);
        stored.setStudentMidate(ADDED);
        when(repo.findById(7)).thenReturn(Optional.of(stored));
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        StudentGovService service = new StudentGovService();
        ReflectionTestUtils.setField(service, "repository", repo);

        StudentGov body = new StudentGov();
        body.setStudentGovName("صنعاء");
        StudentGov saved = service.update(7, body);
        assertThat(saved.getIsActive()).isFalse();
        assertThat(saved.getStudentMidate()).isEqualTo(ADDED);
    }

    @Test
    @DisplayName("Municipality: a disabled row edited without the flag stays disabled")
    void municipality() {
        StudentGovMudRepository repo = Mockito.mock(StudentGovMudRepository.class);
        StudentGovMud stored = new StudentGovMud();
        stored.setIsActive(false);
        when(repo.findById(9)).thenReturn(Optional.of(stored));
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        StudentGovMudService service = new StudentGovMudService();
        ReflectionTestUtils.setField(service, "repository", repo);

        StudentGovMud body = new StudentGovMud();
        body.setStudentMudName("مديرية");
        assertThat(service.update(9, body).getIsActive()).isFalse();
    }
}
