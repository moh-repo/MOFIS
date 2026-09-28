package com.dilaw.mohe.service;

import com.dilaw.mohe.model.StudyFinanceDofYear;
import com.dilaw.mohe.repository.StudyFinanceDofYearRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

@Transactional(readOnly = true)
@Service
public class StudyFinanceDofYearService {

    @Autowired
    private StudyFinanceDofYearRepository repository;

    public List<StudyFinanceDofYear> getAll() {
        return repository.findAll();
    }

    public StudyFinanceDofYear getById(int id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "StudyFinanceDofYear not found"));
    }

    @Transactional
    public StudyFinanceDofYear create(StudyFinanceDofYear entity) {
        return repository.save(entity);
    }

    @Transactional
    public StudyFinanceDofYear update(Integer id, StudyFinanceDofYear entity) {
        Optional<StudyFinanceDofYear> existingOptional = repository.findById(id);
        if (existingOptional.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "StudyFinanceDofYear not found");
        }
        entity.setId(id);
        return repository.save(entity);
    }

    /** 404 for an id with no row (round-5 QA R5-22): deleteById is silent then, and the screen read success. */
    @Transactional
    public void delete(Integer id) {
        if (id == null || !repository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Finance year not found");
        }
        repository.deleteById(id);
    }
} 