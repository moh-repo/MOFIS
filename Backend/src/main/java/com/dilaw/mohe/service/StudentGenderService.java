package com.dilaw.mohe.service;

import com.dilaw.mohe.model.StudentGender;
import com.dilaw.mohe.repository.StudentGenderRepository;
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
public class StudentGenderService {

    @Autowired
    private StudentGenderRepository repository;

    /** For the duplicate-name check (M12). */
    @PersistenceContext
    private EntityManager em;

    public List<StudentGender> getAll() {
        return repository.findAll();
    }

    public StudentGender getById(int id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "StudentGender not found"));
    }

    @Transactional
        public StudentGender create(StudentGender entity) {
        LookupNameUniqueness.require(em, StudentGender.class, "studentGenderName",
                entity.getStudentGenderName(), "id", null);
        entity.setId(repository.nextId());
        return repository.save(entity);
    }

    @Transactional
        public StudentGender update(Integer id, StudentGender entity) {
        Optional<StudentGender> existingOptional = repository.findById(id);
        if (existingOptional.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "StudentGender not found");
        }
        StudentGender existing = existingOptional.get();
        if (!LookupNameUniqueness.same(existing.getStudentGenderName(), entity.getStudentGenderName())) {
            LookupNameUniqueness.require(em, StudentGender.class, "studentGenderName",
                    entity.getStudentGenderName(), "id", id);
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
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Student gender not found");
        }
        repository.deleteById(id);
    }
} 