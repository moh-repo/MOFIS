package com.dilaw.mohe.service;

import com.dilaw.mohe.model.StudyFinReceivedWhomStatus;
import com.dilaw.mohe.repository.StudyFinReceivedWhomStatusRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

@Transactional(readOnly = true)
@Service
public class StudyFinReceivedWhomStatusService {

    @Autowired
    private StudyFinReceivedWhomStatusRepository repository;

    public List<StudyFinReceivedWhomStatus> getAll() {
        return repository.findAll();
    }

    public StudyFinReceivedWhomStatus getById(int id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "StudyFinReceivedWhomStatus not found"));
    }

    @Transactional
    public StudyFinReceivedWhomStatus create(StudyFinReceivedWhomStatus entity) {
        return repository.save(entity);
    }

    @Transactional
    public StudyFinReceivedWhomStatus update(Integer id, StudyFinReceivedWhomStatus entity) {
        Optional<StudyFinReceivedWhomStatus> existingOptional = repository.findById(id);
        if (existingOptional.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "StudyFinReceivedWhomStatus not found");
        }
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