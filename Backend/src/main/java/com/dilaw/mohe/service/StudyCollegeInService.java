package com.dilaw.mohe.service;

import com.dilaw.mohe.model.StudyCollegeIn;
import com.dilaw.mohe.repository.StudyCollegeInRepository;
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
public class StudyCollegeInService {

    @Autowired
    private StudyCollegeInRepository repository;

    /** For the duplicate-name check (M12), unique within the university. */
    @PersistenceContext
    private EntityManager em;

    public List<StudyCollegeIn> getAll() {
        return repository.findAll();
    }

    public StudyCollegeIn getById(int id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "StudyCollegeIn not found"));
    }

    @Transactional
    public StudyCollegeIn create(StudyCollegeIn entity) {
        LookupNameUniqueness.require(em, StudyCollegeIn.class, "studyCollegeInName", entity.getStudyCollegeInName(),
                "id", null, "studyUniversityInId", entity.getStudyUniversityInId());
        entity.setId(repository.nextId());
        return repository.save(entity);
    }

    @Transactional
    public StudyCollegeIn update(Integer id, StudyCollegeIn entity) {
        Optional<StudyCollegeIn> existingOptional = repository.findById(id);
        if (existingOptional.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "StudyCollegeIn not found");
        }
        StudyCollegeIn existing = existingOptional.get();
        if (!LookupNameUniqueness.unchanged(existing.getStudyCollegeInName(), entity.getStudyCollegeInName(),
                existing.getStudyUniversityInId(), entity.getStudyUniversityInId())) {
            LookupNameUniqueness.require(em, StudyCollegeIn.class, "studyCollegeInName", entity.getStudyCollegeInName(),
                    "id", id, "studyUniversityInId", entity.getStudyUniversityInId());
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
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "College not found");
        }
        repository.deleteById(id);
    }
} 