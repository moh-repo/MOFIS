package com.dilaw.mohe.service;

import com.dilaw.mohe.model.StudentDatum;
import com.dilaw.mohe.model.StudentDocument;
import com.dilaw.mohe.model.StudentDocumentHistory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * One place to answer "may the caller touch this student's data?".
 *
 * <p>The country rule already exists, in
 * {@link StudentDataService#getStudentData(Integer)} — it resolves the student,
 * checks the study country against the caller's scope and checks record
 * visibility. What was missing was that the per-student detail endpoints
 * (documents, year results, timeline) call their own services directly, and
 * those take a bare {@code studentId} with no notion of a caller. The list was
 * scoped, so ids were hidden; but student ids are small sequential integers, so
 * any id that was guessed read everything behind it.
 *
 * <p>This guard therefore delegates rather than restating the rule. A second
 * copy of a security check is a second thing to keep in step, and the two would
 * diverge exactly when it mattered.
 *
 * <p>Denials are {@code 403}, matching the endpoints that already deny that way.
 * A missing record is {@code 404}, which {@code getStudentData} raises itself.
 */
@Component
public class StudentAccessGuard {

    @Autowired
    private StudentDataService studentDataService;

    @Autowired
    private StudentDocumentService studentDocumentService;

    @Autowired
    private StudentDocumentHistoryService studentDocumentHistoryService;

    /**
     * Confirms the caller may reach this student, and returns the record so a
     * caller that needs it does not have to load it twice.
     *
     * @throws ResponseStatusException 404 if no such student, 403 if out of scope
     */
    public StudentDatum requireStudent(Integer studentId) {
        if (studentId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Student id is required");
        }
        return studentDataService.getStudentData(studentId);
    }

    /**
     * Resolves a document to its student, then applies the same rule. Used by the
     * endpoints keyed on a document id rather than a student id — a document id
     * is just as guessable, and reaches the file itself.
     */
    public StudentDocument requireDocument(Integer documentId) {
        StudentDocument document = studentDocumentService.getDocumentById(documentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found"));
        requireStudent(document.getStudentId());
        return document;
    }

    /**
     * The archived-version equivalent. {@link StudentDocumentHistory} carries its
     * own {@code studentId}, so this needs no join back to the live document —
     * which matters, because the live document may since have been deleted.
     */
    public StudentDocumentHistory requireDocumentHistory(Integer historyId) {
        StudentDocumentHistory history = studentDocumentHistoryService.getHistoryById(historyId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document version not found"));
        requireStudent(history.getStudentId());
        return history;
    }
}
