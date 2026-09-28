package com.dilaw.mohe.service;

import com.dilaw.mohe.exception.LocalizedStatusException;
import com.dilaw.mohe.model.StudyFinanceBank;
import com.dilaw.mohe.repository.StudyFinanceBankBranchRepository;
import com.dilaw.mohe.repository.StudyFinanceBankRepository;
import com.dilaw.mohe.repository.spec.CountryColumnSpec;
import com.dilaw.mohe.ums.service.CountryScopeService;
import com.dilaw.mohe.ums.service.CountryScopeService.CountryScope;
import com.dilaw.mohe.util.LookupNameUniqueness;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * The banks each attaché pays through.
 *
 * <p>Unlike the study-years and study-amount lookups, <b>a bank with no country
 * is not shared</b> — it is a bank whose attaché was never recorded, which is
 * missing data. So this uses {@code countryScope} (null is ministry-only) rather
 * than {@code countryScopeOrShared}. The distinction is the one written up in
 * {@link CountryColumnSpec}: a null means something different on every table, and
 * copying the rule across is how it goes wrong.
 *
 * <p>V049 left no nulls behind — the one bank that had no valid country was the
 * "Not Specified" sentinel, and it belongs to Egypt now.
 */
@Transactional(readOnly = true)
@Service
public class StudyFinanceBankService {

    @Autowired
    private StudyFinanceBankRepository repository;

    @Autowired
    private StudyFinanceBankBranchRepository branchRepository;

    @Autowired
    private CountryScopeService countryScopeService;

    /** For the duplicate-name check (M12), unique within the attaché's country. */
    @PersistenceContext
    private EntityManager em;

    private Specification<StudyFinanceBank> scoped() {
        return CountryColumnSpec.countryScope(countryScopeService.getScope(), "studyCountryInId");
    }

    public List<StudyFinanceBank> getAll() {
        return repository.findAll(scoped());
    }

    public StudyFinanceBank getById(int id) {
        return require(id);
    }

    /** 404 rather than 403 — bank ids are sequential and a 403 confirms which exist. */
    private StudyFinanceBank require(Integer id) {
        return repository.findOne(scoped().and((root, q, cb) -> cb.equal(root.get("id"), id)))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "StudyFinanceBank not found"));
    }

    @Transactional
    public StudyFinanceBank create(StudyFinanceBank entity) {
        entity.setId(repository.nextId());
        entity.setStudyCountryInId(resolveCountryForWrite(entity.getStudyCountryInId()));
        LookupNameUniqueness.require(em, StudyFinanceBank.class, "studyFinanceBankeName", entity.getStudyFinanceBankeName(),
                "id", null, "studyCountryInId", entity.getStudyCountryInId());
        if (entity.getIsActive() == null) {
            entity.setIsActive(true);
        }
        return repository.save(entity);
    }

    /**
     * Copies what the request states onto the stored row.
     *
     * <p>It used to save the request body itself, which meant every column the
     * caller did not mention was written as NULL — and the edit dialog does not
     * send the English name, so each save quietly cleared
     * {@code STUDY_FINANCE_BANKE_NAME_EN}. A field that is absent is not the same
     * as a field set to nothing; the null guards are what keeps them apart, the
     * same rule {@code LuLookupWrites.copyEditable} follows.
     */
    @Transactional
    public StudyFinanceBank update(Integer id, StudyFinanceBank entity) {
        StudyFinanceBank existing = require(id);
        Integer country = entity.getStudyCountryInId() != null
                ? resolveCountryForWrite(entity.getStudyCountryInId())
                : existing.getStudyCountryInId();
        if (!LookupNameUniqueness.unchanged(existing.getStudyFinanceBankeName(), entity.getStudyFinanceBankeName(),
                existing.getStudyCountryInId(), country)) {
            LookupNameUniqueness.require(em, StudyFinanceBank.class, "studyFinanceBankeName",
                    entity.getStudyFinanceBankeName(), "id", id, "studyCountryInId", country);
        }
        if (entity.getStudyFinanceBankeName() != null) {
            existing.setStudyFinanceBankeName(entity.getStudyFinanceBankeName());
        }
        if (entity.getStudyFinanceBankeNameEn() != null) {
            existing.setStudyFinanceBankeNameEn(entity.getStudyFinanceBankeNameEn());
        }
        if (entity.getIsActive() != null) {
            existing.setIsActive(entity.getIsActive());
        }
        if (entity.getStudyCountryInId() != null) {
            existing.setStudyCountryInId(country);
        }
        return repository.save(existing);
    }

    /**
     * Refuses deleting a bank a branch still names (round-6 QA R6-04: the bank went with
     * 204 and its branch kept the missing id). V147's foreign key refuses it too, but only
     * as "still used by other records"; counted here, the answer says what and how many.
     */
    @Transactional
    public void delete(Integer id) {
        require(id);
        long branches = branchRepository.countByStudyFinanceBankeId(id);
        if (branches > 0) {
            throw new LocalizedStatusException(HttpStatus.CONFLICT,
                    "This bank still has " + branches + " branch(es); move or remove them, or switch the bank off.",
                    "bank.hasBranches", branches);
        }
        repository.deleteById(id);
    }

    /**
     * A bank must belong to an attaché the caller serves.
     *
     * <p>No shared case here, which is why this rejects a null outright instead of
     * letting ministry through with one: a bank with no attaché is exactly the
     * state V049 just cleaned up, and re-creating it would put a row back that
     * only ministry can see.
     */
    Integer resolveCountryForWrite(Integer requested) {
        CountryScope scope = countryScopeService.getScope();
        if (requested == null) {
            List<Integer> allowed = scope.getAllowedCountryIds();
            if (!scope.isMinistryUser() && allowed.size() == 1) return allowed.get(0);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "The attaché this bank belongs to is required (studyCountryInId).");
        }
        if (!countryScopeService.canAccessCountry(requested)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Creating a bank for an attaché outside your scope is not permitted.");
        }
        return requested;
    }
}
