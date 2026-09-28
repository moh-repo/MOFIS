package com.dilaw.mohe.service;

import com.dilaw.mohe.model.StudentStudyType;
import com.dilaw.mohe.repository.StudentStudyTypeRepository;
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
public class StudentStudyTypeService {

    @Autowired
    private StudentStudyTypeRepository repository;

    /** For the duplicate-name check (M12). */
    @PersistenceContext
    private EntityManager em;

    public List<StudentStudyType> getAll() {
        return repository.findAll();
    }

    public StudentStudyType getById(int id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "StudentStudyType not found"));
    }

    @Transactional
    public StudentStudyType create(StudentStudyType entity) {
        LookupNameUniqueness.require(em, StudentStudyType.class, "studentStudyTypeName",
                entity.getStudentStudyTypeName(), "id", null);
        entity.setId(repository.nextId());
        return repository.save(entity);
    }

    @Transactional
    public StudentStudyType update(Integer id, StudentStudyType entity) {
        Optional<StudentStudyType> existingOptional = repository.findById(id);
        if (existingOptional.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "StudentStudyType not found");
        }
        StudentStudyType existing = existingOptional.get();
        if (!LookupNameUniqueness.same(existing.getStudentStudyTypeName(), entity.getStudentStudyTypeName())) {
            LookupNameUniqueness.require(em, StudentStudyType.class, "studentStudyTypeName",
                    entity.getStudentStudyTypeName(), "id", id);
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
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Study type not found");
        }
        repository.deleteById(id);
    }
} 