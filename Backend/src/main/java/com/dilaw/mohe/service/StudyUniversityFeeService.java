package com.dilaw.mohe.service;

import com.dilaw.mohe.model.StudyUniversityFee;
import com.dilaw.mohe.repository.StudentDataRepository;
import com.dilaw.mohe.repository.StudyUniversityFeeRepository;
import com.dilaw.mohe.repository.spec.StudentIdCountrySpec;
import com.dilaw.mohe.ums.service.CountryScopeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Transactional(readOnly = true)
@Service
public class StudyUniversityFeeService {

    /** Same as STUDY_UNIVERSITY_FEE_HISTORY / UI payment status dropdown. */
    public static final int PAY_STATUS_CANCELLED = 3;

    @Autowired
    private StudyUniversityFeeRepository repository;

    @Autowired
    private StudentFinanceWafarService studentFinanceWafarService;

    @Autowired
    private StudentDataRepository studentDataRepository;

    @Autowired
    private CountryScopeService countryScopeService;

    /**
     * The fee's student decides who may touch it (live QA N14, 26 Sep 2026). The
     * country used to be read with {@code findStudyCountryInByStudentId(..).orElse(null)}
     * and a null let through, and null is exactly what a student hidden by row-level
     * security returns: the check passed for the students it existed to refuse.
     */
    @Autowired
    private StudentAccessGuard studentAccessGuard;

    public List<StudyUniversityFee> getAll() {
        return repository.findAll(StudentIdCountrySpec.countryScope(countryScopeService.getScope()));
    }

    /**
     * The fee list, filtered and paged here (review F7). The screen always sent a
     * search term, a status and a date range; the endpoint it called ignored all
     * three and returned every row, so the search box did nothing.
     *
     * @param term  part of the student's name, or a student id, check number or academic year
     * @param status pay status code; null for all
     * @param from  pay date from (inclusive); null for open
     * @param to    pay date to (inclusive); null for open
     */
    public Page<StudyUniversityFee> search(String term, Integer status, Instant from, Instant to, Pageable pageable) {
        Specification<StudyUniversityFee> spec = Specification
                .where(StudentIdCountrySpec.<StudyUniversityFee>countryScope(countryScopeService.getScope()))
                .and(textSearch(term))
                .and(status == null ? null : (root, q, cb) -> cb.equal(root.get("studyUniversityFeePayStatus"), status))
                .and(from == null ? null : (root, q, cb) -> cb.greaterThanOrEqualTo(root.get("studyUniversityFeePayDate"), from))
                .and(to == null ? null : (root, q, cb) -> cb.lessThanOrEqualTo(root.get("studyUniversityFeePayDate"), to));
        return repository.findAll(spec, pageable);
    }

    private static Specification<StudyUniversityFee> textSearch(String term) {
        if (term == null || term.isBlank()) {
            return null;
        }
        String t = term.trim();
        String like = "%" + t + "%";
        Integer asId = t.matches("\\d{1,9}") ? Integer.valueOf(t) : null;
        return (root, q, cb) -> {
            var byText = cb.or(
                    cb.like(root.get("studyUniversityFeeStudentName"), like),
                    cb.like(root.get("studyUniversityCheckNo"), like),
                    cb.like(root.get("studyUniversityFeeYear"), like));
            return asId == null ? byText : cb.or(byText, cb.equal(root.get("studentId"), asId));
        };
    }

    public StudyUniversityFee getById(int id) {
        StudyUniversityFee e = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "StudyUniversityFee not found"));
        studentAccessGuard.requireStudent(e.getStudentId());
        return e;
    }

    /** A student's fees, for a student the caller may reach (it read any student's; N14). */
    public List<StudyUniversityFee> getByStudentId(Integer studentId) {
        studentAccessGuard.requireStudent(studentId);
        return repository.findByStudentId(studentId);
    }

    /**
     * The checks {@link #create} and {@link #update} make, run when the approval request
     * is raised (round-5 QA R5-28): a fee missing how the payment is made was accepted,
     * and refused only when the approver approved it, after which it could only be
     * rejected. Run on a copy, so the request stores the fee as the user sent it.
     */
    public void validateForRequest(StudyUniversityFee entity) {
        if (entity == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The fee is required.");
        }
        StudyUniversityFee copy = new StudyUniversityFee();
        org.springframework.beans.BeanUtils.copyProperties(entity, copy);
        assertValidDiscount(copy);
        prepareForPersistence(copy);
        assertRequiredPeriodFields(copy);
    }

    @Transactional
    public StudyUniversityFee create(StudyUniversityFee entity) {
        studentAccessGuard.requireStudent(entity.getStudentId());
        assertValidDiscount(entity);
        prepareForPersistence(entity);
        applyStudentName(entity);
        assertRequiredPeriodFields(entity);
        assertNoActiveDuplicate(entity, null);
        StudyUniversityFee saved = repository.save(entity);
        studentFinanceWafarService.allocateUniversityFeeDiscount(saved);
        return saved;
    }

    @Transactional
    public StudyUniversityFee update(Integer id, StudyUniversityFee entity) {
        getById(id); // validates existence and the stored student's scope
        // The body names the student too, and it is saved: a fee cannot be moved to a
        // student the caller may not reach (N14).
        studentAccessGuard.requireStudent(entity.getStudentId());
        Optional<StudyUniversityFee> existingOptional = repository.findById(id);
        if (existingOptional.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "StudyUniversityFee not found");
        }
        entity.setId(id);
        assertValidDiscount(entity);
        prepareForPersistence(entity);
        applyStudentName(entity);
        assertRequiredPeriodFields(entity);
        assertNoActiveDuplicate(entity, id);
        return repository.save(entity);
    }

    @Transactional
    public void delete(Integer id) {
        getById(id); // run by the approval executor: the approver must reach the student too (N14)
        repository.deleteById(id);
    }

    /**
     * A discount is part of the fee, so it lies between nothing and the whole fee.
     * Only the form checked this; a request straight to the API stored a discount
     * above the amount, and {@link #normalizeFeeAmounts} then quietly clamped the
     * net to 0 (QA B13, FIN-005).
     */
    static void assertValidDiscount(StudyUniversityFee e) {
        int amount = nz(e.getStudyUniversityFeeAmount());
        int discount = nz(e.getStudyUniversityFeeDiscount());
        if (amount < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The fee amount cannot be negative.");
        }
        if (discount < 0 || discount > amount) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "The discount must be between 0 and the fee amount (" + amount + ").");
        }
    }

    /**
     * The row's copy of the student's name comes from the student, not the form.
     * The form sent whatever its picker showed, so two rows of the same student
     * could carry two spellings and read as two students (QA B13, FIN-005).
     */
    private void applyStudentName(StudyUniversityFee e) {
        if (e.getStudentId() == null) {
            return;
        }
        studentDataRepository.findStudentNameById(e.getStudentId())
                .filter(name -> !name.isBlank())
                .ifPresent(e::setStudyUniversityFeeStudentName);
    }

    private void prepareForPersistence(StudyUniversityFee entity) {
        normalizeFeeYearString(entity);
        normalizeSemesterNumber(entity);
        normalizeFeeAmounts(entity);
        normalizeFeeLookupIds(entity);
    }

    private void assertRequiredPeriodFields(StudyUniversityFee e) {
        if (e.getStudentId() == null || e.getStudentId() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Student is required.");
        }
        if (e.getStudyUniversityFeeYear() == null || e.getStudyUniversityFeeYear().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Academic year is required.");
        }
        if (e.getStudyUniversityFeeSemesterNumber() == null || e.getStudyUniversityFeeSemesterNumber() < 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Semester number is required (1 = first semester, 2 = second, …).");
        }
        if (e.getStudyUniversityFeeYearScopeId() == null || e.getStudyUniversityFeeYearScopeId() < 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Year coverage is required.");
        }
        if (e.getStudyUniversityFeeSemesterScopeId() == null || e.getStudyUniversityFeeSemesterScopeId() < 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Payment semester scope is required.");
        }
        if (e.getStudyUniversityFeeSourceId() == null || e.getStudyUniversityFeeSourceId() < 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Fee source is required.");
        }
        if (e.getStudyUniversityCheckPayorTypeId() == null
                || e.getStudyUniversityCheckPayorTypeId() < 1
                || e.getStudyUniversityCheckPayorTypeId() > 2) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "How the payment is made is required (student name vs university).");
        }
    }

    /**
     * Block a second payment for the same student, academic year, semester number, semester coverage,
     * and year coverage while a non-cancelled record already exists (cancelled = pay status 3).
     */
    private void assertNoActiveDuplicate(StudyUniversityFee e, Integer excludeRecordId) {
        long count = repository.countActiveDuplicatePeriod(
                e.getStudentId(),
                e.getStudyUniversityFeeYear(),
                e.getStudyUniversityFeeSemesterNumber(),
                e.getStudyUniversityFeeSemesterScopeId(),
                e.getStudyUniversityFeeYearScopeId(),
                excludeRecordId);
        if (count > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "A university fee for this student, academic year, semester number, semester coverage, "
                            + "and year coverage already exists. Cancel the existing record before recording "
                            + "another payment for the same period.");
        }
    }

    private static void normalizeFeeYearString(StudyUniversityFee e) {
        if (e == null) {
            return;
        }
        if (e.getStudyUniversityFeeYear() == null) {
            e.setStudyUniversityFeeYear("");
        } else {
            e.setStudyUniversityFeeYear(String.valueOf(e.getStudyUniversityFeeYear()).trim());
        }
    }

    private static void normalizeSemesterNumber(StudyUniversityFee e) {
        if (e == null) {
            return;
        }
        if (e.getStudyUniversityFeeSemesterNumber() == null) {
            e.setStudyUniversityFeeSemesterNumber(0);
        }
    }

    /**
     * Net fee after discount {@code studyUniversityRealFeeAmount = max(0, amount - discount)}.
     */
    public static void normalizeFeeAmounts(StudyUniversityFee e) {
        if (e == null) {
            return;
        }
        int gross = nz(e.getStudyUniversityFeeAmount());
        int discount = nz(e.getStudyUniversityFeeDiscount());
        e.setStudyUniversityRealFeeAmount(Math.max(0, gross - discount));
    }

    /**
     * NOT NULL int columns: avoid null on insert/update if JSON omits a field.
     */
    public static void normalizeFeeLookupIds(StudyUniversityFee e) {
        if (e == null) {
            return;
        }
        if (e.getStudyUniversityFeeSourceId() == null) {
            e.setStudyUniversityFeeSourceId(0);
        }
        if (e.getStudyUniversityFeeSemesterScopeId() == null) {
            e.setStudyUniversityFeeSemesterScopeId(0);
        }
        if (e.getStudyUniversityFeeYearScopeId() == null) {
            e.setStudyUniversityFeeYearScopeId(0);
        }
        if (e.getStudyUniversityCheckPayorTypeId() == null) {
            e.setStudyUniversityCheckPayorTypeId(0);
        }
    }

    private static int nz(Integer v) {
        return v != null ? v.intValue() : 0;
    }
}
