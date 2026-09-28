package com.dilaw.mohe.service;

import com.dilaw.mohe.model.StudentCurrentLevel;
import com.dilaw.mohe.repository.StudentCurrentLevelRepository;
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
public class StudentCurrentLevelService {

    @Autowired
    private StudentCurrentLevelRepository repository;

    /** For the duplicate-name check (M12). */
    @PersistenceContext
    private EntityManager em;

    public List<StudentCurrentLevel> getAll() {
        return repository.findAll();
    }

    public StudentCurrentLevel getById(int id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "StudentCurrentLevel not found"));
    }

    @Transactional
    public StudentCurrentLevel create(StudentCurrentLevel entity) {
        LookupNameUniqueness.require(em, StudentCurrentLevel.class, "studentCurrentLevelName",
                entity.getStudentCurrentLevelName(), "id", null);
        entity.setId(repository.nextId());
        return repository.save(entity);
    }

    @Transactional
    public StudentCurrentLevel update(Integer id, StudentCurrentLevel entity) {
        Optional<StudentCurrentLevel> existingOptional = repository.findById(id);
        if (existingOptional.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "StudentCurrentLevel not found");
        }
        StudentCurrentLevel existing = existingOptional.get();
        if (!LookupNameUniqueness.same(existing.getStudentCurrentLevelName(), entity.getStudentCurrentLevelName())) {
            LookupNameUniqueness.require(em, StudentCurrentLevel.class, "studentCurrentLevelName",
                    entity.getStudentCurrentLevelName(), "id", id);
        }
        // Every column the System Coding form edits (lookup-descriptors.ts). Copying
        // only the Arabic name dropped the English name, the order and the Active
        // flag while the screen said "saved" (QA CRUD matrix, cause [A]).
        existing.setStudentCurrentLevelName(entity.getStudentCurrentLevelName());
        existing.setStudentCurrentLevelNameEn(entity.getStudentCurrentLevelNameEn());
        if (entity.getStudentCurrentLevelOrderNo() != null) {
            existing.setStudentCurrentLevelOrderNo(entity.getStudentCurrentLevelOrderNo());
        }
        if (entity.getIsActive() != null) existing.setIsActive(entity.getIsActive());
        return repository.save(existing);
    }

    /** 404 for an id with no row (round-5 QA R5-22): deleteById is silent then, and the screen read success. */
    @Transactional
    public void delete(Integer id) {
        if (id == null || !repository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Current level not found");
        }
        repository.deleteById(id);
    }
} 