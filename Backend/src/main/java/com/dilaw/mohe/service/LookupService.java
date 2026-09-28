package com.dilaw.mohe.service;

import com.dilaw.mohe.config.CacheConfig;
import com.dilaw.mohe.model.*;
import com.dilaw.mohe.repository.*;
import com.dilaw.mohe.ums.repository.UmsUserCountryRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * The parameterless getAll* lists are cached ({@link CacheConfig#LOOKUPS},
 * 60&nbsp;s TTL): they were re-fetched from the database on every request that
 * built a filter dropdown. Cached lists are shared, detached entities — callers
 * must not mutate them. The country lists are deliberately NOT cached here:
 * they are user/privilege-scoped, and name maps go through CountryNameService.
 */
@Transactional(readOnly = true)
@Service
public class LookupService {

    @Autowired
    private StudyCountryInRepository studyCountryInRepository;

    @Autowired
    private StudyGovInRepository studyGovInRepository;

    @Autowired
    private StudyFinanceBankRepository studyFinanceBankRepository;

    @Autowired
    private StudyFinanceBankBranchRepository studyFinanceBankBranchRepository;

    @Autowired
    private StudyFinanceDofRepository studyFinanceDofRepository;

    @Autowired
    private StudyFinReceivedStatusRepository studyFinReceivedStatusRepository;

    @Autowired
    private StudyReceivedWhomStatusRepository studyReceivedWhomStatusRepository;

    @Autowired
    private StudyFinReceivedWhomRepository studyFinReceivedWhomRepository;

    @Autowired
    private StudentCountryRepository studentCountryRepository;

    @Autowired
    private StudentAccountRepository studentAccountRepository;

    @Autowired
    private StudentDataRepository studentDataRepository;

    @Autowired
    private UmsUserCountryRepository umsUserCountryRepository;

    // Study Country methods
    public List<StudyCountryIn> getAllStudyCountries() {
        return studyCountryInRepository.findAll();
    }

    /** Study countries that appear in active UMS user-privilege assignments (for login country selector). */
    public List<StudyCountryIn> getStudyCountriesFromUserPrivileges() {
        List<Integer> ids = umsUserCountryRepository.findDistinctActiveCountryIds();
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        List<StudyCountryIn> list = new java.util.ArrayList<>(studyCountryInRepository.findAllById(ids));
        list.sort((a, b) -> {
            String na = a.getStudyCountryInName() != null ? a.getStudyCountryInName() : "";
            String nb = b.getStudyCountryInName() != null ? b.getStudyCountryInName() : "";
            return na.compareToIgnoreCase(nb);
        });
        return list;
    }

    /** Study countries that have at least one student record (for login country selector). */
    /**
     * Study countries that students are actually enrolled in — the list behind the
     * country selector on the student-portal login page.
     *
     * <p>Runs as system. This is an anonymous endpoint: the caller is at the login
     * screen and has no scope, so under the V031 policy the underlying
     * {@code STUDENT_DATA} read returns nothing and the selector renders empty —
     * leaving a required field that cannot be filled in. It is also the correct
     * scope on the merits: the list a student chooses their country from cannot
     * be filtered by the country they have not chosen yet.
     *
     * <p>What this discloses is the set of countries the ministry sends students
     * to, and nothing per-student. The main system's login selector
     * ({@link #getStudyCountriesFromUserPrivileges}) is anonymous on the same
     * basis.
     */
    public List<StudyCountryIn> getStudyCountriesFromStudentAccount() {
        List<Integer> ids = com.dilaw.mohe.config.tenant.TenantContext.runAsSystem(
                studentDataRepository::findDistinctStudyCountryInIds);
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        List<StudyCountryIn> list = studyCountryInRepository.findAllById(ids);
        list.sort((a, b) -> {
            String na = a.getStudyCountryInName() != null ? a.getStudyCountryInName() : "";
            String nb = b.getStudyCountryInName() != null ? b.getStudyCountryInName() : "";
            return na.compareToIgnoreCase(nb);
        });
        return list;
    }

    // Study Government methods
    @Cacheable(cacheNames = CacheConfig.LOOKUPS, key = "'lookup:studyGovernments'")
    public List<StudyGovIn> getAllStudyGovernments() {
        return studyGovInRepository.findAll();
    }

    public List<StudyGovIn> getStudyGovernmentsByCountry(Integer countryId) {
        return studyGovInRepository.findByStudyCountryInId(countryId);
    }

    // Study Finance Bank methods
    @Cacheable(cacheNames = CacheConfig.LOOKUPS, key = "'lookup:financeBanks'")
    public List<StudyFinanceBank> getAllStudyFinanceBanks() {
        return studyFinanceBankRepository.findAll();
    }

    public List<StudyFinanceBank> getStudyFinanceBanksByCountry(Integer countryId) {
        return studyFinanceBankRepository.findByStudyCountryInId(countryId);
    }

    // Study Finance Bank Branch methods
    @Cacheable(cacheNames = CacheConfig.LOOKUPS, key = "'lookup:financeBankBranches'")
    public List<StudyFinanceBankBranch> getAllStudyFinanceBankBranches() {
        return studyFinanceBankBranchRepository.findAll();
    }

    public List<StudyFinanceBankBranch> getStudyFinanceBankBranchesByBank(Integer bankId) {
        return studyFinanceBankBranchRepository.findByStudyFinanceBankeId(bankId);
    }

    // Study Finance DOF methods
    @Cacheable(cacheNames = CacheConfig.LOOKUPS, key = "'lookup:financeDofs'")
    public List<StudyFinanceDof> getAllStudyFinanceDofs() {
        return studyFinanceDofRepository.findAll();
    }

    // Study Finance Received Status methods
    @Cacheable(cacheNames = CacheConfig.LOOKUPS, key = "'lookup:finReceivedStatuses'")
    public List<StudyFinReceivedStatus> getAllStudyFinReceivedStatuses() {
        return studyFinReceivedStatusRepository.findAll();
    }

    // Study Received Whom Status methods
    @Cacheable(cacheNames = CacheConfig.LOOKUPS, key = "'lookup:receivedWhomStatuses'")
    public List<StudyReceivedWhomStatus> getAllStudyReceivedWhomStatuses() {
        return studyReceivedWhomStatusRepository.findAll();
    }

    // Study Fin Received Whom methods
    @Cacheable(cacheNames = CacheConfig.LOOKUPS, key = "'lookup:finReceivedWhoms'")
    public List<StudyFinReceivedWhom> getAllStudyFinReceivedWhoms() {
        return studyFinReceivedWhomRepository.findAll();
    }

    // Student Country methods
    @Cacheable(cacheNames = CacheConfig.LOOKUPS, key = "'lookup:studentCountries'")
    public List<StudentCountry> getAllStudentCountries() {
        return studentCountryRepository.findAll();
    }
}
