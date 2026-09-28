package com.dilaw.mohe.service;

import com.dilaw.mohe.model.StudentDataArch;
import com.dilaw.mohe.model.StudentDatum;
import com.dilaw.mohe.repository.StudentDataArchRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Snapshotting a student into STUDENT_DATA_ARCH exactly once per period.
 *
 * <p>Two flows write this table — quarter create archives the period being left, quarter
 * close takes a final snapshot of the quarter being closed — and either can run after
 * the other. The finance archive has upserted on (student, year, quarter) since V038
 * gave it a unique index; this table has no index and the close inserted
 * unconditionally, so closing a quarter that create had already archived wrote a second
 * copy of all 941 students. The table already holds 1,882 rows keyed by
 * (STUDENT_ID, CURRENT_QUARTER) — 941 at 20241 and 941 at 20242 — which is the key
 * enforced here.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StudentDataArchiveServiceTest {

    @Mock private StudentDataArchRepository studentDataArchRepository;

    @InjectMocks private StudentDataArchiveService service;

    private StudentDatum student;

    @BeforeEach
    void setUp() {
        student = new StudentDatum();
        student.setStudentId(1);
        student.setStudentName("Student One");
        student.setCurrentQuarter("20253");
        when(studentDataArchRepository.save(any(StudentDataArch.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    @DisplayName("no snapshot for the period yet: one is inserted")
    void firstSnapshotIsInserted() {
        when(studentDataArchRepository.findFirstByStudentIdAndCurrentQuarterOrderByIdAsc(1, "20253"))
                .thenReturn(Optional.empty());

        StudentDataArchiveService.Result result = service.archive(student, 16, null);

        assertThat(result.refreshed()).isFalse();
        assertThat(result.row().getId()).as("a new row, for the identity column to fill").isNull();
        assertThat(result.row().getCurrentQuarter()).isEqualTo("20253");
        assertThat(result.row().getStudentName()).isEqualTo("Student One");
    }

    @Test
    @DisplayName("the same student and period again: the row is refreshed in place, not duplicated")
    void secondSnapshotRefreshesInPlace() {
        StudentDataArch existing = new StudentDataArch();
        existing.setId(942);
        existing.setStudentId(1);
        existing.setCurrentQuarter("20253");
        existing.setStudentName("Stale Name");
        when(studentDataArchRepository.findFirstByStudentIdAndCurrentQuarterOrderByIdAsc(1, "20253"))
                .thenReturn(Optional.of(existing));

        StudentDataArchiveService.Result result = service.archive(student, 16, null);

        assertThat(result.refreshed()).isTrue();
        assertThat(result.row().getId()).as("keeps its id: the same row, written again").isEqualTo(942);
        assertThat(result.row().getStudentName()).isEqualTo("Student One");
    }

    @Test
    @DisplayName("a refresh keeps the date the snapshot first landed")
    void refreshKeepsTheOriginalInsertedDate() {
        Instant firstLanded = Instant.parse("2026-07-24T15:45:06Z");
        StudentDataArch existing = new StudentDataArch();
        existing.setId(942);
        existing.setInsertedToArchDate(firstLanded);
        when(studentDataArchRepository.findFirstByStudentIdAndCurrentQuarterOrderByIdAsc(1, "20253"))
                .thenReturn(Optional.of(existing));

        StudentDataArchiveService.Result result = service.archive(student, 16, null);

        // INSERTED_TO_ARCH_DATE says when the snapshot first landed; a later refresh
        // does not change that. ARCHIVE_DATE is the one that moves.
        assertThat(result.row().getInsertedToArchDate()).isEqualTo(firstLanded);
        assertThat(result.row().getArchiveDate()).isAfter(firstLanded);
    }

    @Test
    @DisplayName("a student with no period recorded is archived rather than skipped")
    void noPeriodStillArchives() {
        student.setCurrentQuarter(null);

        StudentDataArchiveService.Result result = service.archive(student, 16, null);

        // Nothing to key on, so the duplicate cannot be detected. A second snapshot of
        // a student whose period is not recorded is the lesser of the two problems.
        assertThat(result.refreshed()).isFalse();
        assertThat(result.row().getStudentId()).isEqualTo(1);
    }

    @Test
    @DisplayName("the profile snapshot travels with the row")
    void profileSnapshotIsCarried() {
        when(studentDataArchRepository.findFirstByStudentIdAndCurrentQuarterOrderByIdAsc(1, "20253"))
                .thenReturn(Optional.empty());

        StudentDataArchiveService.Result result = service.archive(student, 16, "{\"id\":1}");

        assertThat(result.row().getProfileSnapshot()).isEqualTo("{\"id\":1}");
        assertThat(result.row().getQuerterIs()).isEqualTo(16);
    }
}
