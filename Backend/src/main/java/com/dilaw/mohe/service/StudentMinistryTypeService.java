package com.dilaw.mohe.service;

import com.dilaw.mohe.model.StudentMinistryType;
import com.dilaw.mohe.repository.StudentMinistryTypeRepository;
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
public class StudentMinistryTypeService {

    @Autowired
    private StudentMinistryTypeRepository repository;

    /** For the duplicate-name check (M12), unique within the agency type. */
    @PersistenceContext
    private EntityManager em;

    public List<StudentMinistryType> getAll() {
        return repository.findAll();
    }

    public StudentMinistryType getById(int id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "StudentMinistryType not found"));
    }

    @Transactional
    public StudentMinistryType create(StudentMinistryType entity) {
        LookupNameUniqueness.require(em, StudentMinistryType.class, "studentMinistryTypeName",
                entity.getStudentMinistryTypeName(), "id", null, "studentAgencyTypeId", entity.getStudentAgencyTypeId());
        entity.setId(repository.nextId());
        return repository.save(entity);
    }

    @Transactional
    public StudentMinistryType update(Integer id, StudentMinistryType entity) {
        Optional<StudentMinistryType> existingOptional = repository.findById(id);
        if (existingOptional.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "StudentMinistryType not found");
        }
        StudentMinistryType existing = existingOptional.get();
        if (!LookupNameUniqueness.unchanged(existing.getStudentMinistryTypeName(), entity.getStudentMinistryTypeName(),
                existing.getStudentAgencyTypeId(), entity.getStudentAgencyTypeId())) {
            LookupNameUniqueness.require(em, StudentMinistryType.class, "studentMinistryTypeName",
                    entity.getStudentMinistryTypeName(), "id", id, "studentAgencyTypeId", entity.getStudentAgencyTypeId());
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
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Ministry type not found");
        }
        repository.deleteById(id);
    }
} 