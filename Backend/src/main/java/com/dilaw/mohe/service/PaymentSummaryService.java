package com.dilaw.mohe.service;

import com.dilaw.mohe.dto.LookupOptionDto;
import com.dilaw.mohe.dto.PaymentSummaryMetadataDto;
import com.dilaw.mohe.dto.PaymentSummaryTotalsDto;
import com.dilaw.mohe.model.PaymentSummary;
import com.dilaw.mohe.model.StudyFinReceivedStatus;
import com.dilaw.mohe.model.StudyFinanceDofYear;
import com.dilaw.mohe.model.StudyReceivedWhomStatus;
import com.dilaw.mohe.repository.MoheQuarterRepository;
import com.dilaw.mohe.repository.PaymentSummaryRepository;
import com.dilaw.mohe.repository.StudyFinReceivedStatusRepository;
import com.dilaw.mohe.repository.StudyFinanceDofYearRepository;
import com.dilaw.mohe.repository.StudyReceivedWhomStatusRepository;
import com.dilaw.mohe.ums.service.ReportCountryScopeHelper;
import com.dilaw.mohe.util.LocalizedLookupOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Transactional(readOnly = true)
@Service
public class PaymentSummaryService {
    
    @Autowired
    private PaymentSummaryRepository repository;
    
    @Autowired
    private StudyFinReceivedStatusRepository studyFinReceivedStatusRepository;
    
    @Autowired
    private StudyReceivedWhomStatusRepository studyReceivedWhomStatusRepository;
    
    @Autowired
    private MoheQuarterRepository moheQuarterRepository;
    
    @Autowired
    private StudyFinanceDofYearRepository studyFinanceDofYearRepository;

    @Autowired
    private ReportCountryScopeHelper countryScopeHelper;

    public List<PaymentSummary> getAll(Integer countryId) {
        return getByFilters(countryId, null, null, null, null, null, null, null, null, null,
                Pageable.unpaged()).getContent();
    }

    public Page<PaymentSummary> getByFilters(
            Integer countryId,
            List<Integer> requestCountryIds,
            String status,
            String type,
            String method,
            String quarter,
            Integer year,
            LocalDate startDate,
            LocalDate endDate,
            String search,
            Pageable pageable) {
        countryScopeHelper.ensureScopeOrThrow();
        List<Integer> countryIds = countryScopeHelper.resolveCountryIds(countryId, requestCountryIds);
        boolean applyCountry = countryScopeHelper.shouldApplyCountryFilter(countryIds);
        return repository.findByFilters(status, type, method, quarter, year, startDate, endDate,
                applyCountry, countryScopeHelper.inClauseIds(countryIds, applyCountry), search, pageable);
    }

    /**
     * Total Amount and the amount per status over every row {@link #getByFilters} would
     * page through, under the same scope (round-6 QA R6-05). The screen summed the page
     * it held, so its Total Amount showed 24,300 of 1,596,620.
     */
    public PaymentSummaryTotalsDto getTotals(
            Integer countryId,
            List<Integer> requestCountryIds,
            String status,
            String type,
            String method,
            String quarter,
            Integer year,
            LocalDate startDate,
            LocalDate endDate,
            String search) {
        countryScopeHelper.ensureScopeOrThrow();
        List<Integer> countryIds = countryScopeHelper.resolveCountryIds(countryId, requestCountryIds);
        boolean applyCountry = countryScopeHelper.shouldApplyCountryFilter(countryIds);
        List<Object[]> rows = repository.sumByStatusCode(status, type, method, quarter, year, startDate, endDate,
                applyCountry, countryScopeHelper.inClauseIds(countryIds, applyCountry), search);
        return totalsOf(rows);
    }

    /** Folds {@code [code, amount]} rows; a NULL sum (no amount on any row) counts as 0. */
    static PaymentSummaryTotalsDto totalsOf(List<Object[]> rows) {
        BigDecimal total = BigDecimal.ZERO;
        List<PaymentSummaryTotalsDto.StatusAmount> byStatus = new ArrayList<>();
        for (Object[] row : rows) {
            BigDecimal amount = row[1] == null ? BigDecimal.ZERO : new BigDecimal(row[1].toString());
            total = total.add(amount);
            byStatus.add(new PaymentSummaryTotalsDto.StatusAmount(row[0] == null ? null : row[0].toString(), amount));
        }
        return PaymentSummaryTotalsDto.builder().totalAmount(total).byStatus(byStatus).build();
    }

    public List<PaymentSummary> getByStatus(Integer countryId, String status) {
        countryScopeHelper.ensureScopeOrThrow();
        List<Integer> countryIds = countryScopeHelper.resolveCountryIds(countryId);
        boolean applyCountry = countryScopeHelper.shouldApplyCountryFilter(countryIds);
        return repository.findByPaymentStatus(status, applyCountry, countryScopeHelper.inClauseIds(countryIds, applyCountry));
    }

    public List<PaymentSummary> getByType(Integer countryId, String type) {
        countryScopeHelper.ensureScopeOrThrow();
        List<Integer> countryIds = countryScopeHelper.resolveCountryIds(countryId);
        boolean applyCountry = countryScopeHelper.shouldApplyCountryFilter(countryIds);
        return repository.findByPaymentType(type, applyCountry, countryScopeHelper.inClauseIds(countryIds, applyCountry));
    }

    public List<PaymentSummary> getByYear(Integer countryId, Integer year) {
        countryScopeHelper.ensureScopeOrThrow();
        List<Integer> countryIds = countryScopeHelper.resolveCountryIds(countryId);
        boolean applyCountry = countryScopeHelper.shouldApplyCountryFilter(countryIds);
        return repository.findByPaymentYear(year, applyCountry, countryScopeHelper.inClauseIds(countryIds, applyCountry));
    }
    
    public PaymentSummaryMetadataDto getMetadata() {
        List<LookupOptionDto> statuses = LocalizedLookupOptions.of(studyFinReceivedStatusRepository.findAll(), StudyFinReceivedStatus::getId);
        if (statuses.isEmpty()) {
            statuses = repository.findDistinctStatuses().stream()
                    .filter(Objects::nonNull)
                    .map(value -> new LookupOptionDto(null, value))
                    .collect(Collectors.toList());
        }
        
        List<LookupOptionDto> types = LocalizedLookupOptions.of(studyReceivedWhomStatusRepository.findAll(), StudyReceivedWhomStatus::getId);
        if (types.isEmpty()) {
            types = repository.findDistinctTypes().stream()
                    .filter(Objects::nonNull)
                    .map(value -> new LookupOptionDto(null, value))
                    .collect(Collectors.toList());
        }
        
        
        // Quarters keep their table order; only the name follows the reader's language (M13).
        boolean arabic = !LocalizedLookupOptions.english();
        List<LookupOptionDto> quarters = moheQuarterRepository.findAll().stream()
                .filter(Objects::nonNull)
                .map(q -> new LookupOptionDto(q.getId(),
                        LocalizedLookupOptions.pick(arabic ? q.getQuarterName() : q.getQuarterNameEn(),
                                arabic ? q.getQuarterNameEn() : q.getQuarterName(), q.getId())))
                .sorted(Comparator.comparing(LookupOptionDto::getId))
                .collect(Collectors.toList());
        if (quarters.isEmpty()) {
            quarters = repository.findDistinctQuarters().stream()
                    .filter(Objects::nonNull)
                    .map(value -> new LookupOptionDto(null, value))
                    .collect(Collectors.toList());
        }
        
        List<LookupOptionDto> years = studyFinanceDofYearRepository.findAll().stream()
                .map(this::mapYear)
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(LookupOptionDto::getId, Comparator.nullsLast(Integer::compareTo)))
                .collect(Collectors.toList());
        if (years.isEmpty()) {
            years = repository.findDistinctYears().stream()
                    .filter(Objects::nonNull)
                    .map(year -> new LookupOptionDto(year, String.valueOf(year)))
                    .collect(Collectors.toList());
        }
        
        return PaymentSummaryMetadataDto.builder()
                .statuses(statuses)
                .types(types)
                .quarters(quarters)
                .years(years)
                .build();
    }

    private LookupOptionDto mapYear(StudyFinanceDofYear year) {
        if (year == null || year.getStudyFinanceDofYearName() == null) {
            return null;
        }
        Integer parsed = parseYear(year.getStudyFinanceDofYearName());
        if (parsed == null) {
            return null;
        }
        return new LookupOptionDto(parsed, year.getStudyFinanceDofYearName());
    }
    
    private Integer parseYear(String value) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }
    
}

