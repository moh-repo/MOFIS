package com.dilaw.mohe.service;

import com.dilaw.mohe.model.MoheQuarter;
import com.dilaw.mohe.model.StudentDatum;
import com.dilaw.mohe.model.StudentFinance;
import com.dilaw.mohe.repository.LuQuarterNumberRepository;
import com.dilaw.mohe.repository.MoheQuarterRepository;
import com.dilaw.mohe.repository.StudentCurrentLevelRepository;
import com.dilaw.mohe.repository.StudentDataRepository;
import com.dilaw.mohe.repository.StudentFinanceRepository;
import com.dilaw.mohe.repository.StudentStudyStatusRepository;
import com.dilaw.mohe.repository.spec.StudentDataSpec;
import com.dilaw.mohe.ums.service.CountryScopeService;
import com.dilaw.mohe.ums.service.CountryScopeService.CountryScope;
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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Creates a new MOHE quarter and, within the same transaction:
 *  1. Snapshots every in-scope StudentDatum → STUDENT_DATA_ARCH (upsert on student/period).
 *  2. Snapshots every in-scope StudentFinance → STUDENT_FINANCE_ARCH (upsert on student/year/quarter).
 *  3. Rolls both live rows over into the new quarter — see {@link QuarterRolloverRules},
 *     which owns what a student and a finance row become.
 *  4. Advances StudentDatum.studentLastQuarter to the new quarter.
 *
 * <p>Country scope rules:
 * <ul>
 *   <li>Ministry users process ALL students, and their quarter is global
 *       ({@code COUNTRY_ID} NULL — the meaning {@link MoheQuarterRepository} gives it).</li>
 *   <li>Country users process only students whose studyCountryIn is in their allowed
 *       list, and their quarter carries one of those countries. A {@code countryId} they
 *       send outside their scope is refused with 403; it used to be silently replaced
 *       with their first country, so a quarter could be created for an attaché the
 *       caller never named and the response looked like success.</li>
 * </ul>
 *
 * <p>The screen runs this as a background job ({@link MoheQuarterCreateJobs}, review
 * Q4): for every student it builds a full-profile snapshot, and a request that waited
 * for all of them timed out. {@link #validate} runs first, on the request thread, so a
 * bad period or country is still refused at once.
 */
@Service
@Transactional(readOnly = true)
public class MoheQuarterCreateService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(MoheQuarterCreateService.class);

    private static final int PAGE_SIZE = 200;

    /** How far a creation has got; the job reports it to the screen. */
    public interface Progress {
        Progress NONE = new Progress() { };

        /** Students in scope, known once the quarter row is written. */
        default void started(long totalStudents) { }

        /** {@code done} students archived and rolled over so far. */
        default void advanced(int done) { }
    }

    @Autowired private MoheQuarterRepository quarterRepository;
    @Autowired private StudentDataRepository studentDataRepository;
    @Autowired private StudentFinanceRepository studentFinanceRepository;
    @Autowired private StudentDataArchiveService studentDataArchiveService;
    @Autowired private StudentFinanceArchiveService studentFinanceArchiveService;
    @Autowired private StudentStudyStatusRepository studyStatusRepository;
    @Autowired private StudentCurrentLevelRepository currentLevelRepository;
    @Autowired private LuQuarterNumberRepository quarterNumberRepository;
    @Autowired private CountryScopeService countryScopeService;
    @Autowired private StudentProfileService studentProfileService;
    @Autowired private com.fasterxml.jackson.databind.ObjectMapper objectMapper;
    @jakarta.persistence.PersistenceContext private jakarta.persistence.EntityManager entityManager;

    /**
     * Serialise the full profile for the snapshot; never let it abort the archive.
     * Logged rather than swallowed so a missing snapshot can be traced to its student.
     */
    private String buildProfileSnapshot(Integer studentId) {
        try {
            return objectMapper.writeValueAsString(studentProfileService.buildProfile(studentId));
        } catch (Exception e) {
            log.warn("Profile snapshot for student {} could not be built; archiving without it: {}",
                    studentId, e.toString());
            return null;
        }
    }

    /** Synchronous creation, for callers that wait for it (tests; the job uses the overload). */
    @Transactional
    public Map<String, Object> createWithSnapshot(MoheQuarter entity) {
        return createWithSnapshot(entity, countryScopeService.getScope(), Progress.NONE);
    }

    /**
     * Everything that can refuse the request, without writing anything: period present
     * and valid, country in the caller's scope, and after the latest quarter on record.
     * Fills in {@code countryId} and the {@code QUARTER} key on {@code entity}.
     */
    public void validate(MoheQuarter entity, CountryScope scope) {
        requirePeriod(entity);
        validateQuarterNumber(entity);
        entity.setCountryId(resolveCountryId(entity.getCountryId(), scope));

        // Validate YYYYQ ordering: new quarter must be strictly after the current maximum.
        int newYYYYQ = QuarterFinanceTotalsService.quarterKey(entity.getQuarterYear(), entity.getQuarterNumber());
        Integer maxYYYYQ = quarterRepository.findMaxYearQuarter();
        if (maxYYYYQ != null && newYYYYQ <= maxYYYYQ) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "New quarter (" + newYYYYQ + ") must be after the current maximum (" + maxYYYYQ + "). "
                    + "Expected next: " + nextYYYYQ(maxYYYYQ));
        }

        // QUARTER is the YYYYQ period key, derived here rather than accepted from the
        // client: the column is NOT NULL, the screen has no control for it and never
        // sent one, so a create from the screen inserted NULL into it.
        entity.setQuarter(newYYYYQ);
    }

    /**
     * Creates the quarter and rolls every in-scope student over, in one transaction: if
     * anything fails, no archive is written and no student is advanced.
     *
     * @param scope the caller's scope, resolved on the request thread (a job thread has
     *              no request to resolve it from)
     */
    /** STUDY_FIN_RECEIVED_STATUS, for the NOT_SPECIFIED id the roll writes (live QA H2). */
    @Autowired private com.dilaw.mohe.repository.StudyFinReceivedStatusRepository finReceivedStatusRepository;

    @Transactional
    public Map<String, Object> createWithSnapshot(MoheQuarter entity, CountryScope scope, Progress progress) {
        // Again inside the transaction: another quarter may have been added since the
        // request-thread check.
        validate(entity, scope);
        entity.setId(null);
        MoheQuarter saved = quarterRepository.save(entity);

        // The three lookup tables the roll reads are loaded once, not once per student.
        QuarterRolloverRules rules = QuarterRolloverRules.of(
                saved, studyStatusRepository.findAll(), currentLevelRepository.findAll(),
                finReceivedStatusRepository.findAll());

        // Snapshot + advance, scoped by country.
        Specification<StudentDatum> spec = scope.isMinistryUser()
                ? Specification.where(null)
                : StudentDataSpec.countryScope(scope);

        AtomicInteger studentsArchived = new AtomicInteger();
        AtomicInteger financeArchived = new AtomicInteger();
        progress.started(studentDataRepository.count(spec));

        // Sorted: unsorted offset paging on SQL Server has no stable order, so a row
        // could be visited twice or skipped between pages.
        Pageable pageable = PageRequest.of(0, PAGE_SIZE, Sort.by("studentId"));
        while (true) {
            Page<StudentDatum> pg = studentDataRepository.findAll(spec, pageable);
            if (pg.isEmpty()) break;

            /*
             * The page's finance rows in one query. This used to be a findById per
             * student, plus a save of the finance row and a save of the student row
             * each, inside the loop — for 941 students that is close to three
             * thousand statements where three per page will do.
             */
            List<Integer> ids = pg.getContent().stream().map(StudentDatum::getStudentId).toList();
            Map<Integer, StudentFinance> financeById = studentFinanceRepository.findAllById(ids).stream()
                    .collect(Collectors.toMap(StudentFinance::getId, Function.identity(), (a, b) -> a));

            /*
             * Both archives are written from the rows as they stand, before anything
             * below touches them, so the snapshot is of the period being left. Same
             * transaction: if an archive fails, nothing is advanced or cleared. Both
             * are upserts, so re-running the roll refreshes rather than duplicates.
             * Each is one lookup and one saveAll for the page (review Q4).
             */
            // 1. Archive student data snapshots (with full-profile JSON).
            Map<Integer, String> snapshots = new java.util.HashMap<>();
            for (StudentDatum sd : pg.getContent()) {
                snapshots.put(sd.getStudentId(), buildProfileSnapshot(sd.getStudentId()));
            }
            studentsArchived.addAndGet(studentDataArchiveService.archiveAll(pg.getContent(), saved.getId(), snapshots));

            // 2. Archive the page's finance rows, each under the quarter it belongs to.
            List<StudentFinance> pageFinance = pg.getContent().stream()
                    .map(sd -> financeById.get(sd.getStudentId()))
                    .filter(java.util.Objects::nonNull)
                    .toList();
            financeArchived.addAndGet(studentFinanceArchiveService.archiveAll(pageFinance));

            for (StudentDatum sd : pg.getContent()) {
                // 3. Advance the finance record if present.
                StudentFinance fin = financeById.get(sd.getStudentId());
                if (fin != null) {
                    rules.advance(fin);
                }

                // 4. Roll the student over: period pointer, study status, year of study.
                rules.advance(sd);

                // 5. Advance student last-quarter pointer.
                sd.setStudentLastQuarter(saved.getId());
            }
            studentFinanceRepository.saveAll(pageFinance);
            studentDataRepository.saveAll(pg.getContent());

            // Written out and let go: otherwise every row of every page stays managed to
            // the end, and each flush dirty-checks all of them again.
            entityManager.flush();
            entityManager.clear();
            progress.advanced(studentsArchived.get());

            if (!pg.hasNext()) break;
            pageable = pg.nextPageable();
        }

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("quarterId", saved.getId());
        summary.put("quarterYear", saved.getQuarterYear());
        summary.put("quarterNumber", saved.getQuarterNumber());
        summary.put("yyyyQ", saved.getQuarter());
        summary.put("countryId", saved.getCountryId());
        summary.put("studentSnapshotsArchived", studentsArchived.get());
        summary.put("financeSnapshotsArchived", financeArchived.get());
        // Worth saying: the year of study only turns over on the third quarter, and an
        // operator who added a Q4 should not wonder why nobody moved up a year.
        summary.put("levelsAdvanced", rules.advancesLevel());
        return summary;
    }

    /**
     * The defaults the Add dialog opens with: the quarter that follows the latest one
     * on record, named the way the existing rows are named.
     *
     * <p>Nothing is reserved and nothing is written — the values are editable, and a
     * later quarter is still accepted, so a genuinely skipped period can be recorded.
     */
    public Map<String, Object> nextQuarterDefaults() {
        Integer maxYYYYQ = quarterRepository.findMaxYearQuarter();
        int next = maxYYYYQ == null ? currentCalendarQuarter() : nextYYYYQ(maxYYYYQ);
        int year = next / 10;
        int number = next % 10;

        Map<String, Object> defaults = new LinkedHashMap<>();
        defaults.put("quarter", next);
        defaults.put("quarterYear", year);
        defaults.put("quarterNumber", number);
        defaults.put("quarterName", quarterName(number, year, true));
        defaults.put("quarterNameEn", quarterName(number, year, false));
        return defaults;
    }

    /**
     * "الربع الرابع 2025" / "Quarter 4 2025" — the lookup's own name for the quarter
     * followed by the year, which reproduces exactly how the seven existing rows read.
     * An unknown quarter number falls back to the bare year rather than inventing a name.
     */
    private String quarterName(int number, int year, boolean arabic) {
        return quarterNumberRepository.findByIsDeletedFalseOrderBySortOrderAsc().stream()
                .filter(q -> String.valueOf(number).equals(q.getCode()))
                .map(q -> arabic ? q.getNameAr() : q.getNameEn())
                .filter(name -> name != null && !name.isBlank())
                .map(name -> name + " " + year)
                .findFirst()
                .orElseGet(() -> String.valueOf(year));
    }

    /** Used only when MOHE_QUARTER is empty and there is no last quarter to follow. */
    private static int currentCalendarQuarter() {
        java.time.LocalDate today = java.time.LocalDate.now();
        return QuarterFinanceTotalsService.quarterKey(today.getYear(), (today.getMonthValue() - 1) / 3 + 1);
    }

    /**
     * The period is what the whole roll is keyed on, and all three of QUARTER,
     * QUARTER_YEAR and QUARTER_NO are NOT NULL. Refusing here gives the caller a reason;
     * without it the driver reported a NOT NULL violation on a column the screen has no
     * control for.
     */
    private static void requirePeriod(MoheQuarter entity) {
        if (entity.getQuarterYear() == null || entity.getQuarterNumber() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "quarterYear and quarterNumber are required: they are the quarter's period");
        }
    }

    /**
     * The country the new quarter is stamped with.
     *
     * <p>Ministry: whatever was sent, normally null (global). Country user: the sent id
     * if it is one of theirs, 403 if it is not, and — when nothing was sent — their one
     * country if they have exactly one. A user holding several countries who names
     * none is asked to choose: guessing the first would file the quarter under an
     * attaché they did not pick.
     */
    static Integer resolveCountryId(Integer requested, CountryScope scope) {
        if (scope.isMinistryUser()) {
            return requested;
        }
        List<Integer> allowed = scope.getAllowedCountryIds();
        if (allowed.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "User has no country access assigned");
        }
        if (requested != null) {
            if (!allowed.contains(requested)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied: country not in scope");
            }
            return requested;
        }
        if (allowed.size() == 1) {
            return allowed.get(0);
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "countryId is required: the quarter must belong to one of your countries " + allowed);
    }

    /** Returns the YYYYQ value that logically follows the given one (e.g. 20244 → 20251). */
    static int nextYYYYQ(int current) {
        int year = current / 10;
        int q    = current % 10;
        return q < 4 ? year * 10 + (q + 1) : (year + 1) * 10 + 1;
    }

    private static void validateQuarterNumber(MoheQuarter entity) {
        if (entity.getQuarterNumber() == null) return;
        int n = entity.getQuarterNumber();
        if (n < 1 || n > 4) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Quarter number must be between 1 and 4");
        }
    }
}
