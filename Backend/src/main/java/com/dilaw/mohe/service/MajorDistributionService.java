package com.dilaw.mohe.service;

import com.dilaw.mohe.model.MajorDistribution;
import com.dilaw.mohe.dto.LookupOptionDto;
import com.dilaw.mohe.dto.MajorDistributionMetadataDto;
import com.dilaw.mohe.model.StudentDegree;
import com.dilaw.mohe.model.StudentMajor;
import com.dilaw.mohe.model.StudentStudyType;
import com.dilaw.mohe.repository.MajorDistributionRepository;
import com.dilaw.mohe.repository.StudentDegreeRepository;
import com.dilaw.mohe.repository.StudentMajorRepository;
import com.dilaw.mohe.repository.StudentStudyTypeRepository;
import com.dilaw.mohe.ums.service.ReportCountryScopeHelper;
import com.dilaw.mohe.util.LocalizedLookupOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Transactional(readOnly = true)
@Service
public class MajorDistributionService {

    @Autowired
    private MajorDistributionRepository repository;

    @Autowired
    private StudentMajorRepository majorRepository;

    @Autowired
    private StudentDegreeRepository degreeRepository;

    @Autowired
    private CountryNameService countryNameService;

    @Autowired
    private StudentStudyTypeRepository studyTypeRepository;

    @Autowired
    private ReportCountryScopeHelper countryScopeHelper;

    public List<MajorDistribution> search(Integer countryId, List<Integer> requestCountryIds, Integer majorId, Integer degreeId, Integer studyTypeId) {
        countryScopeHelper.ensureScopeOrThrow();
        List<Integer> countryIds = countryScopeHelper.resolveCountryIds(countryId, requestCountryIds);
        boolean applyCountry = countryScopeHelper.shouldApplyCountryFilter(countryIds);
        return repository.search(
                majorId, degreeId, applyCountry, countryScopeHelper.safeList(countryIds), studyTypeId);
    }

    public List<MajorDistribution> getAllOrderByCount() {
        return search(null, null, null, null, null);
    }

    public List<MajorDistribution> getByCountry(Integer countryId) {
        return search(countryId, null, null, null, null);
    }

    public List<MajorDistribution> getByMajor(Integer majorId) {
        return search(null, null, majorId, null, null);
    }

    public MajorDistributionMetadataDto getMetadata() {
        return MajorDistributionMetadataDto.builder()
                .majors(LocalizedLookupOptions.of(majorRepository.findAll(), StudentMajor::getId))
                .degrees(LocalizedLookupOptions.of(degreeRepository.findAll(), StudentDegree::getId))
                .countries(countryNameService.all().stream()
                        // name(...) rather than a raw name: the comparator below would NPE on a null name.
                        .map(c -> new LookupOptionDto(c.id(), c.name(!LocalizedLookupOptions.english())))
                        .sorted((a, b) -> a.getName().compareToIgnoreCase(b.getName()))
                        .collect(Collectors.toList()))
                .studyTypes(LocalizedLookupOptions.of(studyTypeRepository.findAll(), StudentStudyType::getId))
                .build();
    }
}
