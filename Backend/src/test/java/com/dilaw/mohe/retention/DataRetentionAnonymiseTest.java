package com.dilaw.mohe.retention;

import com.dilaw.mohe.security.SecurityEventLog;
import com.dilaw.mohe.service.StudentFileStorageService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * System test PRT-01, PRT-02, PRT-09 without a database: which date the candidates are
 * measured from, what an anonymised student loses, and that each student is one
 * transaction whose files go only after it commits.
 */
class DataRetentionAnonymiseTest {

    /** A transaction manager with no resource: counts commits and rollbacks, runs synchronisations. */
    static final class CountingTxManager extends AbstractPlatformTransactionManager {
        int commits;
        int rollbacks;

        @Override protected Object doGetTransaction() { return new Object(); }
        @Override protected void doBegin(Object transaction, TransactionDefinition definition) { }
        @Override protected void doCommit(DefaultTransactionStatus status) { commits++; }
        @Override protected void doRollback(DefaultTransactionStatus status) { rollbacks++; }
    }

    @TempDir Path dir;

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final StudentFileStorageService storage = mock(StudentFileStorageService.class);
    private final CountingTxManager tx = new CountingTxManager();
    private DataRetentionJob job;

    @BeforeEach
    void setUp() {
        job = new DataRetentionJob(jdbc, new ObjectMapper(), mock(SecurityEventLog.class), storage, tx,
                true, true, 90, 7, "WITHDRAWN,TRANSFERRED,DECEASED", dir.resolve("reports").toString());
        when(storage.resolveStored(anyString())).thenAnswer(inv -> dir.resolve(inv.getArgument(0, String.class)));
        when(jdbc.queryForObject(startsWith("SELECT OBJECT_ID"), eq(Integer.class))).thenReturn(4242);
    }

    private Path file(String name) throws Exception {
        return Files.writeString(dir.resolve(name), "x");
    }

    private void filesOf(int id, String doc, String history, String photo) {
        when(jdbc.queryForList(eq("SELECT FILE_PATH FROM MOHECORE.STUDENT_DOCUMENTS WHERE STUDENT_ID = ?"), eq(String.class), eq(id)))
                .thenReturn(List.of(doc));
        when(jdbc.queryForList(eq("SELECT FILE_PATH FROM MOHECORE.STUDENT_DOCUMENT_HISTORY WHERE STUDENT_ID = ?"), eq(String.class), eq(id)))
                .thenReturn(List.of(history));
        when(jdbc.queryForList(eq("SELECT PICTURE_PATH FROM MOHECORE.STUDENT_DATA WHERE STUDENT_ID = ?"), eq(String.class), eq(id)))
                .thenReturn(java.util.Collections.singletonList(photo));
    }

    @Test
    @DisplayName("PRT-01: candidates are measured from the recorded status date, never from STUDENT_MIDATE")
    void candidatesFromStatusDate() {
        LocalDateTime cutoff = LocalDateTime.of(2019, 9, 28, 0, 0);
        job.studentCandidates(cutoff);
        verify(jdbc).queryForList(argThat((String sql) -> sql.contains("JOIN MOHECORE.STUDENT_STUDY_STATUS_CHANGE c")
                        && sql.contains("c.STUDENT_STUDY_STATUS_ID = d.STUDENT_STUDY_STATUS_ID")
                        && sql.contains("c.CHANGED_AT < ?")
                        && !sql.contains("STUDENT_MIDATE")),
                eq(Integer.class), any(Object[].class));
    }

    @Test
    @DisplayName("PRT-01: without the status-date table (V150 not applied) nobody is a candidate")
    void noTableNoCandidates() {
        when(jdbc.queryForObject(startsWith("SELECT OBJECT_ID"), eq(Integer.class))).thenReturn(null);
        assertThat(job.studentCandidates(LocalDateTime.now())).isEmpty();
        verify(jdbc, never()).queryForList(contains("STUDENT_DATA d"), eq(Integer.class), any(Object[].class));
    }

    @Test
    @DisplayName("PRT-02/09: applied, a student's documents, earlier versions and photo are deleted after the commit, and the free text is scrubbed")
    void anonymisesEverythingAndDeletesAfterCommit() throws Exception {
        Path doc = file("doc.enc");
        Path old = file("doc-v1.enc");
        Path photo = file("photo.enc");
        filesOf(7, "doc.enc", "doc-v1.enc", "photo.enc");
        when(jdbc.queryForList(contains("STUDENT_STUDY_STATUS_CHANGE"), eq(Integer.class), any(Object[].class)))
                .thenReturn(List.of(7));

        DataRetentionJob.Report report = job.run(LocalDateTime.now(), false);

        DataRetentionJob.ClassResult students = report.classes().stream()
                .filter(c -> c.dataClass().equals("student-records")).findFirst().orElseThrow();
        assertThat(students.affected()).isEqualTo(1);
        assertThat(students.note()).isNull();
        assertThat(tx.commits).isEqualTo(1);
        assertThat(doc).doesNotExist();
        assertThat(old).as("the earlier version").doesNotExist();
        assertThat(photo).as("the photo").doesNotExist();

        verify(jdbc).update(argThat((String sql) -> sql.contains("UPDATE MOHECORE.STUDENT_DATA SET") && sql.contains("PICTURE_PATH = NULL")),
                eq("Anonymised student #7"), eq(7));
        verify(jdbc).update(contains("STUDENTCORE.student_change_requests SET old_value = NULL"), eq(7));
        verify(jdbc).update(contains("s.StudentDataJson = NULL, s.StudentFinanceJson = NULL"), eq(7));
        verify(jdbc).update(contains("MOHECORE.StudentInfoRequest SET ResponseText = NULL"), eq(7));
        verify(jdbc).update(contains("MOHECORE.StudentInquiry SET Title = N'Anonymised', Description = NULL"), eq(7));
        verify(jdbc).update(contains("STUDENT_DOCUMENT_HISTORY SET ORIGINAL_FILENAME = NULL, FILE_PATH = 'retired'"), eq(7));
    }

    @Test
    @DisplayName("PRT-09: a failure part-way through one student rolls that student back, keeps their files, and the next student still goes")
    void failureRollsBackOneStudentOnly() throws Exception {
        Path keep = file("keep.enc");
        Path gone = file("gone.enc");
        filesOf(8, "keep.enc", "keep.enc", null);
        filesOf(9, "gone.enc", "gone.enc", null);
        when(jdbc.queryForList(contains("STUDENT_STUDY_STATUS_CHANGE"), eq(Integer.class), any(Object[].class)))
                .thenReturn(List.of(8, 9));
        when(jdbc.update(contains("MOHECORE.StudentInquiry SET"), eq(8)))
                .thenThrow(new org.springframework.dao.DataAccessResourceFailureException("connection lost"));

        DataRetentionJob.Report report = job.run(LocalDateTime.now(), false);

        DataRetentionJob.ClassResult students = report.classes().stream()
                .filter(c -> c.dataClass().equals("student-records")).findFirst().orElseThrow();
        assertThat(students.candidates()).isEqualTo(2);
        assertThat(students.affected()).isEqualTo(1);
        assertThat(students.note()).contains("student 8").contains("connection lost");
        assertThat(tx.rollbacks).isEqualTo(1);
        assertThat(tx.commits).isEqualTo(1);
        assertThat(keep).as("the failed student's file is kept with their rows").exists();
        assertThat(gone).doesNotExist();
    }

    @Test
    @DisplayName("a dry run counts and changes nothing")
    void dryRunTouchesNothing() throws Exception {
        Path doc = file("dry.enc");
        filesOf(5, "dry.enc", "dry.enc", null);
        when(jdbc.queryForList(contains("STUDENT_STUDY_STATUS_CHANGE"), eq(Integer.class), any(Object[].class)))
                .thenReturn(List.of(5));

        DataRetentionJob.Report report = job.run(LocalDateTime.now(), true);

        assertThat(report.studentIds()).containsExactly(5);
        assertThat(report.totalAffected()).isZero();
        assertThat(doc).exists();
        verify(jdbc, never()).update(contains("UPDATE MOHECORE.STUDENT_DATA SET"), any(Object[].class));
    }
}
