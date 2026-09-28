package com.dilaw.mohe.service;

import com.dilaw.mohe.dto.LookupOptionDto;
import com.dilaw.mohe.dto.StudentExtensionMetadataDto;
import com.dilaw.mohe.repository.StudentDataRepository;
import com.dilaw.mohe.repository.StudentExtensionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Transactional(readOnly = true)
@Service
@RequiredArgsConstructor
public class StudentExtensionMetadataService {

    private final StudentExtensionRepository extensionRepository;
    private final StudentDataRepository studentDataRepository;
    private final StudentExtensionLocalizationService localizationService;

    public StudentExtensionMetadataDto getMetadata(String lang) {
        List<LookupOptionDto> students = studentDataRepository.findAllStudentOptions().stream()
                .filter(option -> option != null && option.getId() != null && StringUtils.hasText(option.getName()))
                .sorted(Comparator.comparing(option -> option.getName().trim(), String.CASE_INSENSITIVE_ORDER))
                .collect(Collectors.toList());

        if (students.isEmpty()) {
            List<Integer> extensionStudentIds = extensionRepository.findDistinctStudentIds();
            if (!CollectionUtils.isEmpty(extensionStudentIds)) {
                students = studentDataRepository.findStudentOptionsByIds(extensionStudentIds).stream()
                        .filter(option -> option != null && option.getId() != null && StringUtils.hasText(option.getName()))
                        .sorted(Comparator.comparing(option -> option.getName().trim(), String.CASE_INSENSITIVE_ORDER))
                        .collect(Collectors.toList());
            }
        }

        StudentExtensionLocalizationService.Localizer localizer = localizationService.localizer(lang);

        return StudentExtensionMetadataDto.builder()
                .students(students)
                .extensionTypes(localizer.typeOptions())
                .statuses(localizer.statusOptions())
                .build();
    }
}
