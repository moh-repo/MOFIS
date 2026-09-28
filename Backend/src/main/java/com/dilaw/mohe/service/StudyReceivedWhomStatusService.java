package com.dilaw.mohe.service;

import com.dilaw.mohe.model.StudyReceivedWhomStatus;
import com.dilaw.mohe.repository.StudyReceivedWhomStatusRepository;
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
public class StudyReceivedWhomStatusService {

    @Autowired
    private StudyReceivedWhomStatusRepository repository;

    /** For the duplicate-name check (M12). */
    @PersistenceContext
    private EntityManager em;

    public List<StudyReceivedWhomStatus> getAll() {
        return repository.findAll();
    }

    public StudyReceivedWhomStatus getById(int id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "StudyReceivedWhomStatus not found"));
    }

    @Transactional
        public StudyReceivedWhomStatus create(StudyReceivedWhomStatus entity) {
        LookupNameUniqueness.require(em, StudyReceivedWhomStatus.class, "studyReceivedWhomStatusName",
                entity.getStudyReceivedWhomStatusName(), "id", null);
        entity.setId(repository.nextId());
        return repository.save(entity);
    }

    @Transactional
        public StudyReceivedWhomStatus update(Integer id, StudyReceivedWhomStatus entity) {
        Optional<StudyReceivedWhomStatus> existingOptional = repository.findById(id);
        if (existingOptional.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "StudyReceivedWhomStatus not found");
        }
        StudyReceivedWhomStatus existing = existingOptional.get();
        if (!LookupNameUniqueness.same(existing.getStudyReceivedWhomStatusName(), entity.getStudyReceivedWhomStatusName())) {
            LookupNameUniqueness.require(em, StudyReceivedWhomStatus.class, "studyReceivedWhomStatusName",
                    entity.getStudyReceivedWhomStatusName(), "id", id);
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
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Received-whom status not found");
        }
        repository.deleteById(id);
    }
} 