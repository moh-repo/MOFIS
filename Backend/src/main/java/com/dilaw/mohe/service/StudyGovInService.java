package com.dilaw.mohe.service;

import com.dilaw.mohe.model.StudyGovIn;
import com.dilaw.mohe.repository.StudyGovInRepository;
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
public class StudyGovInService {

    @Autowired
    private StudyGovInRepository repository;

    /** For the duplicate-name check (M12), unique within the country. */
    @PersistenceContext
    private EntityManager em;

    public List<StudyGovIn> getAll() {
        return repository.findAll();
    }

    public StudyGovIn getById(int id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "StudyGovIn not found"));
    }

    @Transactional
    public StudyGovIn create(StudyGovIn entity) {
        LookupNameUniqueness.require(em, StudyGovIn.class, "studyGovInName", entity.getStudyGovInName(),
                "id", null, "studyCountryInId", entity.getStudyCountryInId());
        entity.setId(repository.nextId());
        return repository.save(entity);
    }

    @Transactional
    public StudyGovIn update(Integer id, StudyGovIn entity) {
        Optional<StudyGovIn> existingOptional = repository.findById(id);
        if (existingOptional.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "StudyGovIn not found");
        }
        StudyGovIn existing = existingOptional.get();
        if (!LookupNameUniqueness.unchanged(existing.getStudyGovInName(), entity.getStudyGovInName(),
                existing.getStudyCountryInId(), entity.getStudyCountryInId())) {
            LookupNameUniqueness.require(em, StudyGovIn.class, "studyGovInName", entity.getStudyGovInName(),
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
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Study governorate not found");
        }
        repository.deleteById(id);
    }
} 