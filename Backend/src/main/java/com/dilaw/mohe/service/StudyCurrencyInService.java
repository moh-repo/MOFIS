package com.dilaw.mohe.service;

import com.dilaw.mohe.model.StudyCurrencyIn;
import com.dilaw.mohe.repository.StudyCurrencyInRepository;
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
public class StudyCurrencyInService {

    @Autowired
    private StudyCurrencyInRepository repository;

    /** For the duplicate-name check (M12). */
    @PersistenceContext
    private EntityManager em;

    public List<StudyCurrencyIn> getAll() {
        return repository.findAll();
    }

    public StudyCurrencyIn getById(int id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "StudyCurrencyIn not found"));
    }

    @Transactional
    public StudyCurrencyIn create(StudyCurrencyIn entity) {
        LookupNameUniqueness.require(em, StudyCurrencyIn.class, "studyCurrencyInName",
                entity.getStudyCurrencyInName(), "id", null);
        entity.setId(repository.nextId());
        return repository.save(entity);
    }

    @Transactional
    public StudyCurrencyIn update(Integer id, StudyCurrencyIn entity) {
        Optional<StudyCurrencyIn> existingOptional = repository.findById(id);
        if (existingOptional.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "StudyCurrencyIn not found");
        }
        StudyCurrencyIn existing = existingOptional.get();
        if (!LookupNameUniqueness.same(existing.getStudyCurrencyInName(), entity.getStudyCurrencyInName())) {
            LookupNameUniqueness.require(em, StudyCurrencyIn.class, "studyCurrencyInName",
                    entity.getStudyCurrencyInName(), "id", id);
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
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Currency not found");
        }
        repository.deleteById(id);
    }
} 