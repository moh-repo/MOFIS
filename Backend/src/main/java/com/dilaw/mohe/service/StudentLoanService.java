package com.dilaw.mohe.service;

import com.dilaw.mohe.model.StudentLoan;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.dilaw.mohe.repository.StudentDataRepository;
import com.dilaw.mohe.repository.StudentLoanRepository;
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
public class StudentLoanService {

    @Autowired
    private StudentLoanRepository repository;

    @Autowired
    private StudentDataRepository studentDataRepository;

    @Autowired
    private CountryScopeService countryScopeService;

    @Autowired
    private StudentAccessGuard studentAccessGuard;

    /** For reading an edit onto the row it edits, rather than over it. */
    @Autowired
    private ObjectMapper json;

    public List<StudentLoan> getAll() {
        return repository.findAll(StudentIdCountrySpec.countryScope(countryScopeService.getScope()));
    }

    public StudentLoan getById(Integer id) {
        StudentLoan e = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Loan not found"));
        // The record's student decides (N14): the country was read with .orElse(null)
        // and a null let through, which is what a student hidden by row-level security returns.
        studentAccessGuard.requireStudent(e.getStudentId());
        return e;
    }

    /** Per-student read: the student must be in the caller's country scope (403 otherwise). */
    public List<StudentLoan> getByStudentId(Integer studentId) {
        studentAccessGuard.requireStudent(studentId);
        return repository.findByStudentIdOrderByLoanIdAsc(studentId);
    }

    /**
     * Adds a loan.
     *
     * <p>The id is cleared first. {@code LOAN_ID} really is an identity column,
     * so a new row must arrive without one — but the dialog sends
     * {@code loanId: 0}, and a non-null id makes Spring Data take {@code merge()}
     * rather than {@code persist()}. Hibernate then looked for row 0, did not
     * find it, and the screen got <em>"The record was changed by someone
     * else"</em> on every single Add.
     */
    @Transactional
    public StudentLoan create(StudentLoan entity) {
        entity.setLoanId(null);
        entity.setStudyCountryInId(resolveCountryForWrite(entity.getStudentId()));
        entity.setMidate(Instant.now());
        requirePositiveAmount(entity.getLoanAmount());
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
    public StudentLoan update(Integer id, JsonNode body) {
        StudentLoan existing = getById(id); // validates existence and country scope
        try {
            json.readerForUpdating(existing).readValue(body);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Malformed request body: " + e.getMessage());
        }
        existing.setLoanId(id);
        existing.setStudyCountryInId(resolveCountryForWrite(existing.getStudentId()));
        existing.setMidate(Instant.now());
        requirePositiveAmount(existing.getLoanAmount());
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
                    "The student this loan is for could not be found (studentId).");
        if (!countryScopeService.canAccessCountry(country))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied: country not in scope");
        return country;
    }

    /**
     * {@code CK_STUDENT_LOAN_AMOUNT_POSITIVE} refuses an amount of zero, and the
     * dialog's own default amount was zero. Said here so the screen gets an
     * answer it can show rather than a constraint violation reported as a server
     * fault.
     */
    private void requirePositiveAmount(Integer amount) {
        if (amount == null || amount <= 0)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "A loan amount must be greater than zero (loanAmount).");
    }

    @Transactional
    public void delete(Integer id) {
        getById(id); // validates existence and country scope
        repository.deleteById(id);
    }
}
