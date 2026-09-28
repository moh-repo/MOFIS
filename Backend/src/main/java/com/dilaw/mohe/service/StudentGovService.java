package com.dilaw.mohe.service;

import com.dilaw.mohe.model.StudentGov;
import com.dilaw.mohe.exception.LocalizedStatusException;
import com.dilaw.mohe.repository.StudentGovMudRepository;
import com.dilaw.mohe.repository.StudentGovRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

@Transactional(readOnly = true)
@Service
public class StudentGovService {

    @Autowired
    private StudentGovRepository repository;

    @Autowired
    private StudentGovMudRepository mudRepository;

    public List<StudentGov> getAll() {
        return repository.findAll();
    }

    public StudentGov getById(int id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "StudentGov not found"));
    }

    @Transactional
    public StudentGov create(StudentGov entity) {
        return repository.save(entity);
    }

    @Transactional
    public StudentGov update(Integer id, StudentGov entity) {
        Optional<StudentGov> existingOptional = repository.findById(id);
        if (existingOptional.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "StudentGov not found");
        }
        // The body replaces the row, so what it does not mention keeps the stored value (M12).
        StudentGov existing = existingOptional.get();
        if (entity.getIsActive() == null) entity.setIsActive(existing.getIsActive());
        if (entity.getStudentMidate() == null) entity.setStudentMidate(existing.getStudentMidate());
        entity.setId(id);
        return repository.save(entity);
    }

    /**
     * Refuses a delete that would orphan children (plan W6-06, QA C9).
     *
     * <p>V129 gives the hierarchy the foreign keys it always implied, so the
     * database would refuse this anyway — but as a constraint violation, which
     * reaches the caller as a 500 naming a constraint. Counted here instead, so
     * the answer is a 409 that says what still depends on the row and how many.
     *
     * <p>An id with no row is 404 (round-5 QA R5-22): deleteById is silent then,
     * and the screen read success.
     */
    @Transactional
    public void delete(Integer id) {
        if (id == null || !repository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Governorate not found");
        }
        int children = mudRepository.findByStudentGovId(id).size();
        if (children > 0) {
            throw new LocalizedStatusException(org.springframework.http.HttpStatus.CONFLICT,
                    "This governorate still has " + children + " municipalit" + (children == 1 ? "y" : "ies")
                            + "; move or remove them first.",
                    "location.governorate.hasMunicipalities", children);
        }
        repository.deleteById(id);
    }
} 