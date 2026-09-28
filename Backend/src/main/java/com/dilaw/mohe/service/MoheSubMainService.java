package com.dilaw.mohe.service;

import com.dilaw.mohe.model.MoheSubMain;
import com.dilaw.mohe.repository.MoheSubMainRepository;
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
public class MoheSubMainService {

    @Autowired
    private MoheSubMainRepository repository;

    /** For the duplicate-name check (M12), unique within the parent main. */
    @PersistenceContext
    private EntityManager em;

    public List<MoheSubMain> getAll() {
        return repository.findAll();
    }

    public MoheSubMain getById(int id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "MoheSubMain not found"));
    }

    @Transactional
    public MoheSubMain create(MoheSubMain entity) {
        LookupNameUniqueness.require(em, MoheSubMain.class, "moheSubMainName", entity.getMoheSubMainName(),
                "id", null, "moheMainId", entity.getMoheMainId());
        entity.setId(repository.nextId());
        return repository.save(entity);
    }

    @Transactional
    public MoheSubMain update(Integer id, MoheSubMain entity) {
        Optional<MoheSubMain> existingOptional = repository.findById(id);
        if (existingOptional.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "MoheSubMain not found");
        }
        MoheSubMain existing = existingOptional.get();
        Integer parent = entity.getMoheMainId() != null ? entity.getMoheMainId() : existing.getMoheMainId();
        if (!LookupNameUniqueness.unchanged(existing.getMoheSubMainName(), entity.getMoheSubMainName(),
                existing.getMoheMainId(), parent)) {
            LookupNameUniqueness.require(em, MoheSubMain.class, "moheSubMainName", entity.getMoheSubMainName(),
                    "id", id, "moheMainId", parent);
        }
        // Every column the System Coding form edits (lookup-descriptors.ts). Copying
        // only the Arabic name and the parent dropped the English name, the order and
        // the Active flag while the screen said "saved" (QA CRUD matrix, cause [A]).
        existing.setMoheSubMainName(entity.getMoheSubMainName());
        existing.setMoheSubMainNameEn(entity.getMoheSubMainNameEn());
        if (entity.getMoheMainId() != null) existing.setMoheMainId(entity.getMoheMainId());
        if (entity.getMoheSubMainOrder() != null) existing.setMoheSubMainOrder(entity.getMoheSubMainOrder());
        if (entity.getIsActive() != null) existing.setIsActive(entity.getIsActive());
        return repository.save(existing);
    }

    /** 404 for an id with no row (round-5 QA R5-22): deleteById is silent then, and the screen read success. */
    @Transactional
    public void delete(Integer id) {
        if (id == null || !repository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "MOHE sub-main not found");
        }
        repository.deleteById(id);
    }
} 