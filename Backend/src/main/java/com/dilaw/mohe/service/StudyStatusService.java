package com.dilaw.mohe.service;

import com.dilaw.mohe.model.StudyStatus;
import com.dilaw.mohe.repository.StudyStatusRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

@Transactional(readOnly = true)
@Service
public class StudyStatusService {

    @Autowired
    private StudyStatusRepository repository;

    public List<StudyStatus> getAll() {
        return repository.findAll();
    }

    public StudyStatus getById(int id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "StudyStatus not found"));
    }

    @Transactional
        public StudyStatus create(StudyStatus entity) {
        return repository.save(entity);
    }

    @Transactional
        public StudyStatus update(Integer id, StudyStatus entity) {
        Optional<StudyStatus> existingOptional = repository.findById(id);
        if (existingOptional.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "StudyStatus not found");
        }
        // The body replaces the row, so a flag it does not mention keeps the stored value (M12).
        if (entity.getIsActive() == null) entity.setIsActive(existingOptional.get().getIsActive());
        entity.setId(id);
        return repository.save(entity);
    }

    /** 404 for an id with no row (round-5 QA R5-22): deleteById is silent then, and the screen read success. */
    @Transactional
        public void delete(Integer id) {
        if (id == null || !repository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Study status not found");
        }
        repository.deleteById(id);
    }
} 