package com.dilaw.mohe.service;

import com.dilaw.mohe.model.StudentBooksAllowance;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.dilaw.mohe.repository.StudentBooksAllowanceRepository;
import com.dilaw.mohe.repository.StudentDataRepository;
import com.dilaw.mohe.repository.spec.StudentIdCountrySpec;
import com.dilaw.mohe.ums.service.CountryScopeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.time.Instant;
import java.util.List;

@Transactional(readOnly = true)
@Service
public class StudentBooksAllowanceService {

    @Autowired
    private StudentBooksAllowanceRepository repository;

    @Autowired
    private StudentDataRepository studentDataRepository;

    @Autowired
    private CountryScopeService countryScopeService;

    @Autowired
    private StudentAccessGuard studentAccessGuard;

    /** For reading an edit onto the row it edits, rather than over it. */
    @Autowired
    private ObjectMapper json;

    public List<StudentBooksAllowance> getAll() {
        return repository.findAll(StudentIdCountrySpec.countryScope(countryScopeService.getScope()));
    }

    public StudentBooksAllowance getById(Integer id) {
        StudentBooksAllowance e = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Books allowance not found"));
        // The record's student decides (N14): the country was read with .orElse(null)
        // and a null let through, which is what a student hidden by row-level security returns.
        studentAccessGuard.requireStudent(e.getStudentId());
        return e;
    }

    /** Per-student read: the student must be in the caller's country scope (403 otherwise). */
    public List<StudentBooksAllowance> getByStudentId(Integer studentId) {
        studentAccessGuard.requireStudent(studentId);
        return repository.findByStudentIdOrderByBooksAllowIdAsc(studentId);
    }

    /**
     * Adds a books allowance.
     *
     * <p>The id is cleared first. {@code BOOKS_ALLOW_ID} really is an identity
     * column, so a new row must arrive without one — but the dialog sends
     * {@code booksAllowId: 0}, and a non-null id makes Spring Data take
     * {@code merge()} rather than {@code persist()}. Hibernate then looked for
     * row 0, did not find it, and the screen got
     * <em>"The record was changed by someone else"</em> on every single Add.
     */
    @Transactional
    public StudentBooksAllowance create(StudentBooksAllowance entity) {
        entity.setBooksAllowId(null);
        entity.setStudyCountryInId(resolveCountryForWrite(entity.getStudentId()));
        entity.setMidate(Instant.now());
        return repository.save(entity);
    }

    /**
     * An edit changes the columns it names and leaves the rest of the row alone.
     *
     * <p>This used to be {@code repository.save(entity)} on a bound entity, so a
     * dialog that does not send a column wrote NULL over it. The body arrives as
     * JSON rather than a deserialized object because a deserialized object cannot
     * tell the two apart: <b>a field that is absent is not a field set to
     * nothing</b>. Reading the tree onto the loaded row keeps that distinction,
     * so the dialog can still clear a field it owns by sending it as null.
     */
    @Transactional
    public StudentBooksAllowance update(Integer id, JsonNode body) {
        StudentBooksAllowance existing = getById(id); // validates existence and country scope
        try {
            json.readerForUpdating(existing).readValue(body);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Malformed request body: " + e.getMessage());
        }
        existing.setBooksAllowId(id);
        existing.setStudyCountryInId(resolveCountryForWrite(existing.getStudentId()));
        existing.setMidate(Instant.now());
        return repository.save(existing);
    }

    /**
     * The country a row is filed under, taken from the student it is for.
     *
     * <p>A student that cannot be resolved used to pass: the check read
     * {@code country != null && !canAccess(country)}, so an absent or unknown
     * student id skipped it entirely and stored a row under no country — one no
     * attaché's staff would ever see again. That is the disappearance the check
     * exists to prevent, so an unresolvable student is refused instead.
     */
    private Integer resolveCountryForWrite(Integer studentId) {
        Integer country = studentId == null ? null
                : studentDataRepository.findStudyCountryInByStudentId(studentId).orElse(null);
        if (country == null)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "The student this allowance is for could not be found (studentId).");
        if (!countryScopeService.canAccessCountry(country))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied: country not in scope");
        return country;
    }

    @Transactional
    public void delete(Integer id) {
        getById(id); // validates existence and country scope
        repository.deleteById(id);
    }
}
