package com.dilaw.mohe.service;

import com.dilaw.mohe.exception.LocalizedStatusException;
import com.dilaw.mohe.model.StudyFinanceBankBranch;
import com.dilaw.mohe.repository.StudyFinanceBankBranchRepository;
import com.dilaw.mohe.repository.StudyFinanceBankRepository;
import com.dilaw.mohe.repository.spec.CountryColumnSpec;
import com.dilaw.mohe.ums.service.CountryScopeService;
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
 * Branches of the banks each attaché pays through.
 *
 * <p>Scoped on the branch's own country column, like {@link StudyFinanceBankService},
 * with a null meaning missing data rather than shared.
 *
 * <p><b>A branch inherits its bank.</b> The branch and its bank both carry a
 * country and they are redundant — they agree on every row today, and this keeps
 * them agreeing. An absent country is taken from the parent bank; one that
 * disagrees with the bank is refused. Without that a branch could drift out of its
 * bank's country and become invisible to the very officers who use that bank,
 * which reads as the branch having been deleted.
 *
 * <p>Worth knowing when this screen looks empty: all twelve branches belong to
 * Egypt's National Bank. Jordan and Saudi each have three banks and <em>no</em>
 * branches, so a correctly scoped branch list is empty for them until someone
 * enters theirs.
 */
@Transactional(readOnly = true)
@Service
public class StudyFinanceBankBranchService {

    @Autowired
    private StudyFinanceBankBranchRepository repository;

    @Autowired
    private StudyFinanceBankRepository bankRepository;

    @Autowired
    private CountryScopeService countryScopeService;

    /** For the duplicate-name check (M12), unique within the bank. */
    @PersistenceContext
    private EntityManager em;

    private Specification<StudyFinanceBankBranch> scoped() {
        return CountryColumnSpec.countryScope(countryScopeService.getScope(), "studyCountryInId");
    }

    public List<StudyFinanceBankBranch> getAll() {
        return repository.findAll(scoped());
    }

    public StudyFinanceBankBranch getById(int id) {
        return require(id);
    }

    @org.springframework.beans.factory.annotation.Autowired
    private com.dilaw.mohe.security.policy.AccessControl accessControl;

    private StudyFinanceBankBranch require(Integer id) {
        return repository.findOne(scoped().and((root, q, cb) -> cb.equal(root.get("id"), id)))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "StudyFinanceBankBranch not found"));
    }

    @Transactional
    public StudyFinanceBankBranch create(StudyFinanceBankBranch entity) {
        entity.setId(repository.nextId());
        entity.setStudyCountryInId(resolveCountry(entity));
        LookupNameUniqueness.require(em, StudyFinanceBankBranch.class, "studyFinanceBankBranchName",
                entity.getStudyFinanceBankBranchName(), "id", null, "studyFinanceBankeId", entity.getStudyFinanceBankeId());
        if (entity.getIsActive() == null) {
            entity.setIsActive(true);
        }
        return repository.save(entity);
    }

    /**
     * Copies what the request states onto the stored row; see
     * {@link StudyFinanceBankService#update} for why saving the body itself was
     * wrong. The country is resolved from the merged row, so a request that names
     * only a new bank is still checked against that bank's attaché.
     */
    @Transactional
    public StudyFinanceBankBranch update(Integer id, StudyFinanceBankBranch entity) {
        StudyFinanceBankBranch existing = require(id);
        accessControl.assertAllowed(new com.dilaw.mohe.security.policy.CountryOwnedConfig("bank branch", existing.getStudyCountryInId()), "WRITE");
        Integer bank = entity.getStudyFinanceBankeId() != null ? entity.getStudyFinanceBankeId() : existing.getStudyFinanceBankeId();
        if (!LookupNameUniqueness.unchanged(existing.getStudyFinanceBankBranchName(), entity.getStudyFinanceBankBranchName(),
                existing.getStudyFinanceBankeId(), bank)) {
            LookupNameUniqueness.require(em, StudyFinanceBankBranch.class, "studyFinanceBankBranchName",
                    entity.getStudyFinanceBankBranchName(), "id", id, "studyFinanceBankeId", bank);
        }
        if (entity.getStudyFinanceBankBranchName() != null) {
            existing.setStudyFinanceBankBranchName(entity.getStudyFinanceBankBranchName());
        }
        if (entity.getStudyFinanceBankBranchNameEn() != null) {
            existing.setStudyFinanceBankBranchNameEn(entity.getStudyFinanceBankBranchNameEn());
        }
        if (entity.getIsActive() != null) {
            existing.setIsActive(entity.getIsActive());
        }
        if (entity.getStudyFinanceBankeId() != null) {
            existing.setStudyFinanceBankeId(entity.getStudyFinanceBankeId());
        }
        if (entity.getStudyCountryInId() != null) {
            existing.setStudyCountryInId(entity.getStudyCountryInId());
        }
        if (entity.getStudyGovInId() != null) {
            existing.setStudyGovInId(entity.getStudyGovInId());
        }
        existing.setStudyCountryInId(resolveCountry(existing));
        return repository.save(existing);
    }

    @Transactional
    public void delete(Integer id) {
        StudyFinanceBankBranch existing = require(id);
        accessControl.assertAllowed(new com.dilaw.mohe.security.policy.CountryOwnedConfig("bank branch", existing.getStudyCountryInId()), "WRITE");
        repository.deleteById(id);
    }

    /**
     * The branch's country: its own if given, otherwise its bank's — and they must
     * not disagree.
     */
    private Integer resolveCountry(StudyFinanceBankBranch branch) {
        Integer bankCountry = null;
        if (branch.getStudyFinanceBankeId() != null) {
            // A bank that does not exist is refused, not read as "no bank to take a country
            // from" (round-6 QA R6-04: a branch naming bank 999999 was saved).
            Integer bankId = branch.getStudyFinanceBankeId();
            bankCountry = bankRepository.findById(bankId)
                    .orElseThrow(() -> new LocalizedStatusException(HttpStatus.BAD_REQUEST,
                            "The bank this branch names (" + bankId + ") does not exist.", "bankBranch.bankMissing"))
                    .getStudyCountryInId();
        }

        // Keyed, so the screen can show the reason in the reader's language; the
        // English sentence named the field (studyCountryInId) to Arabic readers too.
        Integer requested = branch.getStudyCountryInId();
        if (requested == null) {
            if (bankCountry == null) {
                throw new LocalizedStatusException(HttpStatus.BAD_REQUEST,
                        "The attaché this branch belongs to is required (studyCountryInId), "
                                + "or name a bank it can be taken from.", "bankBranch.countryRequired");
            }
            requested = bankCountry;
        } else if (bankCountry != null && !bankCountry.equals(requested)) {
            throw new LocalizedStatusException(HttpStatus.BAD_REQUEST,
                    "This branch's attaché does not match its bank's. A branch belongs to the same "
                            + "attaché as the bank it is part of.", "bankBranch.countryMismatch");
        }

        // S-06: a branch is the attaché's own configuration - its staff write it, the ministry reads it.
        accessControl.assertAllowed(new com.dilaw.mohe.security.policy.CountryOwnedConfig("bank branch", requested), "WRITE");
        return requested;
    }
}
