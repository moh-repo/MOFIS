package com.dilaw.mohe.service;

import com.dilaw.mohe.model.StudentCountryAll;
import com.dilaw.mohe.repository.StudentCountryAllRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

@Transactional(readOnly = true)
@Service
public class StudentCountryAllService {

    @Autowired
    private StudentCountryAllRepository repository;

    public List<StudentCountryAll> getAll() {
        return repository.findAll();
    }

    public StudentCountryAll getById(int id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "StudentCountryAll not found"));
    }

    @Transactional
    public StudentCountryAll create(StudentCountryAll entity) {
        return repository.save(entity);
    }

    @Transactional
    public StudentCountryAll update(Integer id, StudentCountryAll entity) {
        Optional<StudentCountryAll> existingOptional = repository.findById(id);
        if (existingOptional.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "StudentCountryAll not found");
        }
        entity.setId(id);
        return repository.save(entity);
    }

    /** 404 for an id with no row (round-5 QA R5-22): deleteById is silent then, and the screen read success. */
    @Transactional
    public void delete(Integer id) {
        if (id == null || !repository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Country not found");
        }
        repository.deleteById(id);
    }
} 