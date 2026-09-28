package com.dilaw.mohe.service;

import com.dilaw.mohe.model.StudyDegreeIn;
import com.dilaw.mohe.repository.StudyDegreeInRepository;
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
public class StudyDegreeInService {

    @Autowired
    private StudyDegreeInRepository repository;

    /** For the duplicate-name check (M12). */
    @PersistenceContext
    private EntityManager em;

    public List<StudyDegreeIn> getAll() {
        return repository.findAll();
    }

    public StudyDegreeIn getById(int id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "StudyDegreeIn not found"));
    }

    @Transactional
    public StudyDegreeIn create(StudyDegreeIn entity) {
        LookupNameUniqueness.require(em, StudyDegreeIn.class, "studyDegreeInName",
                entity.getStudyDegreeInName(), "id", null);
        entity.setId(repository.nextId());
        return repository.save(entity);
    }

    @Transactional
    public StudyDegreeIn update(Integer id, StudyDegreeIn entity) {
        Optional<StudyDegreeIn> existingOptional = repository.findById(id);
        if (existingOptional.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "StudyDegreeIn not found");
        }
        StudyDegreeIn existing = existingOptional.get();
        if (!LookupNameUniqueness.same(existing.getStudyDegreeInName(), entity.getStudyDegreeInName())) {
            LookupNameUniqueness.require(em, StudyDegreeIn.class, "studyDegreeInName",
                    entity.getStudyDegreeInName(), "id", id);
        }
        // Every column the System Coding form edits (lookup-descriptors.ts). Copying
        // only the Arabic name dropped the English name and the Active flag while the
        // screen said "saved" (QA CRUD matrix, cause [A]).
        existing.setStudyDegreeInName(entity.getStudyDegreeInName());
        existing.setStudyDegreeInNameEn(entity.getStudyDegreeInNameEn());
        if (entity.getIsActive() != null) existing.setIsActive(entity.getIsActive());
        return repository.save(existing);
    }

    /** 404 for an id with no row (round-5 QA R5-22): deleteById is silent then, and the screen read success. */
    @Transactional
    public void delete(Integer id) {
        if (id == null || !repository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Degree-in not found");
        }
        repository.deleteById(id);
    }
} 