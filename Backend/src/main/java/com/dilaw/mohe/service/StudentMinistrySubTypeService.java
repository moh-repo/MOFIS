package com.dilaw.mohe.service;

import com.dilaw.mohe.model.StudentMinistrySubType;
import com.dilaw.mohe.repository.StudentMinistrySubTypeRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

@Transactional(readOnly = true)
@Service
public class StudentMinistrySubTypeService {

    @Autowired
    private StudentMinistrySubTypeRepository repository;

    public List<StudentMinistrySubType> getAll() {
        return repository.findAll();
    }

    public StudentMinistrySubType getById(int id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "StudentMinistrySubType not found"));
    }

    @Transactional
    public StudentMinistrySubType create(StudentMinistrySubType entity) {
        return repository.save(entity);
    }

    @Transactional
    public StudentMinistrySubType update(Integer id, StudentMinistrySubType entity) {
        Optional<StudentMinistrySubType> existingOptional = repository.findById(id);
        if (existingOptional.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "StudentMinistrySubType not found");
        }
        entity.setId(id);
        return repository.save(entity);
    }

    /** 404 for an id with no row (round-5 QA R5-22): deleteById is silent then, and the screen read success. */
    @Transactional
    public void delete(Integer id) {
        if (id == null || !repository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Ministry sub-type not found");
        }
        repository.deleteById(id);
    }
} 