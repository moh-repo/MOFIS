package com.dilaw.mohe.service;

import com.dilaw.mohe.dto.LookupOptionDto;
import com.dilaw.mohe.dto.StudentExtensionDto;
import com.dilaw.mohe.dto.StudentExtensionRequest;
import com.dilaw.mohe.model.ExtensionType;
import com.dilaw.mohe.model.StudentExtension;
import com.dilaw.mohe.repository.ExtensionTypeRepository;
import com.dilaw.mohe.repository.StudentDataRepository;
import com.dilaw.mohe.repository.StudentExtensionRepository;
import com.dilaw.mohe.repository.spec.StudentExtensionSpec;
import com.dilaw.mohe.repository.spec.StudentIdCountrySpec;
import com.dilaw.mohe.ums.service.CountryScopeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import jakarta.persistence.EntityNotFoundException;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Transactional(readOnly = true)
@Service
@RequiredArgsConstructor
public class StudentExtensionService {

    private final StudentExtensionRepository extensionRepository;
    private final StudentDataRepository studentDataRepository;
    private final ExtensionTypeRepository extensionTypeRepository;
    private final CountryScopeService countryScopeService;
    private final StudentExtensionLocalizationService localizationService;
    private final StudentAccessGuard studentAccessGuard;

    public StudentExtensionDto getById(Integer extensionId) {
        return getById(extensionId, null);
    }

    public StudentExtensionDto getById(Integer extensionId, String lang) {
        StudentExtension extension = extensionRepository.findById(extensionId)
                .orElseThrow(() -> new EntityNotFoundException("Student extension not found: " + extensionId));
        validateCountryAccess(extension.getStudentId());
        return mapToDto(extension, resolveStudentName(extension.getStudentId()), localizationService.localizer(lang));
    }

    @Transactional
    public StudentExtensionDto create(StudentExtensionRequest request) {
        validateRequest(request);
        validateCountryAccess(request.getStudentId());
        enforceExtensionLimit(request.getStudentId(), request.getExtensionType());
        StudentExtension entity = new StudentExtension();
        applyRequest(entity, request, true);
        StudentExtension saved = extensionRepository.save(entity);
        return mapToDto(saved, resolveStudentName(saved.getStudentId()), localizationService.localizer(null));
    }

    private void enforceExtensionLimit(Integer studentId, String extensionTypeValue) {
        if (studentId == null || extensionTypeValue == null || extensionTypeValue.isBlank()) return;
        String value = extensionTypeValue.trim();
        // The client submits the type name (not the code), so resolve by code OR either name.
        extensionTypeRepository.findByCodeOrName(value).stream().findFirst().ifPresent(et -> {
            Integer max = et.getMaxExtensions();
            if (max == null) return;
            long existing = extensionRepository.countByStudentIdAndExtensionType(studentId, value);
            if (existing >= max) {
                throw new IllegalStateException(
                    "Student has reached the maximum of " + max + " extension(s) for type '" + value + "'.");
            }
        });
    }

    @Transactional
    public StudentExtensionDto update(Integer extensionId, StudentExtensionRequest request) {
        validateRequest(request);
        StudentExtension entity = extensionRepository.findById(extensionId)
                .orElseThrow(() -> new EntityNotFoundException("Student extension not found: " + extensionId));
        validateCountryAccess(entity.getStudentId());
        // applyRequest copies the student from the request: the new one is checked too (N14).
        validateCountryAccess(request.getStudentId());

        applyRequest(entity, request, false);
        StudentExtension saved = extensionRepository.save(entity);
        return mapToDto(saved, resolveStudentName(saved.getStudentId()), localizationService.localizer(null));
    }

    @Transactional
    public void delete(Integer extensionId) {
        StudentExtension entity = extensionRepository.findById(extensionId)
                .orElseThrow(() -> new EntityNotFoundException("Student extension not found: " + extensionId));
        validateCountryAccess(entity.getStudentId());
        extensionRepository.delete(entity);
    }

    public List<StudentExtensionDto> search(Integer studentId, Integer filterStudentId, String extensionType, String status, String passport) {
        return search(studentId, filterStudentId, extensionType, status, passport, null);
    }

    public List<StudentExtensionDto> search(Integer studentId, Integer filterStudentId, String extensionType, String status, String passport, String lang) {
        String normalizedType = normalize(extensionType);
        String normalizedStatus = normalize(status);
        String normalizedPassport = normalize(passport);

        org.springframework.data.jpa.domain.Specification<StudentExtension> spec =
                StudentExtensionSpec.filter(studentId, filterStudentId, normalizedType, normalizedStatus, normalizedPassport)
                .and(StudentIdCountrySpec.countryScope(countryScopeService.getScope()));

        List<StudentExtension> extensions = extensionRepository.findAll(spec);

        if (extensions.isEmpty() && studentId == null && filterStudentId == null && normalizedType == null && normalizedStatus == null && normalizedPassport == null) {
            return List.of();
        }

        Map<Integer, String> studentNames = resolveStudentNames(extensions.stream()
                .map(StudentExtension::getStudentId)
                .filter(id -> id != null && id > 0)
                .collect(Collectors.toSet()));

        StudentExtensionLocalizationService.Localizer localizer = localizationService.localizer(lang);

        return extensions.stream()
                .map(extension -> mapToDto(extension, studentNames.get(extension.getStudentId()), localizer))
                .collect(Collectors.toList());
    }

    private void applyRequest(StudentExtension entity, StudentExtensionRequest request, boolean isCreate) {
        entity.setStudentId(request.getStudentId());
        entity.setExtensionType(trimToNull(request.getExtensionType()));
        entity.setExtensionQuarters(request.getExtensionQuarters());
        entity.setExtensionStartDate(request.getExtensionStartDate());
        entity.setExtensionEndDate(request.getExtensionEndDate());
        entity.setExtensionReason(trimToNull(request.getExtensionReason()));
        entity.setExtensionReasonDetails(trimToNull(request.getExtensionReasonDetails()));
        entity.setExtensionDecisionNo(trimToNull(request.getExtensionDecisionNo()));
        entity.setExtensionDecisionDate(request.getExtensionDecisionDate());
        entity.setApprovedBy(trimToNull(request.getApprovedBy()));
        entity.setExtensionStatus(trimToNull(request.getExtensionStatus()));
        entity.setNotes(trimToNull(request.getNotes()));

        LocalDateTime now = LocalDateTime.now();
        if (isCreate) {
            entity.setCreatedDate(now);
            entity.setCreatedBy(trimToNull(request.getCreatedBy()));
        }

        entity.setModifiedDate(now);
        entity.setModifiedBy(trimToNull(StringUtils.hasText(request.getModifiedBy()) ? request.getModifiedBy() : request.getCreatedBy()));
    }

    private Map<Integer, String> resolveStudentNames(Collection<Integer> studentIds) {
        if (CollectionUtils.isEmpty(studentIds)) {
            return Map.of();
        }

        List<LookupOptionDto> options = studentDataRepository.findStudentOptionsByIds(studentIds);
        return options.stream()
                .filter(option -> option.getId() != null)
                .collect(Collectors.toMap(
                        LookupOptionDto::getId,
                        LookupOptionDto::getName,
                        (existing, ignored) -> existing
                ));
    }

    private String resolveStudentName(Integer studentId) {
        if (studentId == null) {
            return null;
        }
        Map<Integer, String> names = resolveStudentNames(List.of(studentId));
        return names.get(studentId);
    }

    private StudentExtensionDto mapToDto(StudentExtension entity, String studentName,
                                         StudentExtensionLocalizationService.Localizer localizer) {
        return StudentExtensionDto.builder()
                .extensionId(entity.getExtensionId())
                .studentId(entity.getStudentId())
                .studentName(studentName)
                .extensionType(entity.getExtensionType())
                .extensionTypeLabel(localizer.typeLabel(entity.getExtensionType()))
                .extensionQuarters(entity.getExtensionQuarters())
                .extensionStartDate(entity.getExtensionStartDate())
                .extensionEndDate(entity.getExtensionEndDate())
                .extensionReason(entity.getExtensionReason())
                .extensionReasonDetails(entity.getExtensionReasonDetails())
                .extensionDecisionNo(entity.getExtensionDecisionNo())
                .extensionDecisionDate(entity.getExtensionDecisionDate())
                .approvedBy(entity.getApprovedBy())
                .extensionStatus(entity.getExtensionStatus())
                .extensionStatusLabel(localizer.statusLabel(entity.getExtensionStatus()))
                .createdDate(entity.getCreatedDate())
                .createdBy(entity.getCreatedBy())
                .modifiedDate(entity.getModifiedDate())
                .modifiedBy(entity.getModifiedBy())
                .notes(entity.getNotes())
                .build();
    }

    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim().toLowerCase() : null;
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    /**
     * The caller may reach the extension's student (live QA N14, 26 Sep 2026): 404 for a
     * student that is missing or hidden by row-level security, 403 out of scope. The
     * country used to be read with {@code .orElse(null)} and a null let through, which is
     * what a hidden student returns.
     */
    private void validateCountryAccess(Integer studentId) {
        studentAccessGuard.requireStudent(studentId);
    }

    private void validateRequest(StudentExtensionRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Student extension request is required");
        }
        if (request.getStudentId() == null) {
            throw new IllegalArgumentException("Student ID is required");
        }
        if (!StringUtils.hasText(request.getExtensionType())) {
            throw new IllegalArgumentException("Extension type is required");
        }
        if (!StringUtils.hasText(request.getExtensionStatus())) {
            throw new IllegalArgumentException("Extension status is required");
        }
        if (request.getExtensionQuarters() != null && request.getExtensionQuarters() < 0) {
            throw new IllegalArgumentException("Extension quarters cannot be negative");
        }
    }
}


