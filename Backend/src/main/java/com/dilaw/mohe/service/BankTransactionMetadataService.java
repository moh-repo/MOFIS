package com.dilaw.mohe.service;

import com.dilaw.mohe.dto.BankTransactionMetadataDto;
import com.dilaw.mohe.dto.LookupOptionDto;
import com.dilaw.mohe.model.StudyFinanceBank;
import com.dilaw.mohe.model.StudyFinanceBankBranch;
import com.dilaw.mohe.model.StudyFinReceivedStatus;
import com.dilaw.mohe.model.StudyCurrencyIn;
import com.dilaw.mohe.repository.BankTransactionRepository;
import com.dilaw.mohe.repository.StudyFinanceBankBranchRepository;
import com.dilaw.mohe.repository.StudyFinanceBankRepository;
import com.dilaw.mohe.repository.StudyFinReceivedStatusRepository;
import com.dilaw.mohe.repository.StudyCurrencyInRepository;
import com.dilaw.mohe.util.LocalizedLookupOptions;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Transactional(readOnly = true)
@Service
public class BankTransactionMetadataService {

    private final BankTransactionRepository bankTransactionRepository;
    private final StudyFinanceBankRepository bankRepository;
    private final StudyFinanceBankBranchRepository bankBranchRepository;
    private final StudyFinReceivedStatusRepository statusRepository;
    private final StudyCurrencyInRepository currencyRepository;

    public BankTransactionMetadataService(BankTransactionRepository bankTransactionRepository,
                                          StudyFinanceBankRepository bankRepository,
                                          StudyFinanceBankBranchRepository bankBranchRepository,
                                          StudyFinReceivedStatusRepository statusRepository,
                                          StudyCurrencyInRepository currencyRepository) {
        this.bankTransactionRepository = bankTransactionRepository;
        this.bankRepository = bankRepository;
        this.bankBranchRepository = bankBranchRepository;
        this.statusRepository = statusRepository;
        this.currencyRepository = currencyRepository;
    }

    public BankTransactionMetadataDto getMetadata() {
        List<LookupOptionDto> transactionTypes = LocalizedLookupOptions.of(statusRepository.findAll(), StudyFinReceivedStatus::getId);
        if (transactionTypes.isEmpty()) {
            transactionTypes = bankTransactionRepository.findDistinctTypes().stream()
                    .filter(StringUtils::hasText)
                    .map(value -> new LookupOptionDto(null, value))
                    .sorted(Comparator.comparing(LookupOptionDto::getName, String.CASE_INSENSITIVE_ORDER))
                    .collect(Collectors.toList());
        }

        List<LookupOptionDto> banks = LocalizedLookupOptions.of(bankRepository.findAll(), StudyFinanceBank::getId);
        if (banks.isEmpty()) {
            banks = bankTransactionRepository.findDistinctBanks().stream()
                    .filter(StringUtils::hasText)
                    .map(value -> new LookupOptionDto(null, value))
                    .sorted(Comparator.comparing(LookupOptionDto::getName, String.CASE_INSENSITIVE_ORDER))
                    .collect(Collectors.toList());
        }

        List<LookupOptionDto> bankBranches = LocalizedLookupOptions.of(bankBranchRepository.findAll(), StudyFinanceBankBranch::getId);
        if (bankBranches.isEmpty()) {
            bankBranches = bankTransactionRepository.findDistinctBranches().stream()
                    .filter(StringUtils::hasText)
                    .map(value -> new LookupOptionDto(null, value))
                    .sorted(Comparator.comparing(LookupOptionDto::getName, String.CASE_INSENSITIVE_ORDER))
                    .collect(Collectors.toList());
        }

        List<LookupOptionDto> currencies = LocalizedLookupOptions.of(currencyRepository.findAll(), StudyCurrencyIn::getId);
        if (currencies.isEmpty()) {
            currencies = bankTransactionRepository.findDistinctCurrencies().stream()
                    .filter(StringUtils::hasText)
                    .map(value -> new LookupOptionDto(null, value))
                    .sorted(Comparator.comparing(LookupOptionDto::getName, String.CASE_INSENSITIVE_ORDER))
                    .collect(Collectors.toList());
        }

        return BankTransactionMetadataDto.builder()
                .transactionTypes(transactionTypes)
                .banks(banks)
                .bankBranches(bankBranches)
                .currencies(currencies)
                .build();
    }
}

