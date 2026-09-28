package com.dilaw.mohe.service;

import com.dilaw.mohe.model.StudentGovMud;
import com.dilaw.mohe.repository.StudentGovMudRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

@Transactional(readOnly = true)
@Service
public class StudentGovMudService {

    @Autowired
    private StudentGovMudRepository repository;

    public List<StudentGovMud> getAll() {
        return repository.findAll();
    }

    public List<StudentGovMud> getAllByGovId(Integer govId) {
        return repository.findByStudentGovId(govId);
    }

    public List<StudentGovMud> getAllByGovIdAndCountryId(Integer govId, Integer countryId) {
        return repository.findByStudentGovIdAndStudentCountryId(govId, countryId);
    }

    public StudentGovMud getById(int id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "StudentGovMud not found"));
    }

    @Transactional
    public StudentGovMud create(StudentGovMud entity) {
        return repository.save(entity);
    }

    @Transactional
    public StudentGovMud update(Integer id, StudentGovMud entity) {
        Optional<StudentGovMud> existingOptional = repository.findById(id);
        if (existingOptional.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "StudentGovMud not found");
        }
        // The body replaces the row, so what it does not mention keeps the stored value (M12).
        StudentGovMud existing = existingOptional.get();
        if (entity.getIsActive() == null) entity.setIsActive(existing.getIsActive());
        if (entity.getStudentMidate() == null) entity.setStudentMidate(existing.getStudentMidate());
        entity.setId(id);
        return repository.save(entity);
    }

    /** 404 for an id with no row (round-5 QA R5-22): deleteById is silent then, and the screen read success. */
    @Transactional
    public void delete(Integer id) {
        if (id == null || !repository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Municipality not found");
        }
        repository.deleteById(id);
    }
} 