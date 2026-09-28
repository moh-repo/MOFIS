package com.dilaw.mohe.service;

import com.dilaw.mohe.model.StudyPayFeeInStatus;
import com.dilaw.mohe.repository.StudyPayFeeInStatusRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

@Transactional(readOnly = true)
@Service
public class StudyPayFeeInStatusService {

    @Autowired
    private StudyPayFeeInStatusRepository repository;

    public List<StudyPayFeeInStatus> getAll() {
        return repository.findAll();
    }

    public StudyPayFeeInStatus getById(int id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "StudyPayFeeInStatus not found"));
    }

    @Transactional
    public StudyPayFeeInStatus create(StudyPayFeeInStatus entity) {
        return repository.save(entity);
    }

    @Transactional
    public StudyPayFeeInStatus update(Integer id, StudyPayFeeInStatus entity) {
        Optional<StudyPayFeeInStatus> existingOptional = repository.findById(id);
        if (existingOptional.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "StudyPayFeeInStatus not found");
        }
        entity.setId(id);
        return repository.save(entity);
    }

    /** 404 for an id with no row (round-5 QA R5-22): deleteById is silent then, and the screen read success. */
    @Transactional
    public void delete(Integer id) {
        if (id == null || !repository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Fee payment status not found");
        }
        repository.deleteById(id);
    }
} 