package com.dilaw.mohe.service;

import com.dilaw.mohe.model.StudyMajorIn;
import com.dilaw.mohe.repository.StudyMajorInRepository;
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
public class StudyMajorInService {

    @Autowired
    private StudyMajorInRepository repository;

    /** For the duplicate-name check (M12), unique within the education. */
    @PersistenceContext
    private EntityManager em;

    public List<StudyMajorIn> getAll() {
        return repository.findAll();
    }

    public StudyMajorIn getById(int id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "StudyMajorIn not found"));
    }

    @Transactional
    public StudyMajorIn create(StudyMajorIn entity) {
        LookupNameUniqueness.require(em, StudyMajorIn.class, "studyMajorName", entity.getStudyMajorName(),
                "id", null, "studyEductionId", entity.getStudyEductionId());
        entity.setId(repository.nextId());
        return repository.save(entity);
    }

    @Transactional
    public StudyMajorIn update(Integer id, StudyMajorIn entity) {
        Optional<StudyMajorIn> existingOptional = repository.findById(id);
        if (existingOptional.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "StudyMajorIn not found");
        }
        StudyMajorIn existing = existingOptional.get();
        if (!LookupNameUniqueness.unchanged(existing.getStudyMajorName(), entity.getStudyMajorName(),
                existing.getStudyEductionId(), entity.getStudyEductionId())) {
            LookupNameUniqueness.require(em, StudyMajorIn.class, "studyMajorName", entity.getStudyMajorName(),
                    "id", id, "studyEductionId", entity.getStudyEductionId());
        }
        // The body replaces the row, so a flag it does not mention keeps the stored value (L1).
        if (entity.getIsActive() == null) entity.setIsActive(existing.getIsActive());
        entity.setId(id);
        return repository.save(entity);
    }

    public List<StudyMajorIn> getByEducationId(Integer educationId) {
        return repository.findByStudyEductionId(educationId);
    }

    /** 404 for an id with no row (round-5 QA R5-22): deleteById is silent then, and the screen read success. */
    @Transactional
    public void delete(Integer id) {
        if (id == null || !repository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Study major not found");
        }
        repository.deleteById(id);
    }
} 