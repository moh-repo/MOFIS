package com.dilaw.mohe.service;

import com.dilaw.mohe.model.StudentFinance;
import com.dilaw.mohe.model.StudentFinanceArch;
import com.dilaw.mohe.repository.StudentFinanceArchRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * STUDENT_FINANCE_ARCH is unique on (student, year, quarter) since V038. The quarter
 * close used to insert blindly and, because the archive already held the quarter —
 * from the historical 2024 load, or from quarter create having archived it first —
 * the close died on that index with a 500. A snapshot has to be an upsert.
 */
@ExtendWith(MockitoExtension.class)
class StudentFinanceArchiveServiceTest {

    @Mock
    private StudentFinanceArchRepository archRepository;

    @InjectMocks
    private StudentFinanceArchiveService service;

    private StudentFinance live;

    @BeforeEach
    void setUp() {
        live = new StudentFinance();
        live.setId(1);
        live.setStudentFinName("Student One");
        live.setStudentFinRecievedYear(2024);
        live.setStudentFinRecievedMonth(2);
        live.setStudentFinRecievedYearMonth(20242);
        live.setStudentFinRecievedAmount(1500);
        live.setWafarAmount(100);
        org.mockito.Mockito.lenient().when(archRepository.save(any(StudentFinanceArch.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    @DisplayName("no archive row for the quarter yet: a new row is inserted")
    void insertsWhenAbsent() {
        when(archRepository.findByStudentIdAndStudentFinRecievedYearAndStudentFinRecievedMonth(1, 2024, 2))
                .thenReturn(Optional.empty());

        StudentFinanceArchiveService.Result r = service.archive(live, 3);

        assertThat(r.refreshed()).isFalse();
        assertThat(r.row().getFinanceArchEntryId()).isNull();
        assertThat(r.row().getStudentId()).isEqualTo(1);
        assertThat(r.row().getStudentFinRecievedYear()).isEqualTo(2024);
        assertThat(r.row().getStudentFinRecievedMonth()).isEqualTo(2);
        assertThat(r.row().getStudentFinRecievedAmount()).isEqualTo(1500);
        assertThat(r.row().getMoheQuarterId()).isEqualTo(3);
        assertThat(r.row().getArchievedDate())
                .as("R5-23: stamped on insert too; the column default never fired, Hibernate sent NULL").isNotNull();
    }

    @Test
    @DisplayName("R5-23: any insert of an archive row is stamped, whoever builds it; a set date is kept")
    void entityStampsOnInsert() {
        StudentFinanceArch built = new StudentFinanceArch();
        built.stampArchiveDate();
        assertThat(built.getArchievedDate()).isNotNull();

        java.time.Instant earlier = java.time.Instant.parse("2026-09-01T00:00:00Z");
        StudentFinanceArch dated = new StudentFinanceArch();
        dated.setArchievedDate(earlier);
        dated.stampArchiveDate();
        assertThat(dated.getArchievedDate()).isEqualTo(earlier);
    }

    @Test
    @DisplayName("archive row already exists: it is refreshed in place, keeping its entry id")
    void refreshesWhenPresent() {
        StudentFinanceArch existing = new StudentFinanceArch();
        existing.setFinanceArchEntryId(777L);
        existing.setStudentId(1);
        existing.setStudentFinRecievedYear(2024);
        existing.setStudentFinRecievedMonth(2);
        existing.setStudentFinRecievedAmount(900);       // stale
        when(archRepository.findByStudentIdAndStudentFinRecievedYearAndStudentFinRecievedMonth(1, 2024, 2))
                .thenReturn(Optional.of(existing));

        StudentFinanceArchiveService.Result r = service.archive(live, 3);

        ArgumentCaptor<StudentFinanceArch> saved = ArgumentCaptor.forClass(StudentFinanceArch.class);
        verify(archRepository).save(saved.capture());
        assertThat(saved.getValue()).isSameAs(existing);
        assertThat(r.refreshed()).isTrue();
        assertThat(existing.getFinanceArchEntryId()).isEqualTo(777L);
        assertThat(existing.getStudentFinRecievedAmount()).isEqualTo(1500);
        assertThat(existing.getWafarAmount()).isEqualTo(100);
        assertThat(existing.getMoheQuarterId()).isEqualTo(3);
        assertThat(existing.getArchievedDate()).as("a refresh records when it happened").isNotNull();
    }

    @Test
    @DisplayName("a null quarter id (quarter create, live row unstamped) does not erase an existing stamp")
    void nullQuarterIdKeepsExistingStamp() {
        StudentFinanceArch existing = new StudentFinanceArch();
        existing.setFinanceArchEntryId(778L);
        existing.setMoheQuarterId(3);
        when(archRepository.findByStudentIdAndStudentFinRecievedYearAndStudentFinRecievedMonth(1, 2024, 2))
                .thenReturn(Optional.of(existing));

        service.archive(live, null);

        assertThat(existing.getMoheQuarterId()).isEqualTo(3);
    }

    @Test
    @DisplayName("a live row with no year/quarter cannot be keyed: no lookup, plain insert as before")
    void unkeyedRowSkipsLookup() {
        live.setStudentFinRecievedMonth(null);

        StudentFinanceArchiveService.Result r = service.archive(live, 3);

        verify(archRepository, never())
                .findByStudentIdAndStudentFinRecievedYearAndStudentFinRecievedMonth(anyInt(), any(), any());
        assertThat(r.refreshed()).isFalse();
    }
}
