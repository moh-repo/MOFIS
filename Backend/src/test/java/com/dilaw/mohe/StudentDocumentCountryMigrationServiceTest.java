package com.dilaw.mohe;

import com.dilaw.mohe.model.StudentDatum;
import com.dilaw.mohe.model.StudentDocument;
import com.dilaw.mohe.model.StudentDocumentHistory;
import com.dilaw.mohe.repository.StudentDataRepository;
import com.dilaw.mohe.repository.StudentDocumentHistoryRepository;
import com.dilaw.mohe.repository.StudentDocumentRepository;
import com.dilaw.mohe.service.StudentDocumentCountryMigrationService;
import com.dilaw.mohe.service.StudentFileStorageService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The one-off move of pre-country-folder uploads: what it moves, what it leaves, and
 * that running it twice is not running it twice.
 *
 * <p>Repositories are mocked rather than hit — the entities are returned by reference,
 * so a repointed path is observable on the object the service was handed.
 */
class StudentDocumentCountryMigrationServiceTest {

    private static final int STUDENT = 42;
    private static final int EGYPT = 7;

    private StudentDatum student(Integer studyCountryIn, String picturePath) {
        StudentDatum s = new StudentDatum();
        s.setStudentId(STUDENT);
        s.setStudyCountryIn(studyCountryIn);
        s.setPicturePath(picturePath);
        return s;
    }

    private StudentDocument document(String filePath) {
        StudentDocument d = new StudentDocument();
        d.setStudentId(STUDENT);
        d.setFilePath(filePath);
        return d;
    }

    private StudentDocumentHistory history(String filePath) {
        StudentDocumentHistory h = new StudentDocumentHistory();
        h.setStudentId(STUDENT);
        h.setFilePath(filePath);
        return h;
    }

    /** A legacy file on disk at uploads/students/documents/student_42/{tail}. */
    private void legacyFile(Path root, String tail) throws Exception {
        Path p = root.resolve("students/documents/student_" + STUDENT).resolve(tail);
        Files.createDirectories(p.getParent());
        Files.writeString(p, "content-of-" + tail);
    }

    private record Fixture(StudentDocumentCountryMigrationService service,
                           StudentFileStorageService storage) {}

    private Fixture fixture(Path root, StudentDatum s, List<StudentDocument> docs,
            List<StudentDocumentHistory> hist) {
        StudentFileStorageService storage = new StudentFileStorageService(root.toString());
        StudentDataRepository students = mock(StudentDataRepository.class);
        StudentDocumentRepository documents = mock(StudentDocumentRepository.class);
        StudentDocumentHistoryRepository histories = mock(StudentDocumentHistoryRepository.class);

        when(students.findAll()).thenReturn(List.of(s));
        when(students.findById(STUDENT)).thenReturn(Optional.of(s));
        when(students.save(any(StudentDatum.class))).thenAnswer(i -> i.getArgument(0));
        when(documents.findByStudentId(STUDENT)).thenReturn(docs);
        when(documents.save(any(StudentDocument.class))).thenAnswer(i -> i.getArgument(0));
        when(histories.findByStudentIdOrderByChangedAtDesc(STUDENT)).thenReturn(hist);
        when(histories.save(any(StudentDocumentHistory.class))).thenAnswer(i -> i.getArgument(0));

        return new Fixture(
                new StudentDocumentCountryMigrationService(storage, students, documents, histories),
                storage);
    }

    @Test
    @DisplayName("A legacy student's files and all three path columns move to their country")
    void migratesFilesAndRepointsEveryColumn(@TempDir Path root) throws Exception {
        legacyFile(root, "20260101/passport.pdf");
        legacyFile(root, "history/20260101_old.pdf");
        legacyFile(root, "20260101/photo.png");

        StudentDatum s = student(EGYPT, "student_42/20260101/photo.png");
        StudentDocument doc = document("student_42/20260101/passport.pdf");
        StudentDocumentHistory h = history("student_42/history/20260101_old.pdf");
        Fixture f = fixture(root, s, List.of(doc), List.of(h));

        Map<String, Object> result = f.service().migrateAll();

        assertThat(result.get("success")).isEqualTo(true);
        assertThat(result.get("studentsMoved")).isEqualTo(1);

        String base = "countries/country_7/students/documents/student_42";
        assertThat(doc.getFilePath()).isEqualTo(base + "/20260101/passport.pdf");
        assertThat(h.getFilePath()).isEqualTo(base + "/history/20260101_old.pdf");
        assertThat(s.getPicturePath()).isEqualTo(base + "/20260101/photo.png");

        // The files followed their rows.
        assertThat(Files.readString(f.storage().resolveStored(doc.getFilePath())))
                .isEqualTo("content-of-20260101/passport.pdf");
        assertThat(Files.exists(root.resolve("students/documents/student_42/20260101/passport.pdf"))).isFalse();
    }

    @Test
    @DisplayName("A student with no country lands in _unassigned rather than a shared country_0")
    void migratesCountrylessStudentToUnassigned(@TempDir Path root) throws Exception {
        legacyFile(root, "20260101/passport.pdf");

        StudentDatum s = student(null, null);
        StudentDocument doc = document("student_42/20260101/passport.pdf");
        Fixture f = fixture(root, s, List.of(doc), List.of());

        f.service().migrateAll();

        assertThat(doc.getFilePath())
                .isEqualTo("countries/_unassigned/students/documents/student_42/20260101/passport.pdf");
    }

    @Test
    @DisplayName("Running it again moves nothing — an already-migrated student is skipped")
    void isIdempotent(@TempDir Path root) throws Exception {
        legacyFile(root, "20260101/passport.pdf");

        StudentDatum s = student(EGYPT, null);
        StudentDocument doc = document("student_42/20260101/passport.pdf");
        Fixture f = fixture(root, s, List.of(doc), List.of());

        assertThat(f.service().migrateAll().get("studentsMoved")).isEqualTo(1);
        String afterFirst = doc.getFilePath();

        Map<String, Object> second = f.service().migrateAll();

        assertThat(second.get("studentsMoved")).isEqualTo(0);
        assertThat(second.get("studentsSkipped")).isEqualTo(1);
        assertThat(doc.getFilePath()).isEqualTo(afterFirst);
    }

    @Test
    @DisplayName("A country change carries the student's files and rows to the new country")
    void relocatesOnCountryChange(@TempDir Path root) throws Exception {
        StudentFileStorageService probe = new StudentFileStorageService(root.toString());
        Path from = probe.studentDir(STUDENT, EGYPT);
        Files.createDirectories(from.resolve("20260101"));
        Files.writeString(from.resolve("20260101/passport.pdf"), "doc");

        StudentDatum s = student(9, null);
        StudentDocument doc = document("countries/country_7/students/documents/student_42/20260101/passport.pdf");
        Fixture f = fixture(root, s, List.of(doc), List.of());

        boolean changed = f.service().relocateForCountryChange(STUDENT, EGYPT, 9);

        assertThat(changed).isTrue();
        assertThat(doc.getFilePath())
                .isEqualTo("countries/country_9/students/documents/student_42/20260101/passport.pdf");
        assertThat(Files.readString(f.storage().resolveStored(doc.getFilePath()))).isEqualTo("doc");
    }

    @Test
    @DisplayName("A country that did not actually change is not a move")
    void sameCountryIsANoOp(@TempDir Path root) {
        Fixture f = fixture(root, student(EGYPT, null), List.of(), List.of());

        assertThat(f.service().relocateForCountryChange(STUDENT, EGYPT, EGYPT)).isFalse();
    }
}
