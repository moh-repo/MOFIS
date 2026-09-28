package com.dilaw.mohe.service;

import com.dilaw.mohe.model.StudentDataArch;
import com.dilaw.mohe.repository.StudentDataArchRepository;
import com.dilaw.mohe.repository.StudentFinanceArchRepository;
import com.dilaw.mohe.repository.spec.StudentDataArchSpec;
import com.dilaw.mohe.ums.service.CountryScopeService;
import com.dilaw.mohe.ums.service.CountryScopeService.CountryScope;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Transactional(readOnly = true)
@Service
public class StudentDataArchService {

    @Autowired
    private StudentDataArchRepository repository;

    @Autowired
    private StudentFinanceArchRepository financeArchRepository;

    @Autowired
    private CountryScopeService countryScopeService;

    private CountryScope currentScope() {
        return countryScopeService.getScope();
    }

    private Specification<StudentDataArch> scopedSpec() {
        return StudentDataArchSpec.countryScope(currentScope());
    }

    public List<StudentDataArch> getAll() {
        return repository.findAll(scopedSpec());
    }

    public StudentDataArch getById(int id) {
        StudentDataArch entity = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "StudentDataArch not found"));
        validateCountryAccess(entity);
        return entity;
    }

    /*
     * No create, whole-row update or delete: the archive is written by the quarter
     * close (QuarterArchiveMapper through the repository) and corrected only through
     * updateFinanceColumns (live QA H1, 23 Sep 2026; DEC-08 a).
     */

    /**
     * Copies the finance columns of {@code changes} onto archived row {@code id} and
     * leaves every other column as archived (review F5).
     */
    @Transactional
    public StudentDataArch updateFinanceColumns(Integer id, StudentDataArch changes) {
        StudentDataArch existing = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "StudentDataArch not found"));
        validateCountryAccess(existing);
        copyFinanceColumns(changes, existing);
        return repository.save(existing);
    }

    /** The columns the finance history page edits. */
    static void copyFinanceColumns(StudentDataArch from, StudentDataArch to) {
        if (from.getStudentAmount() != null && from.getStudentAmount() < 0) {
            throw new IllegalArgumentException("Amount must not be negative");
        }
        to.setStudentAmount(from.getStudentAmount());
        to.setAmountCurrency(from.getAmountCurrency());
        to.setUniversityFeeStatusCurrency(from.getUniversityFeeStatusCurrency());
        to.setStudentFirstReceiptOfDue(from.getStudentFirstReceiptOfDue());
        to.setStudentNote1(from.getStudentNote1());
        to.setStudentNote2(from.getStudentNote2());
    }

    public Page<StudentDataArch> findAllWithPagination(Pageable pageable) {
        return repository.findAll(scopedSpec(), pageable);
    }

    public List<StudentDataArch> findByStudentNameContaining(String name) {
        Specification<StudentDataArch> spec = scopedSpec().and(StudentDataArchSpec.nameContains(name));
        return repository.findAll(spec);
    }
    
    // New methods for distinct students
    public Page<StudentDataArch> findDistinctStudentsWithLatestData(Pageable pageable) {
        return withArchivedFinanceTotals(repository.findDistinctStudentsWithLatestData(pageable, countryFilterArgs()));
    }
    
    public List<StudentDataArch> findDistinctStudentsByNameWithLatestData(String name) {
        return repository.findDistinctStudentsByNameWithLatestData(name, countryFilterArgs());
    }
    
    public List<StudentDataArch> findAllDistinctStudentsWithLatestData() {
        return repository.findAllDistinctStudentsWithLatestData(countryFilterArgs());
    }
    
    public List<StudentDataArch> findAllFinancialRecordsByStudentName(String studentName) {
        return repository.findAllFinancialRecordsByStudentName(studentName, countryFilterArgs())
                .stream()
                .filter(this::isInScope)
                .toList();
    }

    /** All archived snapshots for one student by the real STUDENT_ID (avoids merging same-named students). */
    public List<StudentDataArch> findAllFinancialRecordsByStudentId(Integer studentId) {
        return repository.findAllFinancialRecordsByStudentId(studentId, countryFilterArgs())
                .stream()
                .filter(this::isInScope)
                .toList();
    }

    public Page<StudentDataArch> findDistinctStudentsWithFieldFilters(Integer studentId, String name, String passport, Pageable pageable) {
        return withArchivedFinanceTotals(
                repository.findDistinctStudentsWithFieldFilters(studentId, name, passport, pageable, countryFilterArgs()));
    }

    /**
     * Fills in each row's Total Received / Total Wafar / Total Net from STUDENT_FINANCE_ARCH.
     *
     * One grouped query for the whole page, not one per row. Students with no archived
     * finance rows keep zeros rather than nulls, so the list renders 0 instead of a blank.
     */
    private Page<StudentDataArch> withArchivedFinanceTotals(Page<StudentDataArch> page) {
        List<Integer> studentIds = page.getContent().stream()
                .map(StudentDataArch::getStudentId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        Map<Integer, int[]> totals = studentIds.isEmpty()
                ? Map.of()
                : financeArchRepository.sumFinanceTotalsByStudentIds(studentIds).stream()
                        .collect(Collectors.toMap(
                                row -> toInt(row[0]),
                                row -> new int[] { toInt(row[1]), toInt(row[2]), toInt(row[3]) },
                                (a, b) -> a));

        for (StudentDataArch row : page.getContent()) {
            int[] t = totals.getOrDefault(row.getStudentId(), new int[] { 0, 0, 0 });
            row.setArchivedAmountReceived(t[0]);
            row.setArchivedWafarAmount(t[1]);
            row.setArchivedNetAmount(t[2]);
        }
        return page;
    }

    /** Native SUM() comes back as whatever numeric type the driver picked, so normalise. */
    private static int toInt(Object value) {
        return value instanceof Number n ? n.intValue() : 0;
    }

    private CountryFilterArgs countryFilterArgs() {
        CountryScope scope = currentScope();
        boolean apply = scope != null && !scope.isMinistryUser() && !scope.getAllowedCountryIds().isEmpty();
        List<Integer> ids = apply ? scope.getAllowedCountryIds() : List.of(-1);
        return new CountryFilterArgs(apply, ids);
    }

    private void validateCountryAccess(StudentDataArch entity) {
        if (!isInScope(entity)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied: country not in scope");
        }
    }

    private boolean isInScope(StudentDataArch entity) {
        CountryScope scope = currentScope();
        if (scope == null || scope.isMinistryUser()) {
            return true;
        }
        List<Integer> allowed = scope.getAllowedCountryIds();
        if (allowed.isEmpty()) {
            return false;
        }
        return entity.getStudyCountryIn() != null && allowed.contains(entity.getStudyCountryIn());
    }

    public record CountryFilterArgs(boolean applyCountryFilter, List<Integer> countryIds) {}
}
