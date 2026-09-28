package com.dilaw.mohe.service;

import com.dilaw.mohe.model.StudentUniversityType;
import com.dilaw.mohe.repository.StudentUniversityTypeRepository;
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
public class StudentUniversityTypeService {

    @Autowired
    private StudentUniversityTypeRepository repository;

    /** For the duplicate-name check (M12), unique within the agency type. */
    @PersistenceContext
    private EntityManager em;

    public List<StudentUniversityType> getAll() {
        return repository.findAll();
    }

    public StudentUniversityType getById(int id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "StudentUniversityType not found"));
    }

    @Transactional
    public StudentUniversityType create(StudentUniversityType entity) {
        LookupNameUniqueness.require(em, StudentUniversityType.class, "studentUniversityTypeName",
                entity.getStudentUniversityTypeName(), "id", null, "studentAgencyTypeId", entity.getStudentAgencyTypeId());
        entity.setId(repository.nextId());
        return repository.save(entity);
    }

    @Transactional
    public StudentUniversityType update(Integer id, StudentUniversityType entity) {
        Optional<StudentUniversityType> existingOptional = repository.findById(id);
        if (existingOptional.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "StudentUniversityType not found");
        }
        StudentUniversityType existing = existingOptional.get();
        Integer parent = entity.getStudentAgencyTypeId() != null ? entity.getStudentAgencyTypeId() : existing.getStudentAgencyTypeId();
        if (!LookupNameUniqueness.unchanged(existing.getStudentUniversityTypeName(), entity.getStudentUniversityTypeName(),
                existing.getStudentAgencyTypeId(), parent)) {
            LookupNameUniqueness.require(em, StudentUniversityType.class, "studentUniversityTypeName",
                    entity.getStudentUniversityTypeName(), "id", id, "studentAgencyTypeId", parent);
        }
        // Every column the System Coding form edits (lookup-descriptors.ts). Copying
        // only the Arabic name dropped the English name, the Active flag and the
        // agency type while the screen said "saved" (QA CRUD matrix, cause [A]).
        existing.setStudentUniversityTypeName(entity.getStudentUniversityTypeName());
        existing.setStudentUniversityTypeNameEn(entity.getStudentUniversityTypeNameEn());
        if (entity.getIsActive() != null) existing.setIsActive(entity.getIsActive());
        if (entity.getStudentAgencyTypeId() != null) existing.setStudentAgencyTypeId(entity.getStudentAgencyTypeId());
        return repository.save(existing);
    }
    
    /** 404 for an id with no row (round-5 QA R5-22): deleteById is silent then, and the screen read success. */
    @Transactional
    public void delete(Integer id) {
        if (id == null || !repository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "University type not found");
        }
        repository.deleteById(id);
    }
} 