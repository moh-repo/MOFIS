package com.dilaw.mohe.service;

import com.dilaw.mohe.config.CacheConfig;
import com.dilaw.mohe.model.StudyCountryIn;
import com.dilaw.mohe.exception.LocalizedStatusException;
import com.dilaw.mohe.repository.StudyCountryInRepository;
import com.dilaw.mohe.repository.StudyGovInRepository;
import com.dilaw.mohe.util.LookupNameUniqueness;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

@Transactional(readOnly = true)
@Service
public class StudyCountryInService {

    @Autowired
    private StudyCountryInRepository repository;

    @Autowired
    private StudyGovInRepository govRepository;

    /** For the duplicate-name check (M12). */
    @PersistenceContext
    private EntityManager em;

    public List<StudyCountryIn> getAll() {
        return repository.findAll();
    }

    public StudyCountryIn getById(int id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "StudyCountryIn not found"));
    }

    // Every write evicts the CountryNameService snapshot, so its 10-minute TTL
    // only matters for edits that bypass this service (SSMS).
    @Transactional
    @CacheEvict(cacheNames = CacheConfig.COUNTRY_NAMES, allEntries = true)
    public StudyCountryIn create(StudyCountryIn entity) {
        LookupNameUniqueness.require(em, StudyCountryIn.class, "studyCountryInName",
                entity.getStudyCountryInName(), "id", null);
        entity.setId(repository.nextId());
        return repository.save(entity);
    }

    @Transactional
    @CacheEvict(cacheNames = CacheConfig.COUNTRY_NAMES, allEntries = true)
    public StudyCountryIn update(Integer id, StudyCountryIn entity) {
        Optional<StudyCountryIn> existingOptional = repository.findById(id);
        if (existingOptional.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "StudyCountryIn not found");
        }
        StudyCountryIn existing = existingOptional.get();
        if (!LookupNameUniqueness.same(existing.getStudyCountryInName(), entity.getStudyCountryInName())) {
            LookupNameUniqueness.require(em, StudyCountryIn.class, "studyCountryInName",
                    entity.getStudyCountryInName(), "id", id);
        }
        // The body replaces the row, so a flag it does not mention keeps the stored value (L1).
        if (entity.getIsActive() == null) entity.setIsActive(existing.getIsActive());
        entity.setId(id);
        return repository.save(entity);
    }

    @Transactional
    @CacheEvict(cacheNames = CacheConfig.COUNTRY_NAMES, allEntries = true)
    /**
     * Refuses a delete that would orphan the governorates under this country
     * (plan W6-06, QA C9). V129's foreign key would refuse it too, as a
     * constraint violation the caller reads as a 500; counted here it is a 409
     * that names what is in the way.
     *
     * <p>An id with no row is 404 (round-5 QA R5-22): deleteById is silent then,
     * and the screen read success.
     */
    public void delete(Integer id) {
        if (id == null || !repository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Study country not found");
        }
        int children = govRepository.findByStudyCountryInId(id).size();
        if (children > 0) {
            throw new LocalizedStatusException(org.springframework.http.HttpStatus.CONFLICT,
                    "This country still has " + children + " governorate" + (children == 1 ? "" : "s")
                            + "; move or remove them first.",
                    "location.country.hasGovernorates", children);
        }
        repository.deleteById(id);
    }
} 