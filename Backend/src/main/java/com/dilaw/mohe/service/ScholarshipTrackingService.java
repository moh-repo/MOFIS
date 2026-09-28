package com.dilaw.mohe.service;

import com.dilaw.mohe.dto.LookupOptionDto;
import com.dilaw.mohe.dto.ScholarshipTrackingMetadataDto;
import com.dilaw.mohe.model.ScholarshipTracking;
import com.dilaw.mohe.model.StudentAgencyType;
import com.dilaw.mohe.model.StudentStudyStatus;
import com.dilaw.mohe.model.StudentStudyType;
import com.dilaw.mohe.repository.ScholarshipTrackingRepository;
import com.dilaw.mohe.repository.StudentAgencyTypeRepository;
import com.dilaw.mohe.repository.StudentStudyStatusRepository;
import com.dilaw.mohe.repository.StudentStudyTypeRepository;
import com.dilaw.mohe.ums.service.ReportCountryScopeHelper;
import com.dilaw.mohe.util.LocalizedLookupOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Transactional(readOnly = true)
@Service
public class ScholarshipTrackingService {

    @Autowired
    private ScholarshipTrackingRepository repository;

    @Autowired
    private StudentStudyTypeRepository studentStudyTypeRepository;

    @Autowired
    private StudentAgencyTypeRepository studentAgencyTypeRepository;

    @Autowired
    private StudentStudyStatusRepository studentStudyStatusRepository;

    @Autowired
    private ReportCountryScopeHelper countryScopeHelper;

    public List<ScholarshipTracking> getAll(Integer countryId) {
        return getByFilters(countryId, null, null, null, null, null, Pageable.unpaged()).getContent();
    }

    public Page<ScholarshipTracking> getByFilters(
            Integer countryId,
            List<Integer> requestCountryIds,
            String type,
            String provider,
            String status,
            String search,
            Pageable pageable) {
        countryScopeHelper.ensureScopeOrThrow();
        List<Integer> countryIds = countryScopeHelper.resolveCountryIds(countryId, requestCountryIds);
        boolean applyCountry = countryScopeHelper.shouldApplyCountryFilter(countryIds);
        return repository.findByFilters(type, provider, status, applyCountry,
                countryScopeHelper.safeList(countryIds), search, pageable);
    }

    public List<ScholarshipTracking> getByType(Integer countryId, String type) {
        countryScopeHelper.ensureScopeOrThrow();
        List<Integer> countryIds = countryScopeHelper.resolveCountryIds(countryId);
        boolean applyCountry = countryScopeHelper.shouldApplyCountryFilter(countryIds);
        return repository.findByScholarshipType(type, applyCountry, countryScopeHelper.safeList(countryIds));
    }

    public List<ScholarshipTracking> getByProvider(Integer countryId, String provider) {
        countryScopeHelper.ensureScopeOrThrow();
        List<Integer> countryIds = countryScopeHelper.resolveCountryIds(countryId);
        boolean applyCountry = countryScopeHelper.shouldApplyCountryFilter(countryIds);
        return repository.findByScholarshipProvider(provider, applyCountry, countryScopeHelper.safeList(countryIds));
    }

    public List<ScholarshipTracking> getByStatus(Integer countryId, String status) {
        countryScopeHelper.ensureScopeOrThrow();
        List<Integer> countryIds = countryScopeHelper.resolveCountryIds(countryId);
        boolean applyCountry = countryScopeHelper.shouldApplyCountryFilter(countryIds);
        return repository.findByScholarshipStatus(status, applyCountry, countryScopeHelper.safeList(countryIds));
    }

    public ScholarshipTrackingMetadataDto getMetadata() {
        List<LookupOptionDto> types = LocalizedLookupOptions.of(studentStudyTypeRepository.findAll(), StudentStudyType::getId);
        if (types.isEmpty()) {
            types = repository.findDistinctTypes().stream()
                    .map(value -> new LookupOptionDto(null, value))
                    .collect(Collectors.toList());
        }

        List<LookupOptionDto> providers = LocalizedLookupOptions.of(studentAgencyTypeRepository.findAll(), StudentAgencyType::getId);
        if (providers.isEmpty()) {
            providers = repository.findDistinctProviders().stream()
                    .map(value -> new LookupOptionDto(null, value))
                    .collect(Collectors.toList());
        }

        List<LookupOptionDto> statuses = LocalizedLookupOptions.of(studentStudyStatusRepository.findAll(), StudentStudyStatus::getId);
        if (statuses.isEmpty()) {
            statuses = repository.findDistinctStatuses().stream()
                    .map(value -> new LookupOptionDto(null, value))
                    .collect(Collectors.toList());
        }

        return ScholarshipTrackingMetadataDto.builder()
                .types(types)
                .providers(providers)
                .statuses(statuses)
                .build();
    }
}
