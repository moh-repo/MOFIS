package com.dilaw.mohe.service;

import com.dilaw.mohe.model.StudyEductionIn;
import com.dilaw.mohe.repository.StudyEductionInRepository;
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
public class StudyEductionInService {

    @Autowired
    private StudyEductionInRepository repository;

    /** For the duplicate-name check (M12), unique within the college. */
    @PersistenceContext
    private EntityManager em;

    public List<StudyEductionIn> getAll() {
        return repository.findAll();
    }

    public StudyEductionIn getById(int id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "StudyEductionIn not found"));
    }

    @Transactional
    public StudyEductionIn create(StudyEductionIn entity) {
        LookupNameUniqueness.require(em, StudyEductionIn.class, "studyEductionName", entity.getStudyEductionName(),
                "id", null, "studyCollegeInId", entity.getStudyCollegeInId());
        entity.setId(repository.nextId());
        return repository.save(entity);
    }

    @Transactional
    public StudyEductionIn update(Integer id, StudyEductionIn entity) {
        Optional<StudyEductionIn> existingOptional = repository.findById(id);
        if (existingOptional.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "StudyEductionIn not found");
        }
        StudyEductionIn existing = existingOptional.get();
        if (!LookupNameUniqueness.unchanged(existing.getStudyEductionName(), entity.getStudyEductionName(),
                existing.getStudyCollegeInId(), entity.getStudyCollegeInId())) {
            LookupNameUniqueness.require(em, StudyEductionIn.class, "studyEductionName", entity.getStudyEductionName(),
                    "id", id, "studyCollegeInId", entity.getStudyCollegeInId());
        }
        // The body replaces the row, so a flag it does not mention keeps the stored value (L1).
        if (entity.getIsActive() == null) entity.setIsActive(existing.getIsActive());
        entity.setId(id);
        return repository.save(entity);
    }

    public List<StudyEductionIn> getByCollegeId(Integer collegeId) {
        return repository.findByStudyCollegeInId(collegeId);
    }

    /** 404 for an id with no row (round-5 QA R5-22): deleteById is silent then, and the screen read success. */
    @Transactional
    public void delete(Integer id) {
        if (id == null || !repository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Education not found");
        }
        repository.deleteById(id);
    }
}