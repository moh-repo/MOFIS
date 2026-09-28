package com.dilaw.mohe.service;

import com.dilaw.mohe.model.StudentDegree;
import com.dilaw.mohe.repository.StudentDegreeRepository;
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
public class StudentDegreeService {

    @Autowired
    private StudentDegreeRepository repository;

    /** For the duplicate-name check (M12). */
    @PersistenceContext
    private EntityManager em;

    public List<StudentDegree> getAll() {
        return repository.findAll();
    }

    public StudentDegree getById(int id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "StudentDegree not found"));
    }

    @Transactional
    public StudentDegree create(StudentDegree entity) {
        LookupNameUniqueness.require(em, StudentDegree.class, "studentDegreeName",
                entity.getStudentDegreeName(), "id", null);
        entity.setId(repository.nextId());
        return repository.save(entity);
    }

    @Transactional
    public StudentDegree update(Integer id, StudentDegree entity) {
        Optional<StudentDegree> existingOptional = repository.findById(id);
        if (existingOptional.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "StudentDegree not found");
        }
        StudentDegree existing = existingOptional.get();
        if (!LookupNameUniqueness.same(existing.getStudentDegreeName(), entity.getStudentDegreeName())) {
            LookupNameUniqueness.require(em, StudentDegree.class, "studentDegreeName",
                    entity.getStudentDegreeName(), "id", id);
        }
        // Every column the System Coding form edits (lookup-descriptors.ts). Copying
        // only the Arabic name dropped the English name and the Active flag while the
        // screen said "saved" (QA CRUD matrix, cause [A]).
        existing.setStudentDegreeName(entity.getStudentDegreeName());
        existing.setStudentDegreeNameEn(entity.getStudentDegreeNameEn());
        if (entity.getIsActive() != null) existing.setIsActive(entity.getIsActive());
        return repository.save(existing);
    }

    /** 404 for an id with no row (round-5 QA R5-22): deleteById is silent then, and the screen read success. */
    @Transactional
    public void delete(Integer id) {
        if (id == null || !repository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Student degree not found");
        }
        repository.deleteById(id);
    }
}
