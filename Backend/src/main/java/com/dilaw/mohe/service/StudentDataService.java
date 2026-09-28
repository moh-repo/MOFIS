package com.dilaw.mohe.service;

import com.dilaw.mohe.model.StudentDatum;
import com.dilaw.mohe.repository.StudentDataRepository;
import com.dilaw.mohe.repository.spec.StudentDataSpec;
import com.dilaw.mohe.ums.service.CountryScopeService;
import com.dilaw.mohe.ums.service.CountryScopeService.CountryScope;
import com.dilaw.mohe.util.NameNormalizer;
import com.dilaw.mohe.util.StudentStatusTransitionValidator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;

import java.util.*;
import com.dilaw.mohe.systemsettings.service.SysParameterService;
import java.util.stream.Collectors;

@Transactional(readOnly = true)
@Service
public class StudentDataService {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(StudentDataService.class);

    @Autowired
    StudentDataRepository studentDataRepository;

    @Autowired
    private CountryScopeService countryScopeService;

    @Autowired
    private StudentDocumentCountryMigrationService studentDocumentCountryMigrationService;

    @Autowired
    private SysParameterService sysParameters;

    @Autowired
    private com.dilaw.mohe.service.codelist.CodeListService codeLists;

    @Autowired
    private StudentDeletionCascade deletionCascade;

    private CountryScope currentScope() {
        return countryScopeService.getScope();
    }

    /** The id of the ACTIVE study status, resolved by CODE through the code list; null when the list lacks it. */
    private Integer activeStatusId() {
        return codeLists.find("study-status", com.dilaw.mohe.codes.StudyStatusCode.ACTIVE)
                .map(com.dilaw.mohe.service.codelist.CodeRow::id).orElse(null);
    }

    public List<StudentDatum> getStudentData() {
        CountryScope scope = currentScope();
        Specification<StudentDatum> spec = Specification
                .where(StudentDataSpec.countryScope(scope))
                .and(StudentDataSpec.visibilityFilter(scope));
        return studentDataRepository.findAll(spec);
    }

    public Page<StudentDatum> findStudentDataAll(Pageable pageable) {
        CountryScope scope = currentScope();
        Specification<StudentDatum> spec = Specification
                .where(StudentDataSpec.countryScope(scope))
                .and(StudentDataSpec.visibilityFilter(scope));
        return studentDataRepository.findAll(spec, pageable);
    }

    public Page<StudentDatum> findStudentDataAllWithStatusFilter(Integer statusId, Pageable pageable) {
        CountryScope scope = currentScope();
        Specification<StudentDatum> spec = Specification
                .where(StudentDataSpec.countryScope(scope))
                .and(StudentDataSpec.visibilityFilter(scope))
                .and(StudentDataSpec.statusFilter(statusId, activeStatusId()));
        return studentDataRepository.findAll(spec, pageable);
    }

    public Page<StudentDatum> searchStudents(String searchTerm, Pageable pageable) {
        CountryScope scope = currentScope();
        Specification<StudentDatum> spec = Specification
                .where(StudentDataSpec.countryScope(scope))
                .and(StudentDataSpec.visibilityFilter(scope))
                .and(StudentDataSpec.searchTerm(searchTerm));
        return studentDataRepository.findAll(spec, pageable);
    }

    /**
     * One page of students for a picker: the same country scope and visibility
     * rules as the list, searched by name or id, ordered by name.
     *
     * <p>Backs the student dropdown on screens that are not student screens — the
     * finance recipients form above all. It reads through the same scope
     * specifications as everything else here, so a country-restricted caller
     * still sees only their own students.
     */
    public Page<StudentDatum> findOptions(String term, java.util.Collection<Integer> ids, Pageable pageable) {
        CountryScope scope = currentScope();
        Specification<StudentDatum> spec = Specification
                .where(StudentDataSpec.countryScope(scope))
                .and(StudentDataSpec.visibilityFilter(scope))
                .and(StudentDataSpec.idsIn(ids))
                .and(StudentDataSpec.pickerTerm(term));
        return studentDataRepository.findAll(spec, pageable);
    }

    public Page<StudentDatum> findByFieldFilters(Integer studentId, String name, String passport, Integer statusId, Pageable pageable) {
        CountryScope scope = currentScope();
        Specification<StudentDatum> spec = Specification
                .where(StudentDataSpec.countryScope(scope))
                .and(StudentDataSpec.visibilityFilter(scope))
                .and(StudentDataSpec.fieldFilters(studentId, name, passport))
                .and(StudentDataSpec.statusFilter(statusId, activeStatusId()));
        return studentDataRepository.findAll(spec, pageable);
    }

    public Page<StudentDatum> searchStudentsWithStatusFilter(String searchTerm, Integer statusId, Pageable pageable) {
        CountryScope scope = currentScope();
        Specification<StudentDatum> spec = Specification
                .where(StudentDataSpec.countryScope(scope))
                .and(StudentDataSpec.visibilityFilter(scope))
                .and(StudentDataSpec.searchTerm(searchTerm))
                .and(StudentDataSpec.statusFilter(statusId, activeStatusId()));
        return studentDataRepository.findAll(spec, pageable);
    }

    /**
     * The student behind a student-portal session, with no UMS authorisation.
     *
     * <p>{@link #getStudentData(Integer)} is a ministry method: it asks
     * {@code CountryScopeService} whether the caller may see this country, and it
     * checks {@code recordVisibility} against the caller's admin flag. Both read
     * the UMS SecurityContext, and a portal student has no UMS user — so calling
     * it from the portal returned 403 "Access denied: country not in scope" for a
     * correctly authenticated student.
     *
     * <p>Worse than failing: it did not fail when a staff member happened to be
     * logged in to the same browser, because the frontend attached their JWT to
     * portal requests too. The same student with the same portal token got 403
     * with nobody logged in, 200 behind a ministry account, and 403 again behind
     * an account scoped to another country. Their access to their own record was
     * decided by somebody else's permissions.
     *
     * <p>So the portal reads through here instead. The authorisation it relies on
     * is the one that actually applies: {@code StudentPortalTokenInterceptor}
     * resolved this id from the session token, so the caller <em>is</em> this
     * student, and the interceptor also put that student's country into
     * {@link com.dilaw.mohe.config.tenant.TenantContext} — so with the V031
     * policy enabled the database independently confirms it.
     *
     * <p>No visibility check. {@code recordVisibility} and {@code isAdmin} exist
     * to hide records between staff of different levels; neither concept applies
     * to the person the record is about.
     */
    public StudentDatum getStudentDataForPortal(Integer id) {
        return studentDataRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Student not found"));
    }

    public StudentDatum getStudentData(Integer id) {
        Optional<StudentDatum> studentDatumOptional = studentDataRepository.findById(id);
        if (studentDatumOptional.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Student not found");
        }
        StudentDatum student = studentDatumOptional.get();
        CountryScope scope = currentScope();
        Integer studyCountry = student.getStudyCountryIn();
        /*
         * A student with no study country is ministry-only, not open to everyone.
         * canAccessCountry passes a null straight through as "no country to check",
         * which is right where the column is optional and wrong here: this is the
         * record the whole scope model exists to protect. Same rule as
         * CountryColumnSpec.countryScope, where a missing country means missing
         * data rather than a deliberate default. No student has a null country
         * today, so this closes a hole rather than changing behaviour.
         */
        if (studyCountry == null) {
            if (!scope.isMinistryUser()) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied: student has no country on record");
            }
        } else if (!countryScopeService.canAccessCountry(studyCountry)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied: country not in scope");
        }
        if (!scope.isAdmin() && !"ALL".equals(student.getRecordVisibility())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied: record visibility restricted");
        }
        return student;
    }


    /**
     * Used by approval EXECUTE INSERT. The database always assigns the student id.
     *
     * <p>A manually typed id used to be written with {@code SET IDENTITY_INSERT}, which
     * needs ALTER on the table. The least-privilege login {@code mohe_app} does not have
     * it, so every such request failed at its last approval level (live QA, request
     * 20869). Manual ids were removed on 22 Sep 2026 (decision DEC-02); an id still
     * present in an older pending request is ignored rather than failing it.
     */
    @Transactional
    public StudentDatum persistNewApprovedStudent(StudentDatum studentDatum) {
        if (studentDatum.getCreatedRoleLevel() == null) {
            studentDatum.setCreatedRoleLevel(currentScope().roleLevel());
        }
        if (studentDatum.getRecordVisibility() == null) {
            studentDatum.setRecordVisibility("ALL");
        }
        if (studentDatum.getStudentId() != null && studentDatum.getStudentId() > 0) {
            log.info("Ignoring requested student id {} on approval: ids are assigned by the database",
                    studentDatum.getStudentId());
        }
        studentDatum.setStudentId(null);
        return studentDataRepository.saveAndFlush(studentDatum);
    }

    @Transactional
    public StudentDatum save(StudentDatum studentDatum) {
        String passport = studentDatum.getStudentPassportNumber();
        if (passport != null && !passport.isBlank()
                && studentDataRepository.existsByStudentPassportNumber(passport.trim())) {
            throw new IllegalStateException(
                    "A student with passport number '" + passport.trim() + "' already exists.");
        }
        CountryScope scope = currentScope();
        if (studentDatum.getCreatedRoleLevel() == null) {
            studentDatum.setCreatedRoleLevel(scope.roleLevel());
        }
        if (studentDatum.getRecordVisibility() == null) {
            studentDatum.setRecordVisibility("ALL");
        }
        return studentDataRepository.save(studentDatum);
    }

    @Transactional
    public void updatePicturePath(Integer studentId, String relativePath) {
        StudentDatum s = getStudentData(studentId);
        s.setPicturePath(relativePath);
        studentDataRepository.save(s);
    }

    @Transactional
    public void delete(Integer id) {
        StudentDatum s = getStudentData(id);
        s.setIsDeleted(true);
        s.setDeletedDate(Instant.now());
        studentDataRepository.save(s);
        // The finance row and the portal account go with the student (live QA H7, D5-a).
        deletionCascade.onStudentDeleted(id);
    }


    @Transactional
    public StudentDatum update(Integer id, StudentDatum studentDatum) {
        Optional<StudentDatum> studentDatumOptional = studentDataRepository.findById(id);
        if (studentDatumOptional.isEmpty()) {
            throw new ResponseStatusException(
                    org.springframework.http.HttpStatus.NOT_FOUND, "Student not found");
        }
        StudentDatum existingStudentDatum = studentDatumOptional.get();

        /*
         * Read before the field is overwritten below: a student moving to another
         * attaché has to take their uploaded files with them, and the folder they are
         * currently in is only knowable from the value being replaced.
         */
        Integer previousStudyCountryIn = existingStudentDatum.getStudyCountryIn();

        // Validate status transition before applying any changes
        if (studentDatum.getStudentStudyStatusId() != null
                && !studentDatum.getStudentStudyStatusId().equals(existingStudentDatum.getStudentStudyStatusId())) {
            StudentStatusTransitionValidator.validate(
                    existingStudentDatum.getStudentStudyStatusId(),
                    studentDatum.getStudentStudyStatusId());
        }

        // Duplicate passport number check (exclude current student)
        String newPassport = studentDatum.getStudentPassportNumber();
        if (newPassport != null && !newPassport.isBlank()
                && studentDataRepository.existsByStudentPassportNumberAndStudentIdNot(newPassport.trim(), id)) {
            throw new IllegalStateException(
                    "A student with passport number '" + newPassport.trim() + "' already exists.");
        }

        // Copy all non-null fields from the incoming studentDatum to existingStudentDatum
        existingStudentDatum.setStudentName(studentDatum.getStudentName());
        existingStudentDatum.setStudentGovId(studentDatum.getStudentGovId());
        existingStudentDatum.setStudentMudId(studentDatum.getStudentMudId());
        existingStudentDatum.setStudentDelegationDecisionYear(studentDatum.getStudentDelegationDecisionYear());
        existingStudentDatum.setStudentDegreeId(studentDatum.getStudentDegreeId());
        existingStudentDatum.setStudentMajorId(studentDatum.getStudentMajorId());
        existingStudentDatum.setStudentMajorName(studentDatum.getStudentMajorName());
        existingStudentDatum.setStudentDelegateDecisionNo(studentDatum.getStudentDelegateDecisionNo());
        existingStudentDatum.setStudentStudyNoOfYears(studentDatum.getStudentStudyNoOfYears());
        existingStudentDatum.setStudentCurrentLevelId(studentDatum.getStudentCurrentLevelId());
        existingStudentDatum.setStudentFirstReceiptOfDue(studentDatum.getStudentFirstReceiptOfDue());
        existingStudentDatum.setStudentAmount(studentDatum.getStudentAmount());
        existingStudentDatum.setAmountCurrency(studentDatum.getAmountCurrency());
        existingStudentDatum.setStudentNote1(studentDatum.getStudentNote1());
        existingStudentDatum.setStudentNote2(studentDatum.getStudentNote2());
        existingStudentDatum.setStudentStudyStatusId(studentDatum.getStudentStudyStatusId());
        existingStudentDatum.setStudentAgencyTypeId(studentDatum.getStudentAgencyTypeId());
        existingStudentDatum.setStudentMinistryTypeId(studentDatum.getStudentMinistryTypeId());
        existingStudentDatum.setStudentStudyTypeId(studentDatum.getStudentStudyTypeId());
        existingStudentDatum.setStudentUniversityTypeId(studentDatum.getStudentUniversityTypeId());
        existingStudentDatum.setStudentAddedQuarter(studentDatum.getStudentAddedQuarter());
        existingStudentDatum.setStudentLastQuarter(studentDatum.getStudentLastQuarter());
        existingStudentDatum.setCurrentQuarter(studentDatum.getCurrentQuarter());
        existingStudentDatum.setStudyUniversityIn(studentDatum.getStudyUniversityIn());
        existingStudentDatum.setStudyCollegeIn(studentDatum.getStudyCollegeIn());
        existingStudentDatum.setStudyCountryIn(studentDatum.getStudyCountryIn());
        existingStudentDatum.setSyudyGovIn(studentDatum.getSyudyGovIn());
        existingStudentDatum.setStudyEducationIn(studentDatum.getStudyEducationIn());
        existingStudentDatum.setStudyMajorIn(studentDatum.getStudyMajorIn());
        existingStudentDatum.setStudentBirthdate(studentDatum.getStudentBirthdate());
        existingStudentDatum.setStudentBirthdatePlace(studentDatum.getStudentBirthdatePlace());
        existingStudentDatum.setStudentGender(studentDatum.getStudentGender());
        existingStudentDatum.setStudentPassportNumber(studentDatum.getStudentPassportNumber());
        existingStudentDatum.setStudentPassportIssueDate(studentDatum.getStudentPassportIssueDate());
        existingStudentDatum.setStudentPassportExpireDate(studentDatum.getStudentPassportExpireDate());
        existingStudentDatum.setStudentPassportIssueCountryId(studentDatum.getStudentPassportIssueCountryId());
        existingStudentDatum.setStudyUniversityFeeId(studentDatum.getStudyUniversityFeeId());
        existingStudentDatum.setStudyUniversityFeeStatusId(studentDatum.getStudyUniversityFeeStatusId());
        existingStudentDatum.setUniversityFeeStatusCurrency(studentDatum.getUniversityFeeStatusCurrency());
        /*
         * The four contact fields were never copied here, so an e-mail or phone edited
         * in the wizard was approved and then silently dropped (live QA H3, 23 Sep
         * 2026). Quick Edit and the portal's change requests write them on their own
         * paths; this is the one the wizard's approval executes.
         */
        existingStudentDatum.setStudentEmail(studentDatum.getStudentEmail());
        existingStudentDatum.setStudentCellphone(studentDatum.getStudentCellphone());
        existingStudentDatum.setStudentHomeNumber(studentDatum.getStudentHomeNumber());
        existingStudentDatum.setStudentEmergencyContact(studentDatum.getStudentEmergencyContact());
        /*
         * Only replace the picture path when the payload actually carries one. The edit
         * form does not submit this field, so assigning it unconditionally cleared the
         * photo of every student whose update was approved.
         */
        if (studentDatum.getPicturePath() != null && !studentDatum.getPicturePath().isBlank()) {
            existingStudentDatum.setPicturePath(studentDatum.getPicturePath());
        }

        StudentDatum saved = studentDataRepository.save(existingStudentDatum);

        if (!java.util.Objects.equals(previousStudyCountryIn, saved.getStudyCountryIn())) {
            studentDocumentCountryMigrationService.relocateForCountryChange(
                    saved.getStudentId(), previousStudyCountryIn, saved.getStudyCountryIn());
        }
        return saved;
    }

    /** The most recently entered students in scope; how many is the {@code DASHBOARD.RECENT_STUDENTS} parameter. */
    public List<StudentDatum> getRecentStudents() {
        int rows = sysParameters.intValue(SysParameterService.DASHBOARD_RECENT_STUDENTS);
        Specification<StudentDatum> spec = StudentDataSpec.countryScope(currentScope());
        Page<StudentDatum> page = studentDataRepository.findAll(spec,
                PageRequest.of(0, rows, Sort.by("studentMidate").descending()));
        if (!page.isEmpty()) return page.getContent();
        // Records with no entry date: order by id within the same scope
        return studentDataRepository.findAll(spec, PageRequest.of(0, rows, Sort.by("studentId").descending()))
                .getContent();
    }

    /**
     * Check for duplicate student names with strict matching rules:
     * 1. First name MUST match STRICTLY (exact match after normalization, or max 1 char typo)
     * 2. Other names (middle + last) MUST have >= 50% similarity (current logic preserved)
     * 3. Handles Arabic normalization, spacing, and order variations
     * 
     * IMPORTANT: First name matching is very strict - only exact matches or single character
     * typos are allowed. Remaining names use the existing 50% similarity logic.
     * 
     * @param name The student name to check
     * @return List of students matching the strict criteria, sorted by relevance
     */
    public List<StudentDatum> checkDuplicateName(String name) {
        if (name == null || name.trim().isEmpty()) {
            return new ArrayList<>();
        }

        // Normalize the search name
        String normalizedSearchName = NameNormalizer.normalize(name);
        if (normalizedSearchName.isEmpty()) {
            return new ArrayList<>();
        }

        // Get name parts - FIRST NAME is the first part
        List<String> searchParts = NameNormalizer.normalizeAndSplit(name);
        if (searchParts.isEmpty()) {
            return new ArrayList<>();
        }

        // Extract first name (first part) - this MUST match strictly
        String searchFirstName = searchParts.get(0);
        if (searchFirstName == null || searchFirstName.isEmpty()) {
            return new ArrayList<>();
        }
        
        // Normalize the first name for matching
        String normalizedSearchFirstName = NameNormalizer.normalize(searchFirstName);
        
        // Extract other names (middle + last) - for similarity check
        List<String> searchOtherNames = searchParts.size() > 1 
            ? searchParts.subList(1, searchParts.size()) 
            : new ArrayList<>();
        String normalizedSearchOtherNames = getOtherNamesString(searchOtherNames, true);

        // Strategy 1: Search for students whose name starts with the first name
        Set<StudentDatum> candidateSet = new HashSet<>();
        
        try {
            // Search for names that start with the first name
            List<StudentDatum> firstNameStartCandidates = studentDataRepository.findByFirstNameStartsWith(searchFirstName.trim());
            candidateSet.addAll(firstNameStartCandidates);
            
            // Also search for names containing the first name (to catch variations)
            List<StudentDatum> firstNameCandidates = studentDataRepository.findCandidatesForFuzzyMatching(searchFirstName.trim());
            candidateSet.addAll(firstNameCandidates);
        } catch (Exception e) {
            log.error("Error in first name candidate search: {}", e.getMessage());
        }

        // Strategy 2: Also try exact match for full name
        try {
            String trimmedName = name.trim();
            if (!trimmedName.isEmpty()) {
                List<StudentDatum> exactCandidates = studentDataRepository.findByStudentNameIgnoreCase(trimmedName);
                candidateSet.addAll(exactCandidates);
            }
        } catch (Exception e) {
            log.error("Error in exact match candidate search: {}", e.getMessage());
        }

        // CRITICAL: Apply strict filtering rules
        List<MatchResult> matches = new ArrayList<>();
        // The floor is the STUDENTS.MATCH_SIMILARITY_FLOOR parameter (V072), not a constant.
        double MINIMUM_OTHER_NAMES_SIMILARITY =
                sysParameters.decimalValue(SysParameterService.STUDENTS_MATCH_SIMILARITY_FLOOR).doubleValue();
        
        for (StudentDatum student : candidateSet) {
            if (student.getStudentName() == null || student.getStudentName().trim().isEmpty()) {
                continue;
            }

            String studentName = student.getStudentName();
            
            // Get student's name parts
            List<String> studentParts = NameNormalizer.normalizeAndSplit(studentName);
            if (studentParts.isEmpty()) {
                continue;
            }
            
            String studentFirstName = studentParts.get(0);
            if (studentFirstName == null || studentFirstName.isEmpty()) {
                continue;
            }
            
            // Normalize student's first name
            String normalizedStudentFirstName = NameNormalizer.normalize(studentFirstName);
            
            // RULE 1: First name MUST match STRICTLY (exact match after normalization is primary)
            // Only allow very minor typos as a fallback (1 character difference max)
            boolean firstNameMatches = false;
            
            // PRIORITY 1: Exact match after normalization (most strict)
            if (normalizedSearchFirstName.equals(normalizedStudentFirstName)) {
                firstNameMatches = true;
            }
            // PRIORITY 2: Allow only very minor typos in first name (1 character difference only)
            // This catches legitimate typos like "Ahmed" vs "Ahmad" after normalization
            else if (normalizedSearchFirstName.length() >= 3 && normalizedStudentFirstName.length() >= 3) {
                // Only allow fuzzy match if both names are at least 3 characters
                // and the Levenshtein distance is exactly 1 (one character difference)
                int distance = NameNormalizer.levenshteinDistance(normalizedSearchFirstName, normalizedStudentFirstName);
                if (distance == 1) {
                    firstNameMatches = true;
                }
            }
            
            // If first name doesn't match, exclude this record
            if (!firstNameMatches) {
                continue;
            }
            
            // RULE 2: Other names (middle + last) must have >= 50% similarity
            List<String> studentOtherNames = studentParts.size() > 1 
                ? studentParts.subList(1, studentParts.size()) 
                : new ArrayList<>();
            
            // Handle cases where one or both have no other names
            String normalizedStudentOtherNames = getOtherNamesString(studentOtherNames, false);
            
            // RULE 2: Check other names similarity
            // If both have no other names, consider it a match (first name already matched)
            if (searchOtherNames.isEmpty() && studentOtherNames.isEmpty()) {
                double score = calculateMatchScore(name, studentName, normalizedSearchName);
                matches.add(new MatchResult(student, score));
                continue;
            }
            
            // Missing middle name is allowed (one has other names, the other doesn't)
            if (searchOtherNames.isEmpty() || studentOtherNames.isEmpty()) {
                // Accept this match (missing middle name is explicitly allowed)
                double score = calculateMatchScore(name, studentName, normalizedSearchName);
                // Slightly reduce score for missing parts (but still acceptable)
                score = score * 0.85; // 85% of normal score
                matches.add(new MatchResult(student, score));
                continue;
            }
            
            // Both have other names - MUST have >= 50% similarity (allowing order variations)
            double otherNamesSimilarity = calculateOtherNamesSimilarity(
                normalizedSearchOtherNames, 
                normalizedStudentOtherNames,
                searchOtherNames,
                studentOtherNames
            );
            
            // Must have >= 50% similarity for other names
            if (otherNamesSimilarity < MINIMUM_OTHER_NAMES_SIMILARITY) {
                continue; // Exclude if similarity is below 50% threshold
            }

            // Both rules passed - calculate final score
            double score = calculateMatchScore(name, studentName, normalizedSearchName);
            
            // Boost score based on other names similarity
            score = Math.max(score, 0.5 + (otherNamesSimilarity * 0.5));
            
            matches.add(new MatchResult(student, score));
        }

        // Sort by score (highest first) and return the students
        return matches.stream()
                .sorted((a, b) -> Double.compare(b.score, a.score))
                .map(mr -> mr.student)
                .distinct()
                .collect(Collectors.toList());
    }
    
    /**
     * Gets the "other names" (middle + last) as a combined normalized string.
     */
    private String getOtherNamesString(List<String> otherNames, boolean allowOrderVariation) {
        if (otherNames == null || otherNames.isEmpty()) {
            return "";
        }
        
        // Return normalized string (order variations handled in similarity calculation)
        return NameNormalizer.normalize(String.join(" ", otherNames));
    }
    
    /**
     * Calculates similarity between "other names" (middle + last).
     * Allows order variations and returns a score from 0.0 to 1.0.
     */
    private double calculateOtherNamesSimilarity(
            String normalizedSearchOtherNames,
            String normalizedStudentOtherNames,
            List<String> searchOtherNames,
            List<String> studentOtherNames) {
        
        // If both are empty, 100% match
        if ((normalizedSearchOtherNames == null || normalizedSearchOtherNames.isEmpty()) &&
            (normalizedStudentOtherNames == null || normalizedStudentOtherNames.isEmpty())) {
            return 1.0;
        }
        
        // If one is empty and the other isn't, return 0% (unless we want to allow missing middle names)
        if (normalizedSearchOtherNames == null || normalizedSearchOtherNames.isEmpty()) {
            // Allow missing middle name - give partial credit if student has other names
            return 0.0; // Strict: no other names means no match
        }
        if (normalizedStudentOtherNames == null || normalizedStudentOtherNames.isEmpty()) {
            return 0.0;
        }
        
        // Method 1: Check all possible orderings for search other names
        Set<String> searchOrderings = new HashSet<>();
        if (searchOtherNames.size() > 1) {
            // Original order
            searchOrderings.add(normalizedSearchOtherNames);
            // Reverse order
            List<String> reversed = new ArrayList<>(searchOtherNames);
            Collections.reverse(reversed);
            searchOrderings.add(NameNormalizer.normalize(String.join(" ", reversed)));
        } else {
            searchOrderings.add(normalizedSearchOtherNames);
        }
        
        // Method 2: Check all possible orderings for student other names
        Set<String> studentOrderings = new HashSet<>();
        if (studentOtherNames.size() > 1) {
            // Original order
            studentOrderings.add(normalizedStudentOtherNames);
            // Reverse order
            List<String> reversed = new ArrayList<>(studentOtherNames);
            Collections.reverse(reversed);
            studentOrderings.add(NameNormalizer.normalize(String.join(" ", reversed)));
        } else {
            studentOrderings.add(normalizedStudentOtherNames);
        }
        
        // Calculate similarity for all combinations of orderings
        double maxSimilarity = 0.0;
        
        for (String searchOrdering : searchOrderings) {
            for (String studentOrdering : studentOrderings) {
                double similarity = calculateStringSimilarity(searchOrdering, studentOrdering);
                maxSimilarity = Math.max(maxSimilarity, similarity);
            }
        }
        
        return maxSimilarity;
    }
    
    /**
     * Calculates similarity between two strings using multiple methods.
     * Returns the maximum similarity score from all methods.
     */
    private double calculateStringSimilarity(String str1, String str2) {
        if (str1 == null || str2 == null) {
            return 0.0;
        }
        if (str1.isEmpty() && str2.isEmpty()) {
            return 1.0;
        }
        if (str1.isEmpty() || str2.isEmpty()) {
            return 0.0;
        }
        
        // Method 1: Exact match after normalization
        if (str1.equals(str2)) {
            return 1.0;
        }
        
        // Method 2: Levenshtein-based similarity
        double levenshteinSimilarity = NameNormalizer.similarity(str1, str2);
        
        // Method 3: Token overlap (word-based similarity)
        double tokenOverlap = calculateTokenOverlap(str1, str2);
        
        // Method 4: Length-based similarity
        double lengthSimilarity = calculateLengthSimilarity(str1, str2);
        
        // Method 5: Partial string match (one contains the other)
        double partialMatch = calculatePartialMatch(str1, str2);
        
        // Return the maximum of all similarity measures
        return Math.max(Math.max(Math.max(levenshteinSimilarity, tokenOverlap), 
                                 Math.max(lengthSimilarity, partialMatch)), 
                       NameNormalizer.similarity(str1, str2));
    }
    
    /**
     * Calculates token overlap similarity (word-based).
     */
    private double calculateTokenOverlap(String str1, String str2) {
        Set<String> tokens1 = new HashSet<>(Arrays.asList(str1.split("\\s+")));
        Set<String> tokens2 = new HashSet<>(Arrays.asList(str2.split("\\s+")));
        
        if (tokens1.isEmpty() && tokens2.isEmpty()) {
            return 1.0;
        }
        if (tokens1.isEmpty() || tokens2.isEmpty()) {
            return 0.0;
        }
        
        // Count matching tokens
        int matchingTokens = 0;
        for (String token : tokens1) {
            if (tokens2.contains(token)) {
                matchingTokens++;
            }
        }
        
        // Use Jaccard similarity: intersection / union
        int totalUniqueTokens = tokens1.size() + tokens2.size() - matchingTokens;
        return totalUniqueTokens > 0 ? (double) matchingTokens / totalUniqueTokens : 0.0;
    }
    
    /**
     * Calculates length-based similarity.
     */
    private double calculateLengthSimilarity(String str1, String str2) {
        int len1 = str1.length();
        int len2 = str2.length();
        
        if (len1 == 0 && len2 == 0) {
            return 1.0;
        }
        int maxLen = Math.max(len1, len2);
        if (maxLen == 0) {
            return 1.0;
        }
        
        int minLen = Math.min(len1, len2);
        return (double) minLen / maxLen;
    }
    
    /**
     * Calculates partial match similarity (one contains the other).
     */
    private double calculatePartialMatch(String str1, String str2) {
        if (str1.contains(str2)) {
            return (double) str2.length() / str1.length();
        }
        if (str2.contains(str1)) {
            return (double) str1.length() / str2.length();
        }
        return 0.0;
    }

    /**
     * Calculates a match score between search name and student name (0.0 to 1.0).
     * Higher score means better match.
     */
    private double calculateMatchScore(String originalSearchName, String studentName, String normalizedSearchName) {
        String normalizedStudentName = NameNormalizer.normalize(studentName);
        
        if (normalizedStudentName.isEmpty()) {
            return 0.0;
        }

        // Strategy 1: Exact normalized match (score: 1.0)
        if (normalizedSearchName.equals(normalizedStudentName)) {
            return 1.0;
        }

        // Strategy 2: NameNormalizer.matches() - handles variations, order, parts (score: 0.9)
        if (NameNormalizer.matches(originalSearchName, studentName)) {
            return 0.9;
        }

        // Strategy 3: Check all possible orderings (score: 0.85)
        Set<String> searchOrderings = NameNormalizer.getAllPossibleOrderings(originalSearchName);
        for (String ordering : searchOrderings) {
            if (NameNormalizer.normalize(ordering).equals(normalizedStudentName)) {
                return 0.85;
            }
        }

        // Strategy 4: Substring match (one contains the other) (score: 0.7-0.8)
        if (normalizedSearchName.contains(normalizedStudentName) || 
            normalizedStudentName.contains(normalizedSearchName)) {
            double baseScore = 0.7;
            int minLen = Math.min(normalizedSearchName.length(), normalizedStudentName.length());
            int maxLen = Math.max(normalizedSearchName.length(), normalizedStudentName.length());
            // Better score if lengths are similar
            double lengthSimilarity = 1.0 - ((double)(maxLen - minLen) / maxLen);
            return baseScore + (0.1 * lengthSimilarity);
        }

        // Strategy 5: Name part matching (score: 0.5-0.7)
        List<String> searchParts = NameNormalizer.normalizeAndSplit(originalSearchName);
        List<String> studentParts = NameNormalizer.normalizeAndSplit(studentName);
        
        if (!searchParts.isEmpty() && !studentParts.isEmpty()) {
            int matchingParts = 0;
            for (String searchPart : searchParts) {
                for (String studentPart : studentParts) {
                    if (searchPart.equals(studentPart) || 
                        NameNormalizer.matches(searchPart, studentPart)) {
                        matchingParts++;
                        break;
                    }
                }
            }
            
            if (matchingParts > 0) {
                double partScore = (double) matchingParts / Math.max(searchParts.size(), studentParts.size());
                return 0.5 + (0.2 * partScore);
            }
        }

        // Strategy 6: Fuzzy matching with Levenshtein distance (score: 0.3-0.5)
        double similarity = NameNormalizer.similarity(originalSearchName, studentName);
        if (similarity >= 0.7) {
            return 0.3 + (0.2 * (similarity - 0.7) / 0.3); // Scale 0.7-1.0 to 0.3-0.5
        }

        // Strategy 7: Fuzzy match within distance threshold (score: 0.1-0.3)
        if (NameNormalizer.fuzzyMatch(originalSearchName, studentName, 3)) {
            int distance = NameNormalizer.levenshteinDistance(normalizedSearchName, normalizedStudentName);
            int maxLen = Math.max(normalizedSearchName.length(), normalizedStudentName.length());
            if (maxLen > 0) {
                double distanceScore = 1.0 - ((double) distance / maxLen);
                return 0.1 + (0.2 * distanceScore);
            }
        }

        return 0.0;
    }

    /**
     * Helper class to store match results with scores.
     */
    private static class MatchResult {
        StudentDatum student;
        double score;

        MatchResult(StudentDatum student, double score) {
            this.student = student;
            this.score = score;
        }
    }

}
