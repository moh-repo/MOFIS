package com.dilaw.mohe;

import com.dilaw.mohe.service.StudentFileStorageService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.web.server.ResponseStatusException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Where a student's uploads land, what happens when they change country, and what
 * stops a stored path from reaching outside the uploads tree.
 */
class StudentFileStorageServiceTest {

    private StudentFileStorageService service(Path root) {
        return new StudentFileStorageService(root.toString());
    }

    private String rel(Path root, Path p) {
        return root.relativize(p).toString().replace('\\', '/');
    }

    @Test
    @DisplayName("A student's folder sits under their country, then the existing layout")
    void buildsCountryScopedStudentPath(@TempDir Path root) throws Exception {
        Path dir = service(root).studentDir(42, 7);

        assertThat(rel(root, dir)).isEqualTo("countries/country_7/students/documents/student_42");
        assertThat(Files.isDirectory(dir)).isTrue();
    }

    @Test
    @DisplayName("Two countries never share a student folder")
    void countriesAreSeparate(@TempDir Path root) throws Exception {
        StudentFileStorageService s = service(root);

        assertThat(s.studentDirPath(42, 7)).isNotEqualTo(s.studentDirPath(42, 8));
    }

    @Test
    @DisplayName("A student with no country goes to _unassigned, not a country_0 everyone shares")
    void unassignedCountryGetsItsOwnFolder(@TempDir Path root) throws Exception {
        StudentFileStorageService s = service(root);

        assertThat(rel(root, s.studentDirPath(42, null)))
                .isEqualTo("countries/_unassigned/students/documents/student_42");
        assertThat(rel(root, s.studentDirPath(42, 0)))
                .isEqualTo("countries/_unassigned/students/documents/student_42");
    }

    @Test
    @DisplayName("A stored path is relative to the uploads root and round-trips back to the file")
    void relativiseRoundTrips(@TempDir Path root) throws Exception {
        StudentFileStorageService s = service(root);
        Path file = s.studentDir(42, 7).resolve("20260824").resolve("passport.pdf");
        Files.createDirectories(file.getParent());
        Files.writeString(file, "x");

        String stored = s.relativise(file);

        assertThat(stored).isEqualTo("countries/country_7/students/documents/student_42/20260824/passport.pdf");
        assertThat(s.resolveStored(stored)).isEqualTo(file);
    }

    @Test
    @DisplayName("A pre-migration path still resolves, under the old root")
    void legacyPathsStillResolve(@TempDir Path root) throws Exception {
        StudentFileStorageService s = service(root);
        Path legacy = root.resolve("students/documents/student_42/20260101/old.pdf");
        Files.createDirectories(legacy.getParent());
        Files.writeString(legacy, "x");

        assertThat(s.isCountryScoped("student_42/20260101/old.pdf")).isFalse();
        assertThat(s.resolveStored("student_42/20260101/old.pdf")).isEqualTo(legacy);
    }

    @Test
    @DisplayName("A stored path cannot climb out of the uploads tree")
    void rejectsTraversal(@TempDir Path root) {
        StudentFileStorageService s = service(root);

        assertThatThrownBy(() -> s.resolveStored("../../application.properties"))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> s.resolveStored("countries/../../secrets.txt"))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    @DisplayName("A student without an id would otherwise share one folder with every other")
    void requiresAStudentId(@TempDir Path root) {
        StudentFileStorageService s = service(root);

        assertThatThrownBy(() -> s.studentDirPath(null, 7))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> s.studentDirPath(0, 7))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Changing country moves the whole folder, date and history subfolders included")
    void movesStudentTreeBetweenCountries(@TempDir Path root) throws Exception {
        StudentFileStorageService s = service(root);
        Path from = s.studentDir(42, 7);
        Files.createDirectories(from.resolve("20260824"));
        Files.writeString(from.resolve("20260824/passport.pdf"), "doc");
        Files.createDirectories(from.resolve("history"));
        Files.writeString(from.resolve("history/20260101_old.pdf"), "old");

        String newRel = s.moveStudentTree(42, 7, 9);

        assertThat(newRel).isEqualTo("countries/country_9/students/documents/student_42");
        Path to = s.studentDirPath(42, 9);
        assertThat(Files.readString(to.resolve("20260824/passport.pdf"))).isEqualTo("doc");
        assertThat(Files.readString(to.resolve("history/20260101_old.pdf"))).isEqualTo("old");
        assertThat(Files.exists(from)).isFalse();
    }

    @Test
    @DisplayName("Moving a student who has no files on disk reports nothing moved")
    void moveWithNothingToMoveIsNotAnError(@TempDir Path root) throws Exception {
        assertThat(service(root).moveStudentTree(42, 7, 9)).isNull();
    }

    @Test
    @DisplayName("Repointing keeps everything below student_{id} exactly as it was")
    void repointPreservesTail(@TempDir Path root) {
        StudentFileStorageService s = service(root);
        String newDir = "countries/country_9/students/documents/student_42";

        assertThat(s.repoint("student_42/20260824/passport.pdf", 42, newDir))
                .isEqualTo(newDir + "/20260824/passport.pdf");
        assertThat(s.repoint("countries/country_7/students/documents/student_42/history/x.pdf", 42, newDir))
                .isEqualTo(newDir + "/history/x.pdf");
    }

    @Test
    @DisplayName("The older student_{id}_{name} folder convention is recognised as the student's")
    void recognisesNameSuffixedLegacyFolders(@TempDir Path root) throws Exception {
        StudentFileStorageService s = service(root);
        Path plain = root.resolve("students/documents/student_1/20260723");
        Path suffixed = root.resolve("students/documents/student_1_دعاء/20260723");
        Path other = root.resolve("students/documents/student_1001");
        for (Path p : List.of(plain, suffixed, other)) {
            Files.createDirectories(p);
        }

        List<Path> dirs = s.legacyStudentDirs(1);

        assertThat(dirs).hasSize(2);
        // student_1001 must not be swallowed by student_1.
        assertThat(dirs).noneMatch(d -> d.getFileName().toString().equals("student_1001"));
    }

    @Test
    @DisplayName("A path inside the name-suffixed folder repoints like any other")
    void repointsNameSuffixedPaths(@TempDir Path root) {
        StudentFileStorageService s = service(root);
        String newDir = "countries/country_1/students/documents/student_1";

        assertThat(s.repoint("student_1_دعاء/20260723/scan.jpeg", 1, newDir))
                .isEqualTo(newDir + "/20260723/scan.jpeg");
        // A different student whose id merely starts the same is untouched.
        assertThat(s.repoint("student_1001/doc.png", 1, newDir))
                .isEqualTo("student_1001/doc.png");
    }

    @Test
    @DisplayName("A path that is not this student's is left alone rather than silently repointed")
    void repointLeavesForeignPaths(@TempDir Path root) {
        StudentFileStorageService s = service(root);

        assertThat(s.repoint("student_99/20260824/passport.pdf", 42, "countries/country_9/students/documents/student_42"))
                .isEqualTo("student_99/20260824/passport.pdf");
    }
}
