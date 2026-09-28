package com.dilaw.mohe.service;

import com.dilaw.mohe.audit.AuditLoggingService;
import com.dilaw.mohe.model.MoheMain;
import com.dilaw.mohe.repository.MoheMainRepository;
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
public class MoheMainService {

    @Autowired
    private MoheMainRepository repository;

    /** For the duplicate-name check (M12). */
    @PersistenceContext
    private EntityManager em;

    @Autowired
    private AuditLoggingService auditLoggingService;

    public List<MoheMain> getAll() {
        return repository.findAll();
    }

    public MoheMain getById(int id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "MoheMain not found"));
    }

    @Transactional
    public MoheMain create(MoheMain entity) {
        LookupNameUniqueness.require(em, MoheMain.class, "moheMainName", entity.getMoheMainName(), "id", null);
        entity.setId(repository.nextId());
        return repository.save(entity);
    }

    @Transactional
    public MoheMain update(Integer id, MoheMain entity) {
        Optional<MoheMain> existingOptional = repository.findById(id);
        if (existingOptional.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "MoheMain not found");
        }
        MoheMain oldEntity = existingOptional.get();
        if (!LookupNameUniqueness.same(oldEntity.getMoheMainName(), entity.getMoheMainName())) {
            LookupNameUniqueness.require(em, MoheMain.class, "moheMainName", entity.getMoheMainName(), "id", id);
        }
        // The body replaces the row, so a flag it does not mention keeps the stored value (L1).
        if (entity.getIsActive() == null) entity.setIsActive(oldEntity.getIsActive());
        entity.setId(id);
        MoheMain updatedEntity = repository.save(entity);
        
        // Log the update
        auditLoggingService.logUpdate(updatedEntity, oldEntity);
        
        return updatedEntity;
    }

    @Transactional
    public void delete(Integer id) {
        Optional<MoheMain> existingOptional = repository.findById(id);
        if (existingOptional.isPresent()) {
            MoheMain entity = existingOptional.get();
            repository.deleteById(id);
            
            // Log the delete
            auditLoggingService.logDelete(entity);
        } else {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "MoheMain not found");
        }
    }
} 