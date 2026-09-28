package com.dilaw.mohe.service;

import com.dilaw.mohe.model.StudyUniversityIn;
import com.dilaw.mohe.repository.StudyUniversityInRepository;
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
public class StudyUniversityInService {

    @Autowired
    private StudyUniversityInRepository repository;

    /** For the duplicate-name check (M12), unique within the country. */
    @PersistenceContext
    private EntityManager em;

    public List<StudyUniversityIn> getAll() {
        return repository.findAll();
    }

    public StudyUniversityIn getById(int id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "StudyUniversityIn not found"));
    }

    @Transactional
    public StudyUniversityIn create(StudyUniversityIn entity) {
        LookupNameUniqueness.require(em, StudyUniversityIn.class, "studyUniversityInName", entity.getStudyUniversityInName(),
                "id", null, "studyCountryInId", entity.getStudyCountryInId());
        entity.setId(repository.nextId());
        return repository.save(entity);
    }

    @Transactional
    public StudyUniversityIn update(Integer id, StudyUniversityIn entity) {
        Optional<StudyUniversityIn> existingOptional = repository.findById(id);
        if (existingOptional.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "StudyUniversityIn not found");
        }
        StudyUniversityIn existing = existingOptional.get();
        if (!LookupNameUniqueness.unchanged(existing.getStudyUniversityInName(), entity.getStudyUniversityInName(),
                existing.getStudyCountryInId(), entity.getStudyCountryInId())) {
            LookupNameUniqueness.require(em, StudyUniversityIn.class, "studyUniversityInName", entity.getStudyUniversityInName(),
                    "id", id, "studyCountryInId", entity.getStudyCountryInId());
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
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "University not found");
        }
        repository.deleteById(id);
    }
} 