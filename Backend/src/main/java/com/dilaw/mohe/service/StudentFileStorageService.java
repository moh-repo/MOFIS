package com.dilaw.mohe.service;

import com.dilaw.mohe.util.SafePathUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Where a student's uploaded files live.
 *
 * <pre>
 *   uploads/countries/country_{studyCountryIn}/students/documents/student_{id}/{yyyyMMdd}/{file}
 *   uploads/countries/country_{studyCountryIn}/students/documents/student_{id}/history/{file}
 * </pre>
 *
 * <p>The country folder is the point, for the same reason it is in
 * {@link CountryFileStorageService}: an attaché's data has to be locatable, backed
 * up, handed over or removed as one thing, without a query. Beneath it the layout is
 * exactly what it has always been — {@code student_{id}} then a date or {@code history}
 * folder — so nothing about how a single file is found had to change.
 *
 * <p>The country is {@code STUDY_COUNTRY_IN}, the tenant key the rest of the system
 * scopes on, not the student's nationality: the folder should line up with who is
 * permitted to see it.
 *
 * <h2>What is stored in the database</h2>
 *
 * <p>Paths are recorded relative to the <em>uploads root</em>, so
 * {@code countries/country_7/students/documents/student_42/20260824/passport.pdf}
 * rather than the old {@code student_42/20260824/passport.pdf}. A path that names its
 * own country is what lets a student move between attachés without stranding files:
 * the row still points at the file wherever it currently sits.
 *
 * <p>{@link #resolveStored} therefore accepts both shapes — anything that does not
 * begin with {@code countries/} is read as a pre-migration path and resolved under the
 * old root. That fallback is deliberate and permanent: a file restored from an old
 * backup must still open.
 */
@Service
public class StudentFileStorageService {

    /** Folder under the uploads root that every country folder sits in. */
    public static final String COUNTRIES_ROOT = CountryFileStorageService.COUNTRIES_ROOT;

    /** Section beneath a country folder, mirroring the pre-migration layout. */
    public static final String SECTION_STUDENT_DOCUMENTS = "students/documents";

    /**
     * Country folder for a student who has no {@code STUDY_COUNTRY_IN} yet.
     *
     * <p>Named rather than {@code country_0} so it can never be mistaken for a real
     * country id, and so a query for "whose files are these?" has an honest answer.
     */
    public static final String UNASSIGNED_COUNTRY = "_unassigned";

    /** Where every student document lived before country folders existed. */
    private static final String LEGACY_SECTION = "students/documents";

    private final Path uploadRoot;

    public StudentFileStorageService(@Value("${file.upload-dir:uploads/}") String uploadDir) {
        this.uploadRoot = Paths.get(uploadDir).toAbsolutePath().normalize();
    }

    /** The uploads root every stored path is relative to. */
    public Path getUploadRoot() {
        return uploadRoot;
    }

    /** The pre-migration root, kept so old paths and old backups still resolve. */
    public Path getLegacyRoot() {
        return uploadRoot.resolve(LEGACY_SECTION).normalize();
    }

    /** {@code country_7}, or {@code _unassigned} when the student has no country yet. */
    public String countryFolderName(Integer studyCountryIn) {
        return (studyCountryIn == null || studyCountryIn <= 0)
                ? UNASSIGNED_COUNTRY
                : "country_" + studyCountryIn;
    }

    /** A country's student-documents directory, whether or not it exists yet. */
    public Path countryDocumentsDir(Integer studyCountryIn) {
        return uploadRoot
                .resolve(COUNTRIES_ROOT)
                .resolve(countryFolderName(studyCountryIn))
                .resolve(SECTION_STUDENT_DOCUMENTS)
                .normalize();
    }

    /**
     * The directory holding one student's files, created if it is not there yet.
     *
     * @throws IllegalArgumentException if the student id is not a positive number — a
     *         null id would otherwise produce a shared {@code student_null} folder that
     *         every student writes into.
     */
    public Path studentDir(Integer studentId, Integer studyCountryIn) throws IOException {
        Path dir = studentDirPath(studentId, studyCountryIn);
        Files.createDirectories(dir);
        return dir;
    }

    /** As {@link #studentDir} but without touching the filesystem. */
    public Path studentDirPath(Integer studentId, Integer studyCountryIn) {
        if (studentId == null || studentId <= 0) {
            throw new IllegalArgumentException("A student is required before a file can be stored.");
        }
        return countryDocumentsDir(studyCountryIn).resolve("student_" + studentId).normalize();
    }

    /** The path as it should be recorded in the database: relative to the uploads root, forward slashes. */
    public String relativise(Path storedFile) {
        return uploadRoot.relativize(storedFile.toAbsolutePath().normalize())
                .toString().replace('\\', '/');
    }

    /** True when a stored path already names its country folder. */
    public boolean isCountryScoped(String storedPath) {
        return storedPath != null && storedPath.replace('\\', '/').startsWith(COUNTRIES_ROOT + "/");
    }

    /**
     * The file a stored path points at, whichever layout it was written in.
     *
     * <p>Both branches go through {@link SafePathUtils#resolveUnderRoot}, so a path
     * carrying {@code ../} is still refused rather than escaping the uploads tree.
     */
    public Path resolveStored(String storedPath) {
        return isCountryScoped(storedPath)
                ? SafePathUtils.resolveUnderRoot(uploadRoot, storedPath)
                : SafePathUtils.resolveUnderRoot(getLegacyRoot(), storedPath);
    }

    /**
     * Moves a student's whole folder into another country and returns the new folder's
     * uploads-root-relative path, or null when there was nothing on disk to move.
     *
     * <p>Used both by the one-off migration and by a student changing country. The move
     * is per-file rather than a directory rename because the destination may already
     * exist — a student whose files were partly migrated, or who is moving back to a
     * country they were in before.
     */
    public String moveStudentTree(Integer studentId, Integer fromCountry, Integer toCountry) throws IOException {
        Path source = studentDirPath(studentId, fromCountry);
        return moveStudentTreeFrom(source, studentId, toCountry);
    }

    /** As {@link #moveStudentTree}, for a source folder that is not itself country-scoped. */
    public String moveStudentTreeFrom(Path source, Integer studentId, Integer toCountry) throws IOException {
        Path target = studentDirPath(studentId, toCountry);
        if (source.equals(target) || !Files.isDirectory(source)) {
            return null;
        }
        Files.createDirectories(target);

        try (Stream<Path> walk = Files.walk(source)) {
            for (Path from : walk.filter(Files::isRegularFile).toList()) {
                Path to = target.resolve(source.relativize(from));
                Files.createDirectories(to.getParent());
                Files.move(from, to, StandardCopyOption.REPLACE_EXISTING);
            }
        }
        deleteEmptyTree(source);
        return relativise(target);
    }

    /** The student's folder within the legacy tree — the migration's source. */
    public Path legacyStudentDir(Integer studentId) {
        if (studentId == null || studentId <= 0) {
            throw new IllegalArgumentException("A student is required.");
        }
        return getLegacyRoot().resolve("student_" + studentId).normalize();
    }

    /**
     * Every legacy folder belonging to a student.
     *
     * <p>Before the folder name was keyed on the id alone it carried the student's name
     * too — {@code student_1_دعاء_علي_عبده_سعد_الجرادي} sits in this database next to
     * {@code student_1}, and both hold live files. A migration that looked only for
     * {@code student_{id}} would move half a student's documents and leave the rest
     * behind, with rows pointing into a folder nothing maintains any more.
     */
    public List<Path> legacyStudentDirs(Integer studentId) throws IOException {
        Path root = getLegacyRoot();
        if (!Files.isDirectory(root)) {
            return List.of();
        }
        List<Path> dirs = new ArrayList<>();
        try (Stream<Path> children = Files.list(root)) {
            children.filter(Files::isDirectory)
                    .filter(p -> isStudentFolderName(p.getFileName().toString(), studentId))
                    .sorted()
                    .forEach(dirs::add);
        }
        return dirs;
    }

    /**
     * True for {@code student_42} and for {@code student_42_some name}, false for
     * {@code student_420} — the underscore is what stops one id from swallowing another.
     */
    public static boolean isStudentFolderName(String folderName, Integer studentId) {
        String exact = "student_" + studentId;
        return folderName.equals(exact) || folderName.startsWith(exact + "_");
    }

    /**
     * Rewrites one stored path so it points inside {@code newStudentRelDir}, keeping
     * whatever sits below {@code student_{id}} — the date or {@code history} folder and
     * the filename — exactly as it was.
     *
     * @return the rewritten path, or the original when it does not name this student's
     *         folder (so a row pointing somewhere unexpected is left alone rather than
     *         silently repointed at a file that is not it).
     */
    public String repoint(String storedPath, Integer studentId, String newStudentRelDir) {
        if (storedPath == null || storedPath.isBlank() || newStudentRelDir == null) {
            return storedPath;
        }
        String[] segments = storedPath.replace('\\', '/').split("/");
        for (int i = 0; i < segments.length - 1; i++) {
            if (isStudentFolderName(segments[i], studentId)) {
                String tail = String.join("/", Arrays.copyOfRange(segments, i + 1, segments.length));
                return newStudentRelDir + "/" + tail;
            }
        }
        return storedPath;
    }

    /** Removes the emptied source folder, leaving anything that was not moved in place. */
    private static void deleteEmptyTree(Path root) throws IOException {
        if (!Files.isDirectory(root)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(root)) {
            for (Path p : walk.sorted(Comparator.reverseOrder()).toList()) {
                if (Files.isDirectory(p)) {
                    try (Stream<Path> children = Files.list(p)) {
                        if (children.findAny().isEmpty()) {
                            Files.deleteIfExists(p);
                        }
                    }
                }
            }
        }
    }
}
