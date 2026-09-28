package com.dilaw.mohe.service;

import com.dilaw.mohe.dto.FinancialAuditMetadataDto;
import com.dilaw.mohe.dto.LookupOptionDto;
import com.dilaw.mohe.model.StudyFinanceBank;
import com.dilaw.mohe.model.StudyFinanceDof;
import com.dilaw.mohe.model.StudyFinReceivedStatus;
import com.dilaw.mohe.model.StudyReceivedWhomStatus;
import com.dilaw.mohe.model.StudyCurrencyIn;
import com.dilaw.mohe.repository.FinancialAuditRepository;
import com.dilaw.mohe.repository.StudyFinanceBankRepository;
import com.dilaw.mohe.repository.StudyFinanceDofRepository;
import com.dilaw.mohe.repository.StudyFinReceivedStatusRepository;
import com.dilaw.mohe.repository.StudyReceivedWhomStatusRepository;
import com.dilaw.mohe.repository.StudyCurrencyInRepository;
import com.dilaw.mohe.service.codelist.CodeListService;
import com.dilaw.mohe.util.LocalizedLookupOptions;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Transactional(readOnly = true)
@Service
public class FinancialAuditMetadataService {

    private final FinancialAuditRepository financialAuditRepository;
    private final StudyFinReceivedStatusRepository statusRepository;
    private final StudyReceivedWhomStatusRepository categoryRepository;
    private final StudyFinanceBankRepository bankRepository;
    private final StudyFinanceDofRepository auditorRepository;
    private final StudyCurrencyInRepository currencyRepository;
    private final CodeListService codeLists;

    public FinancialAuditMetadataService(FinancialAuditRepository financialAuditRepository,
                                         StudyFinReceivedStatusRepository statusRepository,
                                         StudyReceivedWhomStatusRepository categoryRepository,
                                         StudyFinanceBankRepository bankRepository,
                                         StudyFinanceDofRepository auditorRepository,
                                         StudyCurrencyInRepository currencyRepository,
                                         CodeListService codeLists) {
        this.codeLists = codeLists;
        this.financialAuditRepository = financialAuditRepository;
        this.statusRepository = statusRepository;
        this.categoryRepository = categoryRepository;
        this.bankRepository = bankRepository;
        this.auditorRepository = auditorRepository;
        this.currencyRepository = currencyRepository;
    }

    public FinancialAuditMetadataDto getMetadata() {
        List<LookupOptionDto> statuses = LocalizedLookupOptions.of(statusRepository.findAll(), StudyFinReceivedStatus::getId);
        if (statuses.isEmpty()) {
            statuses = financialAuditRepository.findDistinctStatuses().stream()
                    .filter(StringUtils::hasText)
                    .map(value -> new LookupOptionDto(null, value))
                    .sorted(Comparator.comparing(LookupOptionDto::getName, String.CASE_INSENSITIVE_ORDER))
                    .collect(Collectors.toList());
        }

        List<LookupOptionDto> categories = LocalizedLookupOptions.of(categoryRepository.findAll(), StudyReceivedWhomStatus::getId);
        if (categories.isEmpty()) {
            categories = financialAuditRepository.findDistinctCategories().stream()
                    .filter(StringUtils::hasText)
                    .map(value -> new LookupOptionDto(null, value))
                    .sorted(Comparator.comparing(LookupOptionDto::getName, String.CASE_INSENSITIVE_ORDER))
                    .collect(Collectors.toList());
        }

        List<LookupOptionDto> banks = LocalizedLookupOptions.of(bankRepository.findAll(), StudyFinanceBank::getId);
        if (banks.isEmpty()) {
            banks = financialAuditRepository.findDistinctBanks().stream()
                    .filter(StringUtils::hasText)
                    .map(value -> new LookupOptionDto(null, value))
                    .sorted(Comparator.comparing(LookupOptionDto::getName, String.CASE_INSENSITIVE_ORDER))
                    .collect(Collectors.toList());
        }

        List<LookupOptionDto> auditors = LocalizedLookupOptions.of(auditorRepository.findAll(), StudyFinanceDof::getId);
        if (auditors.isEmpty()) {
            auditors = financialAuditRepository.findDistinctAuditors().stream()
                    .filter(StringUtils::hasText)
                    .map(value -> new LookupOptionDto(null, value))
                    .sorted(Comparator.comparing(LookupOptionDto::getName, String.CASE_INSENSITIVE_ORDER))
                    .collect(Collectors.toList());
        }

        List<LookupOptionDto> currencies = LocalizedLookupOptions.of(currencyRepository.findAll(), StudyCurrencyIn::getId);
        if (currencies.isEmpty()) {
            currencies = financialAuditRepository.findDistinctCurrencies().stream()
                    .filter(StringUtils::hasText)
                    .map(value -> new LookupOptionDto(null, value))
                    .sorted(Comparator.comparing(LookupOptionDto::getName, String.CASE_INSENSITIVE_ORDER))
                    .collect(Collectors.toList());
        }

        List<LookupOptionDto> years = financialAuditRepository.findDistinctYears().stream()
                .filter(Objects::nonNull)
                .map(year -> new LookupOptionDto(year, String.valueOf(year)))
                .sorted(Comparator.comparing(LookupOptionDto::getId))
                .collect(Collectors.toList());

        List<LookupOptionDto> quarters = financialAuditRepository.findDistinctQuarters().stream()
                .filter(Objects::nonNull)
                .map(q -> new LookupOptionDto(q, codeLists.label("quarter-number", String.valueOf(q),
                        LocaleContextHolder.getLocale().getLanguage())))
                .sorted(Comparator.comparing(LookupOptionDto::getId))
                .collect(Collectors.toList());

        return FinancialAuditMetadataDto.builder()
                .statuses(statuses)
                .categories(categories)
                .banks(banks)
                .auditors(auditors)
                // An empty currency list stays empty. This used to fall back to a
                // single fabricated "USD" option, so the filter offered a currency
                // no audited row was recorded in and every selection returned
                // nothing. STUDENT_FINANCE.STUDY_CURRENCY_IN_ID is currently NULL
                // throughout, so empty is the accurate answer.
                .currencies(currencies)
                .years(years)
                .quarters(quarters)
                .build();
    }
}

