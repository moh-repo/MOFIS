package com.dilaw.mohe.service;

import com.dilaw.mohe.model.StudentFinanceArch;
import com.dilaw.mohe.repository.StudentFinanceArchRepository;
import com.dilaw.mohe.repository.spec.StudentFinanceArchSpec;
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
import java.util.Optional;

@Transactional(readOnly = true)
@Service
public class StudentFinanceArchService {

    @Autowired
    private StudentFinanceArchRepository repository;

    @Autowired
    private CountryScopeService countryScopeService;

    private CountryScope currentScope() {
        return countryScopeService.getScope();
    }

    private Specification<StudentFinanceArch> scopedSpec() {
        return StudentFinanceArchSpec.countryScope(currentScope());
    }

    public Page<StudentFinanceArch> getAll(Pageable pageable) {
        return repository.findAll(scopedSpec(), pageable);
    }

    public List<StudentFinanceArch> getAll() {
        return repository.findAll(scopedSpec());
    }

    public StudentFinanceArch getById(long id) {
        StudentFinanceArch entity = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "StudentFinanceArch not found"));
        validateCountryAccess(entity);
        return entity;
    }

    @Transactional
    public StudentFinanceArch create(StudentFinanceArch entity) {
        return repository.save(entity);
    }

    @Transactional
    public StudentFinanceArch update(Long id, StudentFinanceArch entity) {
        Optional<StudentFinanceArch> existingOptional = repository.findById(id);
        if (existingOptional.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "StudentFinanceArch not found");
        }
        validateCountryAccess(existingOptional.get());
        entity.setFinanceArchEntryId(id);
        if (entity.getStudentId() == null) {
            entity.setStudentId(existingOptional.get().getStudentId());
        }
        return repository.save(entity);
    }

    @Transactional
    public void delete(Long id) {
        StudentFinanceArch existing = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "StudentFinanceArch not found"));
        validateCountryAccess(existing);
        repository.deleteById(id);
    }

    public List<StudentFinanceArch> findByStudentId(Integer studentId) {
        Specification<StudentFinanceArch> spec = scopedSpec()
                .and((root, query, cb) -> cb.equal(root.get("studentId"), studentId));
        return repository.findAll(spec);
    }

    private void validateCountryAccess(StudentFinanceArch entity) {
        CountryScope scope = currentScope();
        if (scope == null || scope.isMinistryUser()) {
            return;
        }
        List<Integer> allowed = scope.getAllowedCountryIds();
        if (allowed.isEmpty()
                || entity.getStudyCountryInId() == null
                || !allowed.contains(entity.getStudyCountryInId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied: country not in scope");
        }
    }
}
