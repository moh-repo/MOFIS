package com.dilaw.mohe.service;

import com.dilaw.mohe.model.BankTransaction;
import com.dilaw.mohe.repository.BankTransactionRepository;
import com.dilaw.mohe.ums.service.ReportCountryScopeHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Transactional(readOnly = true)
@Service
public class BankTransactionService {

    private final BankTransactionRepository repository;
    private final ReportCountryScopeHelper countryScopeHelper;

    @Autowired
    public BankTransactionService(BankTransactionRepository repository,
                                  ReportCountryScopeHelper countryScopeHelper) {
        this.repository = repository;
        this.countryScopeHelper = countryScopeHelper;
    }

    public List<BankTransaction> getAll(Integer countryId) {
        return getByFilters(countryId, null, null, null, null, null, null, null, null,
                Pageable.unpaged()).getContent();
    }

    public Page<BankTransaction> getByFilters(
            Integer countryId,
            List<Integer> requestCountryIds,
            String type,
            String bankName,
            String branchName,
            String currency,
            LocalDate startDate,
            LocalDate endDate,
            String search,
            Pageable pageable) {
        countryScopeHelper.ensureScopeOrThrow();
        List<Integer> countryIds = countryScopeHelper.resolveCountryIds(countryId, requestCountryIds);
        boolean applyCountry = countryScopeHelper.shouldApplyCountryFilter(countryIds);
        return repository.findByFilters(type, bankName, branchName, currency, startDate, endDate,
                applyCountry, countryScopeHelper.safeList(countryIds), search, pageable);
    }

    public List<BankTransaction> getByType(Integer countryId, String type) {
        countryScopeHelper.ensureScopeOrThrow();
        List<Integer> countryIds = countryScopeHelper.resolveCountryIds(countryId);
        boolean applyCountry = countryScopeHelper.shouldApplyCountryFilter(countryIds);
        return repository.findByTransactionType(type, applyCountry, countryScopeHelper.safeList(countryIds));
    }

    public List<BankTransaction> getByBankName(Integer countryId, String bankName) {
        countryScopeHelper.ensureScopeOrThrow();
        List<Integer> countryIds = countryScopeHelper.resolveCountryIds(countryId);
        boolean applyCountry = countryScopeHelper.shouldApplyCountryFilter(countryIds);
        return repository.findByBankName(bankName, applyCountry, countryScopeHelper.safeList(countryIds));
    }
}
