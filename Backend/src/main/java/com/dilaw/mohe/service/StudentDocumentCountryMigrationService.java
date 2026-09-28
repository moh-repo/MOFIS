package com.dilaw.mohe.service;

import com.dilaw.mohe.model.StudentDatum;
import com.dilaw.mohe.model.StudentDocument;
import com.dilaw.mohe.model.StudentDocumentHistory;
import com.dilaw.mohe.repository.StudentDataRepository;
import com.dilaw.mohe.repository.StudentDocumentHistoryRepository;
import com.dilaw.mohe.repository.StudentDocumentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Moves student uploads that predate country folders into them.
 *
 * <p>Idempotent by construction: a student whose files already sit under
 * {@code countries/} is skipped, so the job can be re-run after a partial failure or
 * after restoring an old backup without moving anything twice.
 *
 * <p>Errors are collected per student rather than thrown. One student with a locked
 * file must not stop the other few thousand from being migrated.
 */
@Service
public class StudentDocumentCountryMigrationService {

    private static final Logger log = LoggerFactory.getLogger(StudentDocumentCountryMigrationService.class);

    private final StudentFileStorageService storage;
    private final StudentDataRepository studentDataRepository;
    private final StudentDocumentRepository documentRepository;
    private final StudentDocumentHistoryRepository historyRepository;

    public StudentDocumentCountryMigrationService(StudentFileStorageService storage,
            StudentDataRepository studentDataRepository,
            StudentDocumentRepository documentRepository,
            StudentDocumentHistoryRepository historyRepository) {
        this.storage = storage;
        this.studentDataRepository = studentDataRepository;
        this.documentRepository = documentRepository;
        this.historyRepository = historyRepository;
    }

    /** Migrates every student, reporting what moved and what did not. */
    @Transactional
    public Map<String, Object> migrateAll() {
        List<String> moved = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        for (StudentDatum student : studentDataRepository.findAll()) {
            Integer studentId = student.getStudentId();
            if (studentId == null) {
                continue;
            }
            try {
                if (migrateStudent(studentId, student.getStudyCountryIn(), student)) {
                    moved.add("student_" + studentId + " -> "
                            + storage.countryFolderName(student.getStudyCountryIn()));
                } else {
                    skipped.add("student_" + studentId);
                }
            } catch (Exception e) {
                log.warn("Could not migrate student {}: {}", studentId, e.getMessage());
                errors.add("student " + studentId + ": " + e.getMessage());
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("success", errors.isEmpty());
        result.put("studentsMoved", moved.size());
        result.put("studentsSkipped", skipped.size());
        result.put("moved", moved);
        result.put("errors", errors);
        return result;
    }

    /**
     * Moves one student's folder out of the legacy tree and repoints their rows.
     *
     * @return true when something was actually moved or repointed.
     */
    private boolean migrateStudent(Integer studentId, Integer studyCountryIn, StudentDatum student)
            throws java.io.IOException {
        // Both folder conventions, so a student split across student_1 and
        // student_1_{name} is migrated whole rather than half.
        List<Path> legacyDirs = storage.legacyStudentDirs(studentId);
        boolean hasLegacyFiles = !legacyDirs.isEmpty();

        List<StudentDocument> documents = documentRepository.findByStudentId(studentId);
        List<StudentDocumentHistory> history = historyRepository.findByStudentIdOrderByChangedAtDesc(studentId);
        boolean hasLegacyRows = documents.stream().anyMatch(d -> !storage.isCountryScoped(d.getFilePath()))
                || history.stream().anyMatch(h -> !storage.isCountryScoped(h.getFilePath()))
                || (student.getPicturePath() != null && !student.getPicturePath().isBlank()
                    && !storage.isCountryScoped(student.getPicturePath()));

        if (!hasLegacyFiles && !hasLegacyRows) {
            return false;
        }

        String newRelDir = storage.relativise(storage.studentDirPath(studentId, studyCountryIn));
        for (Path legacyDir : legacyDirs) {
            storage.moveStudentTreeFrom(legacyDir, studentId, studyCountryIn);
        }

        return repoint(studentId, newRelDir, documents, history, student);
    }

    /**
     * Rewrites the three columns that hold a student's file paths.
     *
     * <p>All three have to agree — a photo left pointing at the old tree while its
     * documents moved would show as a missing picture rather than an error.
     */
    private boolean repoint(Integer studentId, String newRelDir,
            List<StudentDocument> documents, List<StudentDocumentHistory> history, StudentDatum student) {
        if (newRelDir == null) {
            return false;
        }
        boolean changed = false;

        for (StudentDocument doc : documents) {
            String repointed = storage.repoint(doc.getFilePath(), studentId, newRelDir);
            if (!repointed.equals(doc.getFilePath())) {
                doc.setFilePath(repointed);
                documentRepository.save(doc);
                changed = true;
            }
        }
        for (StudentDocumentHistory h : history) {
            String repointed = storage.repoint(h.getFilePath(), studentId, newRelDir);
            if (!repointed.equals(h.getFilePath())) {
                h.setFilePath(repointed);
                historyRepository.save(h);
                changed = true;
            }
        }
        String picture = student.getPicturePath();
        if (picture != null && !picture.isBlank()) {
            String repointed = storage.repoint(picture, studentId, newRelDir);
            if (!repointed.equals(picture)) {
                student.setPicturePath(repointed);
                studentDataRepository.save(student);
                changed = true;
            }
        }
        return changed;
    }

    /**
     * Moves a student's files after their country changed, and repoints their rows.
     *
     * <p>Called from the student update path. Failure is reported to the caller rather
     * than thrown so an unwritable file cannot roll back the field update the user asked
     * for — the same posture {@link StudentDocumentRemovalService} takes.
     */
    @Transactional
    public boolean relocateForCountryChange(Integer studentId, Integer fromCountry, Integer toCountry) {
        if (studentId == null || java.util.Objects.equals(fromCountry, toCountry)) {
            return false;
        }
        try {
            StudentDatum student = studentDataRepository.findById(studentId).orElse(null);
            if (student == null) {
                return false;
            }
            storage.moveStudentTree(studentId, fromCountry, toCountry);
            // Anything still in the legacy tree comes along too, so a country change on a
            // student the migration has not reached yet does not split their files in two.
            for (Path legacyDir : storage.legacyStudentDirs(studentId)) {
                storage.moveStudentTreeFrom(legacyDir, studentId, toCountry);
            }
            String newRelDir = storage.relativise(storage.studentDirPath(studentId, toCountry));
            boolean changed = repoint(studentId, newRelDir,
                    documentRepository.findByStudentId(studentId),
                    historyRepository.findByStudentIdOrderByChangedAtDesc(studentId),
                    student);
            if (changed) {
                log.info("Moved student {} files from {} to {}", studentId,
                        storage.countryFolderName(fromCountry), storage.countryFolderName(toCountry));
            }
            return changed;
        } catch (Exception e) {
            log.warn("Could not move files for student {} after country change {} -> {}: {}",
                    studentId, fromCountry, toCountry, e.getMessage());
            return false;
        }
    }
}
