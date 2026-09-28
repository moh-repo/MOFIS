package com.dilaw.mohe.service;

import com.dilaw.mohe.model.StudentDatum;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Single owner of the student-photo read path. Photos are stored encrypted at
 * rest under the document storage root, so every consumer (HTTP endpoint,
 * Jasper documents) must go through the same resolve-then-decrypt sequence.
 */
@Service
public class StudentPhotoService {

    private static final Logger log = LoggerFactory.getLogger(StudentPhotoService.class);

    @Autowired
    private StudentDataService studentDataService;

    @Autowired
    private FileEncryptionService fileEncryptionService;

    @Autowired
    private StudentFileStorageService studentFileStorage;

    /**
     * Decrypted photo bytes for a student, or null when the student has no
     * photo on record or the recorded file is absent — a normal state, not an
     * error.
     */
    public byte[] getPhotoBytes(Integer studentId) {
        try {
            StudentDatum student = studentDataService.getStudentData(studentId);
            if (student == null || student.getPicturePath() == null || student.getPicturePath().isEmpty()) {
                return null;
            }
            return getPhotoBytesForPath(student.getPicturePath());
        } catch (Exception e) {
            log.warn("Failed to read photo for student {}: {}", studentId, e.getMessage());
            return null;
        }
    }

    /** Decrypted bytes for an already-known picture path, or null when absent. */
    public byte[] getPhotoBytesForPath(String picturePath) {
        if (picturePath == null || picturePath.isEmpty()) {
            return null;
        }
        try {
            Path photoPath = studentFileStorage.resolveStored(picturePath);
            if (!Files.exists(photoPath)) {
                return null;
            }
            return fileEncryptionService.readDecrypted(photoPath);
        } catch (Exception e) {
            log.warn("Failed to read photo file {}: {}", picturePath, e.getMessage());
            return null;
        }
    }

    /** Media type ("png" → image/png, else image/jpeg) for a picture path. */
    public boolean isPng(String picturePath) {
        if (picturePath == null) {
            return false;
        }
        int dot = picturePath.lastIndexOf('.');
        return dot >= 0 && "png".equalsIgnoreCase(picturePath.substring(dot + 1));
    }
}
