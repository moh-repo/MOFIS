package com.dilaw.mohe.service;

import com.dilaw.mohe.model.StudyUniversityFeeArch;
import com.dilaw.mohe.repository.StudentDataRepository;
import com.dilaw.mohe.repository.StudyUniversityFeeArchRepository;
import com.dilaw.mohe.repository.spec.CountryColumnSpec;
import com.dilaw.mohe.ums.service.CountryScopeService;
import com.dilaw.mohe.ums.service.CountryScopeService.CountryScope;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Archived university fees, scoped by country.
 *
 * <p>The live twin, {@link StudyUniversityFeeService}, has been scoped for some
 * time; this archive never was, so a country officer could read every attaché's
 * fee history by going to the archive screen instead of the current one. Same
 * data, one page across.
 *
 * <p>Scoped on the table's own {@code STUDY_COUNTRY_IN_ID} rather than through the
 * student, which is what the live service does. The two differ deliberately: an
 * archived row records where the fee was actually paid, and it should keep that
 * even if the student later transfers to another attaché.
 */
@Transactional(readOnly = true)
@Service
public class StudyUniversityFeeArchService {

    @Autowired
    private StudyUniversityFeeArchRepository repository;

    @Autowired
    private CountryScopeService countryScopeService;

    @Autowired
    private StudentDataRepository studentDataRepository;

    /** The caller's scope as a Specification, the base of every read below. */
    private Specification<StudyUniversityFeeArch> scoped() {
        return Specification.where(
                CountryColumnSpec.countryScope(countryScopeService.getScope(), "studyCountryInId"));
    }

    public Page<StudyUniversityFeeArch> getAll(Pageable pageable) {
        return repository.findAll(scoped(), pageable);
    }

    public List<StudyUniversityFeeArch> getAll() {
        return repository.findAll(scoped());
    }

    /** One log row by its key, STUDY_UNIVERSITY_FEE_ID; out of scope reads as absent. */
    public StudyUniversityFeeArch getById(Integer id) {
        return repository.findOne(scoped().and(byKey(id)))
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "StudyUniversityFeeArch not found"));
    }

    public List<StudyUniversityFeeArch> getByStudentId(Integer studentId) {
        return repository.findAll(scoped().and(byStudent(studentId)));
    }

    public List<StudyUniversityFeeArch> getByStudentName(String studentName) {
        return repository.findAll(scoped().and((root, query, cb) ->
                studentName == null || studentName.isBlank()
                        ? cb.conjunction()
                        : cb.like(root.get("studyUniversityFeeStudentName"), "%" + studentName + "%")));
    }

    public List<StudyUniversityFeeArch> getByPayStatus(Integer payStatus) {
        return repository.findAll(scoped().and((root, query, cb) ->
                payStatus == null
                        ? cb.conjunction()
                        : cb.equal(root.get("studyUniversityFeePayStatus"), payStatus)));
    }

    public List<StudyUniversityFeeArch> getByYear(String year) {
        return repository.findAll(scoped().and((root, query, cb) ->
                year == null || year.isBlank()
                        ? cb.conjunction()
                        : cb.equal(root.get("studyUniversityFeeYear"), year)));
    }

    /**
     * Student ids and names for the page's filter dropdown.
     *
     * <p>Scoped for the same reason the rows are: the list of who has fees
     * archived is itself an answer about another attaché's students.
     */
    public List<Object[]> getDistinctStudents() {
        return repository.findAll(scoped()).stream()
                .collect(Collectors.toMap(
                        StudyUniversityFeeArch::getStudentId,
                        e -> new Object[]{ e.getStudentId(), e.getStudyUniversityFeeStudentName() },
                        (first, duplicate) -> first,
                        TreeMap::new))
                .values().stream().toList();
    }

    public List<StudyUniversityFeeArch> getByStudentIdOrdered(Integer studentId) {
        return repository.findAll(scoped().and(byStudent(studentId)),
                Sort.by(Sort.Direction.DESC, "studyUniversityFeeYear"));
    }

    @Transactional
    public StudyUniversityFeeArch create(StudyUniversityFeeArch entity) {
        requireStudent(entity);
        entity.setStudyUniversityFeeId(null); // drawn from the sequence (V114)
        // NOT NULL with DEFAULT 1 (the student's name) in the table, but Hibernate names
        // every column, so a missing value arrives as an explicit NULL. The log's form
        // has no payor field.
        if (entity.getStudyUniversityCheckPayorTypeId() == null) {
            entity.setStudyUniversityCheckPayorTypeId(1);
        }
        entity.setStudyCountryInId(resolveCountryForWrite(entity.getStudyCountryInId(), entity.getStudentId()));
        return repository.save(entity);
    }

    /** Edits row {@code id}; the row stays with its student (review F8). */
    @Transactional
    public StudyUniversityFeeArch update(Integer id, StudyUniversityFeeArch entity) {
        // getById, not findById: it validates the country as well as existence, so
        // an out-of-scope row reads as absent rather than as editable.
        StudyUniversityFeeArch existing = getById(id);
        entity.setStudyUniversityFeeId(id);
        entity.setStudentId(existing.getStudentId());
        if (entity.getStudyUniversityCheckPayorTypeId() == null) {
            entity.setStudyUniversityCheckPayorTypeId(existing.getStudyUniversityCheckPayorTypeId());
        }
        entity.setStudyCountryInId(entity.getStudyCountryInId() != null
                ? resolveCountryForWrite(entity.getStudyCountryInId(), existing.getStudentId())
                : existing.getStudyCountryInId());
        return repository.save(entity);
    }

    @Transactional
    public void delete(Integer id) {
        getById(id); // 404s rather than deleting another attaché's record
        repository.deleteById(id);
    }

    private static void requireStudent(StudyUniversityFeeArch entity) {
        if (entity.getStudentId() == null || entity.getStudentId() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "studentId is required");
        }
    }

    private static Specification<StudyUniversityFeeArch> byKey(Integer id) {
        return (root, query, cb) -> id == null
                ? cb.disjunction()
                : cb.equal(root.get("studyUniversityFeeId"), id);
    }

    private static Specification<StudyUniversityFeeArch> byStudent(Integer studentId) {
        return (root, query, cb) -> studentId == null
                ? cb.disjunction()
                : cb.equal(root.get("studentId"), studentId);
    }

    /**
     * A country the caller is allowed to write to: the one sent, else the
     * student's attaché, else the caller's own when they have exactly one. Refuses
     * rather than writing a NULL that nobody could later see.
     *
     * <p>The student's attaché is what the form means — the fee is being logged
     * for that student. Without it a ministry user, who has no single country to
     * fall back on, could not add a row at all (live QA #8, G2).
     */
    private Integer resolveCountryForWrite(Integer requested, Integer studentId) {
        CountryScope scope = countryScopeService.getScope();
        if (requested != null) {
            countryScopeService.validateCountryAccess(requested);
            return requested;
        }
        Integer studentsCountry = studentId == null ? null
                : studentDataRepository.findStudyCountryInByStudentId(studentId).orElse(null);
        if (studentsCountry != null) {
            countryScopeService.validateCountryAccess(studentsCountry);
            return studentsCountry;
        }
        List<Integer> allowed = scope.getAllowedCountryIds();
        if (!scope.isMinistryUser() && allowed.size() == 1) {
            return allowed.get(0);
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "The attaché this fee belongs to is required (studyCountryInId).");
    }
}
