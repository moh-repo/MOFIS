package com.dilaw.mohe.service;

import com.dilaw.mohe.dto.StudyWafarTypeRequest;
import com.dilaw.mohe.model.StudyWafarType;
import com.dilaw.mohe.repository.StudyWafarTypeRepository;
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
public class StudyWafarTypeService {

    @Autowired
    private StudyWafarTypeRepository repository;

    /** For the duplicate-name check (M12). */
    @PersistenceContext
    private EntityManager em;

    public List<StudyWafarType> getAll() {
        return repository.findAll();
    }

    public StudyWafarType getById(int id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "StudyWafarType not found"));
    }

    @Transactional
    public StudyWafarType createFromRequest(StudyWafarTypeRequest request) {
        LookupNameUniqueness.require(em, StudyWafarType.class, "wafarName", request.getWafarName(), "id", null);
        StudyWafarType entity = new StudyWafarType();
        entity.setId(repository.nextId());
        applyRequest(entity, request);
        if (entity.getIsActive() == null) {
            entity.setIsActive(true);
        }
        return repository.save(entity);
    }

    @Transactional
    public StudyWafarType updateFromRequest(Integer id, StudyWafarTypeRequest request) {
        StudyWafarType entity = getById(id);
        if (!LookupNameUniqueness.same(entity.getWafarName(), request.getWafarName())) {
            LookupNameUniqueness.require(em, StudyWafarType.class, "wafarName", request.getWafarName(), "id", id);
        }
        applyRequest(entity, request);
        entity.setId(id);
        return repository.save(entity);
    }

    /**
     * Copies what the request states onto the row.
     *
     * <p>The name and the note used to be copied unconditionally, so an edit that
     * did not mention them cleared them — and the dialog never sends the English
     * name, so every save wiped {@code WAFAR_NAME_EN}. Absent is not the same as
     * empty; the guards keep them apart.
     */
    private static void applyRequest(StudyWafarType entity, StudyWafarTypeRequest request) {
        if (request.getWafarName() != null) {
            entity.setWafarName(request.getWafarName());
        }
        if (request.getWafarNameEn() != null) {
            entity.setWafarNameEn(request.getWafarNameEn());
        }
        if (request.getIsActive() != null) {
            entity.setIsActive(request.getIsActive());
        }
        if (request.getWafarNote1() != null) {
            entity.setWafarNote1(request.getWafarNote1());
        }
    }

    @Transactional
    public StudyWafarType create(StudyWafarType entity) {
        LookupNameUniqueness.require(em, StudyWafarType.class, "wafarName", entity.getWafarName(), "id", null);
        entity.setId(repository.nextId());
        if (entity.getIsActive() == null) {
            entity.setIsActive(true);
        }
        return repository.save(entity);
    }

    /** Merges onto the stored row, for the same reason {@link #applyRequest} guards. */
    @Transactional
    public StudyWafarType update(Integer id, StudyWafarType entity) {
        StudyWafarType existing = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "StudyWafarType not found"));
        if (!LookupNameUniqueness.same(existing.getWafarName(), entity.getWafarName())) {
            LookupNameUniqueness.require(em, StudyWafarType.class, "wafarName", entity.getWafarName(), "id", id);
        }
        if (entity.getWafarName() != null) {
            existing.setWafarName(entity.getWafarName());
        }
        if (entity.getWafarNameEn() != null) {
            existing.setWafarNameEn(entity.getWafarNameEn());
        }
        if (entity.getIsActive() != null) {
            existing.setIsActive(entity.getIsActive());
        }
        if (entity.getWafarNote1() != null) {
            existing.setWafarNote1(entity.getWafarNote1());
        }
        return repository.save(existing);
    }

    /** 404 for an id with no row (round-5 QA R5-22): deleteById is silent then, and the screen read success. */
    @Transactional
    public void delete(Integer id) {
        if (id == null || !repository.existsById(id)) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.NOT_FOUND, "Wafar type not found");
        }
        repository.deleteById(id);
    }
}
