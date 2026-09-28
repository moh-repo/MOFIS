package com.dilaw.mohe.service;

import com.dilaw.mohe.model.StudentQuarter;
import com.dilaw.mohe.repository.StudentQuarterRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

@Transactional(readOnly = true)
@Service
public class StudentQuarterService {

    @Autowired
    private StudentQuarterRepository repository;

    public List<StudentQuarter> getAll() {
        return repository.findAll();
    }

    public StudentQuarter getById(int id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "StudentQuarter not found"));
    }

    @Transactional
    public StudentQuarter create(StudentQuarter entity) {
        return repository.save(entity);
    }

    @Transactional
    public StudentQuarter update(Integer id, StudentQuarter entity) {
        Optional<StudentQuarter> existingOptional = repository.findById(id);
        if (existingOptional.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "StudentQuarter not found");
        }
        entity.setId(id);
        return repository.save(entity);
    }

    /** 404 for an id with no row (round-5 QA R5-22): deleteById is silent then, and the caller read success. */
    @Transactional
    public void delete(Integer id) {
        if (id == null || !repository.existsById(id)) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.NOT_FOUND, "Student quarter not found");
        }
        repository.deleteById(id);
    }
} 