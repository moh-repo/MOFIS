package com.dilaw.mohe.service;

import com.dilaw.mohe.model.StudyYearsLookup;
import com.dilaw.mohe.repository.StudyYearsLookupRepository;
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
 * How many years a degree and major take, per attaché.
 *
 * <p>A <b>shared default with per-country overrides</b>. A row with no country is
 * the rule that applies wherever an attaché has not set its own; a row with a
 * country replaces it there. The 49 rows this table shipped with are all
 * defaults, so nothing changed for anyone when the country dimension arrived.
 *
 * <p>Two consequences worth keeping straight:
 *
 * <ul>
 *   <li><b>Reads are permissive.</b> The default has to be visible to everyone —
 *       {@code countryScopeOrShared}, the same rule as the الدليل المحاسبي الموحد.
 *       Failing closed here does not leak, it <em>breaks</em>: a country officer
 *       would open this screen to an empty list and student-add would stop
 *       auto-filling the field.
 *   <li><b>Writes are not.</b> Only a ministry user may create or edit a
 *       country-less default, because a default applies to every attaché.
 *       An officer's row is stamped with their own country whether they asked or
 *       not. That asymmetry is what makes the permissive read safe.
 * </ul>
 */
@Transactional(readOnly = true)
@Service
public class StudyYearsLookupService {

    @Autowired
    private StudyYearsLookupRepository repository;

    @Autowired
    private CountryScopeService countryScopeService;

    private Specification<StudyYearsLookup> scoped() {
        return CountryColumnSpec.countryScopeOrShared(countryScopeService.getScope(), "studyCountryInId");
    }

    public List<StudyYearsLookup> getAll() {
        return repository.findAll(scoped());
    }

    /**
     * The years in force for a student, given their attaché.
     *
     * <p>Deliberately <b>not</b> filtered by the caller's scope. This answers "how
     * many years does this student's programme take", and the answer is a property
     * of the student's attaché, not of who is asking. A ministry user opening a
     * Jordanian student must get Jordan's number.
     *
     * @param countryId the student's study country; {@code null} falls back to the
     *                  shared default
     */
    public Optional<StudyYearsLookup> resolve(Integer degreeId, Integer majorId, Integer countryId) {
        List<StudyYearsLookup> found = countryId == null
                ? repository.resolveDefault(degreeId, majorId)
                : repository.resolve(degreeId, majorId, countryId, PageRequest.ofSize(1));
        return found.stream().findFirst();
    }

    /**
     * The country to resolve against when the caller did not name one.
     *
     * <p>An officer serving exactly one attaché almost certainly means that one.
     * Anyone else — ministry, or an officer covering several — gets the default,
     * because guessing between attachés would silently return one country's rule
     * while looking like a general answer.
     */
    public Integer defaultCountryForRead() {
        CountryScope scope = countryScopeService.getScope();
        if (scope.isMinistryUser()) return null;
        List<Integer> allowed = scope.getAllowedCountryIds();
        return allowed.size() == 1 ? allowed.get(0) : null;
    }

    public StudyYearsLookup getById(Integer id) {
        return require(id);
    }

    /**
     * One row by id, or 404 when it belongs to another attaché.
     *
     * <p>Not found rather than forbidden, consistent with the rest of the scope
     * work: ids are sequential and a 403 would confirm which of them exist.
     */
    private StudyYearsLookup require(Integer id) {
        return repository.findOne(scoped().and((root, q, cb) -> cb.equal(root.get("id"), id)))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "StudyYearsLookup not found"));
    }

    @Transactional
    public StudyYearsLookup create(StudyYearsLookup entity) {
        entity.setStudyCountryInId(resolveCountryForWrite(entity.getStudyCountryInId()));
        return repository.save(entity);
    }

    @Transactional
    public StudyYearsLookup update(Integer id, StudyYearsLookup entity) {
        require(id); // existence and scope
        entity.setId(id);
        entity.setStudyCountryInId(resolveCountryForWrite(entity.getStudyCountryInId()));
        return repository.save(entity);
    }

    @Transactional
    public void delete(Integer id) {
        require(id);
        repository.deleteById(id);
    }

    /**
     * Which country a written row belongs to.
     *
     * <p>Ministry may pass null and create the shared default. A country officer
     * may not: an unqualified rule applies to every attaché, and one officer must
     * not be able to set the number of study years for all of them. An officer
     * with a single attaché gets it stamped; one with several has to say which.
     */
    private Integer resolveCountryForWrite(Integer requested) {
        CountryScope scope = countryScopeService.getScope();
        if (scope.isMinistryUser()) return requested;

        List<Integer> allowed = scope.getAllowedCountryIds();
        if (requested == null) {
            if (allowed.size() == 1) return allowed.get(0);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Name the attaché this rule applies to. Only a ministry user may set the shared default.");
        }
        if (!allowed.contains(requested)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Setting study years for an attaché outside your scope is not permitted.");
        }
        return requested;
    }
}
