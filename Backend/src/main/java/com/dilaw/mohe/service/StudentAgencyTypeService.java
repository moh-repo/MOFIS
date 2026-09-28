package com.dilaw.mohe.service;

import com.dilaw.mohe.model.StudentAgencyType;
import com.dilaw.mohe.repository.StudentAgencyTypeRepository;
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
public class StudentAgencyTypeService {

    @Autowired
    private StudentAgencyTypeRepository repository;

    /** For the duplicate-name check (M12). */
    @PersistenceContext
    private EntityManager em;

    public List<StudentAgencyType> getAll() {
        return repository.findAll();
    }

    public StudentAgencyType getById(int id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "StudentAgencyType not found"));
    }

    @Transactional
    public StudentAgencyType create(StudentAgencyType entity) {
        LookupNameUniqueness.require(em, StudentAgencyType.class, "studentAgencyTypeName",
                entity.getStudentAgencyTypeName(), "id", null);
        entity.setId(repository.nextId());
        return repository.save(entity);
    }

    @Transactional
    public StudentAgencyType update(Integer id, StudentAgencyType entity) {
        Optional<StudentAgencyType> existingOptional = repository.findById(id);
        if (existingOptional.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "StudentAgencyType not found");
        }
        StudentAgencyType existing = existingOptional.get();
        if (!LookupNameUniqueness.same(existing.getStudentAgencyTypeName(), entity.getStudentAgencyTypeName())) {
            LookupNameUniqueness.require(em, StudentAgencyType.class, "studentAgencyTypeName",
                    entity.getStudentAgencyTypeName(), "id", id);
        }
        // Every column the System Coding form edits (lookup-descriptors.ts). Copying
        // only the Arabic name dropped the English name, the order and the Active
        // flag while the screen said "saved" (QA CRUD matrix, cause [A]).
        existing.setStudentAgencyTypeName(entity.getStudentAgencyTypeName());
        existing.setStudentAgencyTypeNameEn(entity.getStudentAgencyTypeNameEn());
        if (entity.getStudentAgencyTypeOrder() != null) {
            existing.setStudentAgencyTypeOrder(entity.getStudentAgencyTypeOrder());
        }
        if (entity.getIsActive() != null) existing.setIsActive(entity.getIsActive());
        return repository.save(existing);
    }

    /** 404 for an id with no row (round-5 QA R5-22): deleteById is silent then, and the screen read success. */
    @Transactional
    public void delete(Integer id) {
        if (id == null || !repository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Agency type not found");
        }
        repository.deleteById(id);
    }
} 