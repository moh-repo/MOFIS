package com.dilaw.mohe.controler;

import com.dilaw.mohe.config.MoheSecurityExpressions;
import com.dilaw.mohe.model.*;
import com.dilaw.mohe.service.LookupService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Reference lists: countries, governorates, banks, branches, DOFs, statuses.
 *
 * <p>The gate here was {@code STUDENT_PII}, which none of these are — a bank
 * branch is not a student's personal data. The three roles that run the finance
 * screens (Finance Manager, FINANCE_OFFICER, WAFAR_OFFICER) hold no
 * {@code STUDENTS.*} permission at all, so for them every one of these answered
 * 403 and every dropdown on the finance recipients form rendered empty. Two
 * endpoints had already been carved out with {@code permitAll()} to get around
 * the same gate.
 *
 * <p>{@link MoheSecurityExpressions#LOOKUP_TABLE} is the expression the lookup
 * tables already use for exactly this: a GET is reference data, a write needs an
 * admin, and there are no writes here. {@code /received-whoms} is the one
 * exception below — it returns recipient records, not a catalogue.
 */
@RestController
@RequestMapping("api/v1/lookup")
@PreAuthorize(MoheSecurityExpressions.LOOKUP_TABLE)
public class LookupController {

    @Autowired
    private LookupService service;

    @GetMapping("/study-countries")
    public List<StudyCountryIn> getAllStudyCountries() {
        return service.getAllStudyCountries();
    }

    @GetMapping("/study-countries-from-student-account")
    @PreAuthorize("permitAll()")
    public List<StudyCountryIn> getStudyCountriesFromStudentAccount() {
        return service.getStudyCountriesFromStudentAccount();
    }

    @GetMapping("/study-countries-for-login")
    @PreAuthorize("permitAll()")
    public List<StudyCountryIn> getStudyCountriesForLogin() {
        return service.getStudyCountriesFromUserPrivileges();
    }

    @GetMapping("/study-governments")
    public List<StudyGovIn> getAllStudyGovernments() {
        return service.getAllStudyGovernments();
    }

    @GetMapping("/study-governments/by-country/{countryId}")
    public List<StudyGovIn> getByCountry(@PathVariable Integer countryId) {
        return service.getStudyGovernmentsByCountry(countryId);
    }

    @GetMapping({"/banks", "/study-finance-banks"})
    public List<StudyFinanceBank> getAllBanks() {
        return service.getAllStudyFinanceBanks();
    }

    @GetMapping({"/banks/by-country/{countryId}", "/study-finance-banks/by-country/{countryId}"})
    public List<StudyFinanceBank> getBanksByCountry(@PathVariable Integer countryId) {
        return service.getStudyFinanceBanksByCountry(countryId);
    }

    @GetMapping({"/bank-branches", "/study-finance-bank-branches"})
    public List<StudyFinanceBankBranch> getAllBankBranches() {
        return service.getAllStudyFinanceBankBranches();
    }

    @GetMapping({"/bank-branches/by-bank/{bankId}", "/study-finance-bank-branches/by-bank/{bankId}"})
    public List<StudyFinanceBankBranch> getBankBranchesByBank(@PathVariable Integer bankId) {
        return service.getStudyFinanceBankBranchesByBank(bankId);
    }

    @GetMapping({"/dofs", "/study-finance-dofs"})
    public List<StudyFinanceDof> getAllDofs() {
        return service.getAllStudyFinanceDofs();
    }

    @GetMapping({"/received-statuses", "/study-fin-received-statuses"})
    public List<StudyFinReceivedStatus> getAllReceivedStatuses() {
        return service.getAllStudyFinReceivedStatuses();
    }

    @GetMapping({"/received-whom-statuses", "/study-received-whom-statuses"})
    public List<StudyReceivedWhomStatus> getAllReceivedWhomStatuses() {
        return service.getAllStudyReceivedWhomStatuses();
    }

    /**
     * Not a lookup: these are recipient records, with names and passport numbers.
     * It keeps a real gate while the catalogues above are opened for reading.
     */
    @PreAuthorize(MoheSecurityExpressions.FINANCE)
    @GetMapping({"/received-whoms", "/study-fin-received-whoms"})
    public List<StudyFinReceivedWhom> getAllReceivedWhoms() {
        return service.getAllStudyFinReceivedWhoms();
    }

    @GetMapping("/student-countries")
    public List<StudentCountry> getAllStudentCountries() {
        return service.getAllStudentCountries();
    }
}