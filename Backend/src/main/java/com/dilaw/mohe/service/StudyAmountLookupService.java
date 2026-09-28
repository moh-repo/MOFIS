package com.dilaw.mohe.service;

import com.dilaw.mohe.model.StudyAmountLookup;
import com.dilaw.mohe.repository.StudyAmountLookupRepository;
import com.dilaw.mohe.repository.spec.CountryColumnSpec;
import com.dilaw.mohe.ums.service.CountryScopeService;
import com.dilaw.mohe.ums.service.CountryScopeService.CountryScope;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

/**
 * The finance amount for a degree and agency type, per attaché.
 *
 * <p>The twin of {@link StudyYearsLookupService}, and the same model: a row with
 * no country is the shared default, a row with one overrides it there. See that
 * class for why reads are permissive and writes are not — the reasoning is
 * identical and is not repeated here.
 *
 * <p>One difference worth noting: this table carries a {@code CURRENCY_ID}, and
 * an attaché overriding the amount will usually be overriding the currency with
 * it. The two travel together on the same row, so nothing extra is needed — but
 * an override that changes the amount and leaves the currency at the default's is
 * a data-entry mistake this code cannot detect.
 */
@Transactional(readOnly = true)
@Service
public class StudyAmountLookupService {

    @Autowired
    private StudyAmountLookupRepository repository;

    @Autowired
    private CountryScopeService countryScopeService;

    private Specification<StudyAmountLookup> scoped() {
        return CountryColumnSpec.countryScopeOrShared(countryScopeService.getScope(), "studyCountryInId");
    }

    public List<StudyAmountLookup> getAll() {
        return repository.findAll(scoped());
    }

    /**
     * The amount in force for a student, given their attaché.
     *
     * <p>Not filtered by the caller's scope, for the same reason as the years
     * resolver: this is a property of the student's attaché, not of who is asking.
     */
    public Optional<StudyAmountLookup> resolve(Integer degreeId, Integer agencyTypeId, Integer countryId) {
        List<StudyAmountLookup> found = countryId == null
                ? repository.resolveDefault(degreeId, agencyTypeId)
                : repository.resolve(degreeId, agencyTypeId, countryId, PageRequest.ofSize(1));
        return found.stream().findFirst();
    }

    /** See {@link StudyYearsLookupService#defaultCountryForRead()}. */
    public Integer defaultCountryForRead() {
        CountryScope scope = countryScopeService.getScope();
        if (scope.isMinistryUser()) return null;
        List<Integer> allowed = scope.getAllowedCountryIds();
        return allowed.size() == 1 ? allowed.get(0) : null;
    }

    public StudyAmountLookup getById(Integer id) {
        return require(id);
    }

    private StudyAmountLookup require(Integer id) {
        return repository.findOne(scoped().and((root, q, cb) -> cb.equal(root.get("id"), id)))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "StudyAmountLookup not found"));
    }

    @Transactional
    public StudyAmountLookup create(StudyAmountLookup entity) {
        entity.setStudyCountryInId(resolveCountryForWrite(entity.getStudyCountryInId()));
        return repository.save(entity);
    }

    @Transactional
    public StudyAmountLookup update(Integer id, StudyAmountLookup entity) {
        require(id);
        entity.setId(id);
        entity.setStudyCountryInId(resolveCountryForWrite(entity.getStudyCountryInId()));
        return repository.save(entity);
    }

    @Transactional
    public void delete(Integer id) {
        require(id);
        repository.deleteById(id);
    }

    /** See {@link StudyYearsLookupService} — only ministry may set the shared default. */
    private Integer resolveCountryForWrite(Integer requested) {
        CountryScope scope = countryScopeService.getScope();
        if (scope.isMinistryUser()) return requested;

        List<Integer> allowed = scope.getAllowedCountryIds();
        if (requested == null) {
            if (allowed.size() == 1) return allowed.get(0);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Name the attaché this amount applies to. Only a ministry user may set the shared default.");
        }
        if (!allowed.contains(requested)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Setting the finance amount for an attaché outside your scope is not permitted.");
        }
        return requested;
    }
}
