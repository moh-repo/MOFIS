package com.dilaw.mohe.service;

import com.dilaw.mohe.model.StudentMajor;
import com.dilaw.mohe.repository.StudentMajorRepository;
import com.dilaw.mohe.util.LookupNameUniqueness;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

@Transactional(readOnly = true)
@Service
public class StudentMajorService {

    @Autowired
    private StudentMajorRepository repository;

    /** For the duplicate-name check (M12), unique within the education level. */
    @PersistenceContext
    private EntityManager em;

    public List<StudentMajor> getAll() {
        return repository.findAll();
    }

    public StudentMajor getById(int id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "StudentMajor not found"));
    }

    @Transactional
    public StudentMajor create(StudentMajor entity) {
        LookupNameUniqueness.require(em, StudentMajor.class, "studentMajorName", entity.getStudentMajorName(),
                "id", null, "studentEductionId", entity.getStudentEductionId());
        entity.setId(repository.nextId());
        return repository.save(entity);
    }

    @Transactional
    public StudentMajor update(Integer id, StudentMajor entity) {
        Optional<StudentMajor> existingOptional = repository.findById(id);
        if (existingOptional.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "StudentMajor not found");
        }
        StudentMajor existing = existingOptional.get();
        if (!LookupNameUniqueness.unchanged(existing.getStudentMajorName(), entity.getStudentMajorName(),
                existing.getStudentEductionId(), entity.getStudentEductionId())) {
            LookupNameUniqueness.require(em, StudentMajor.class, "studentMajorName", entity.getStudentMajorName(),
                    "id", id, "studentEductionId", entity.getStudentEductionId());
        }
        // The body replaces the row, so a flag it does not mention keeps the stored value (L1).
        if (entity.getIsActive() == null) entity.setIsActive(existing.getIsActive());
        entity.setId(id);
        return repository.save(entity);
    }

    /** 404 for an id with no row (round-5 QA R5-22): deleteById is silent then, and the screen read success. */
    @Transactional
    public void delete(Integer id) {
        if (id == null || !repository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Major not found");
        }
        repository.deleteById(id);
    }
} 