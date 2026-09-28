package com.dilaw.mohe.service;

import com.dilaw.mohe.config.SecurityRequestUtils;
import com.dilaw.mohe.dto.StudentParentDto;
import com.dilaw.mohe.dto.StudentRelativeDto;
import com.dilaw.mohe.dto.StudentRelativeSearchDto;
import com.dilaw.mohe.model.*;
import com.dilaw.mohe.repository.*;
import com.dilaw.mohe.repository.spec.StudentDataSpec;
import com.dilaw.mohe.ums.service.CountryScopeService;
import com.dilaw.mohe.ums.service.CountryScopeService.CountryScope;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Transactional(readOnly = true)
@Service
public class StudentFamilyService {

    @Autowired
    private StudentDataService studentDataService;

    @Autowired
    private StudentDataRepository studentDataRepository;

    @Autowired
    private StudentParentRepository studentParentRepository;

    @Autowired
    private StudentRelativeRepository studentRelativeRepository;

    @Autowired
    private LuFamilyRelationshipRepository luFamilyRelationshipRepository;

    @Autowired
    private StudentStudyStatusRepository studentStudyStatusRepository;

    @Autowired
    private StudentCurrentLevelRepository studentCurrentLevelRepository;

    @Autowired
    private CountryScopeService countryScopeService;

    public List<StudentParentDto> getParents(Integer studentId) {
        assertStudentAccess(studentId);
        return readParents(studentId);
    }

    /**
     * A student's own parents, read through the portal.
     *
     * <p>No {@code assertStudentAccess}: that consults the UMS SecurityContext,
     * which a portal student does not have — see
     * {@link StudentDataService#getStudentDataForPortal}. The caller is the
     * student whose family this is, established from the portal session token.
     */
    public List<StudentParentDto> getParentsForPortal(Integer studentId) {
        return readParents(studentId);
    }

    private List<StudentParentDto> readParents(Integer studentId) {
        Map<Integer, LuFamilyRelationship> relMap = relationshipMap();
        return studentParentRepository.findByStudentIdAndIsDeletedFalseOrderByParentIdAsc(studentId).stream()
                .map(p -> toParentDto(p, relMap))
                .collect(Collectors.toList());
    }

    @Transactional
    public List<StudentParentDto> replaceParents(Integer studentId, List<StudentParentDto> parents) {
        assertStudentAccess(studentId);
        List<StudentParent> existing = studentParentRepository.findByStudentIdAndIsDeletedFalseOrderByParentIdAsc(studentId);
        LocalDateTime now = LocalDateTime.now();
        String actor = currentActor();
        for (StudentParent row : existing) {
            row.setIsDeleted(true);
            row.setModifiedDate(now);
            row.setModifiedBy(actor);
        }
        studentParentRepository.saveAll(existing);

        if (parents == null || parents.isEmpty()) {
            return List.of();
        }

        List<StudentParent> saved = new ArrayList<>();
        for (StudentParentDto dto : parents) {
            validateParentDto(dto);
            StudentParent entity = new StudentParent();
            entity.setStudentId(studentId);
            entity.setRelationshipId(dto.getRelationshipId());
            entity.setParentName(dto.getParentName().trim());
            entity.setNationalId(trimOrNull(dto.getNationalId()));
            entity.setOccupation(trimOrNull(dto.getOccupation()));
            entity.setPhone(trimOrNull(dto.getPhone()));
            entity.setAddress(trimOrNull(dto.getAddress()));
            entity.setIsAlive(dto.getIsAlive() == null || dto.getIsAlive());
            entity.setNotes(trimOrNull(dto.getNotes()));
            entity.setCreatedDate(now);
            entity.setCreatedBy(actor);
            saved.add(entity);
        }
        studentParentRepository.saveAll(saved);
        return getParents(studentId);
    }

    @Transactional
    public StudentParentDto createParent(Integer studentId, StudentParentDto dto) {
        assertStudentAccess(studentId);
        validateParentDto(dto);
        StudentParent entity = new StudentParent();
        entity.setStudentId(studentId);
        applyParentFields(entity, dto);
        entity.setCreatedDate(LocalDateTime.now());
        entity.setCreatedBy(currentActor());
        StudentParent saved = studentParentRepository.save(entity);
        return toParentDto(saved, relationshipMap());
    }

    @Transactional
    public StudentParentDto updateParent(Integer studentId, Integer parentId, StudentParentDto dto) {
        assertStudentAccess(studentId);
        validateParentDto(dto);
        StudentParent entity = studentParentRepository.findByParentIdAndStudentIdAndIsDeletedFalse(parentId, studentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Parent not found"));
        applyParentFields(entity, dto);
        entity.setModifiedDate(LocalDateTime.now());
        entity.setModifiedBy(currentActor());
        return toParentDto(studentParentRepository.save(entity), relationshipMap());
    }

    @Transactional
    public void deleteParent(Integer studentId, Integer parentId) {
        assertStudentAccess(studentId);
        StudentParent entity = studentParentRepository.findByParentIdAndStudentIdAndIsDeletedFalse(parentId, studentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Parent not found"));
        entity.setIsDeleted(true);
        entity.setModifiedDate(LocalDateTime.now());
        entity.setModifiedBy(currentActor());
        studentParentRepository.save(entity);
    }

    public List<StudentRelativeDto> getRelatives(Integer studentId) {
        assertStudentAccess(studentId);
        return readRelatives(studentId);
    }

    /** A student's own relatives, read through the portal. See {@link #getParentsForPortal}. */
    public List<StudentRelativeDto> getRelativesForPortal(Integer studentId) {
        return readRelatives(studentId);
    }

    private List<StudentRelativeDto> readRelatives(Integer studentId) {
        return studentRelativeRepository.findByStudentIdAndIsDeletedFalseOrderByRelativeIdAsc(studentId).stream()
                .map(this::toRelativeDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public List<StudentRelativeDto> replaceRelatives(Integer studentId, List<StudentRelativeDto> relatives) {
        assertStudentAccess(studentId);
        List<StudentRelative> existing = studentRelativeRepository.findByStudentIdAndIsDeletedFalseOrderByRelativeIdAsc(studentId);
        LocalDateTime now = LocalDateTime.now();
        String actor = currentActor();
        for (StudentRelative row : existing) {
            row.setIsDeleted(true);
            row.setModifiedDate(now);
            row.setModifiedBy(actor);
        }
        studentRelativeRepository.saveAll(existing);

        if (relatives == null || relatives.isEmpty()) {
            return List.of();
        }

        Set<String> seen = new HashSet<>();
        for (StudentRelativeDto dto : relatives) {
            validateRelativeDto(studentId, dto);
            String key = dto.getRelatedStudentId() + ":" + dto.getRelationshipId();
            if (!seen.add(key)) {
                throw new IllegalArgumentException("Duplicate relative link in request.");
            }
            StudentRelative entity = new StudentRelative();
            entity.setStudentId(studentId);
            entity.setRelatedStudentId(dto.getRelatedStudentId());
            entity.setRelationshipId(dto.getRelationshipId());
            entity.setNotes(trimOrNull(dto.getNotes()));
            entity.setCreatedDate(now);
            entity.setCreatedBy(actor);
            studentRelativeRepository.save(entity);
        }
        return getRelatives(studentId);
    }

    @Transactional
    public StudentRelativeDto createRelative(Integer studentId, StudentRelativeDto dto) {
        assertStudentAccess(studentId);
        validateRelativeDto(studentId, dto);
        if (studentRelativeRepository.existsByStudentIdAndRelatedStudentIdAndRelationshipIdAndIsDeletedFalse(
                studentId, dto.getRelatedStudentId(), dto.getRelationshipId())) {
            throw new IllegalStateException("This relative link already exists.");
        }
        StudentRelative entity = new StudentRelative();
        entity.setStudentId(studentId);
        entity.setRelatedStudentId(dto.getRelatedStudentId());
        entity.setRelationshipId(dto.getRelationshipId());
        entity.setNotes(trimOrNull(dto.getNotes()));
        entity.setCreatedDate(LocalDateTime.now());
        entity.setCreatedBy(currentActor());
        return toRelativeDto(studentRelativeRepository.save(entity));
    }

    @Transactional
    public StudentRelativeDto updateRelative(Integer studentId, Integer relativeId, StudentRelativeDto dto) {
        assertStudentAccess(studentId);
        validateRelativeDto(studentId, dto);
        StudentRelative entity = studentRelativeRepository.findByRelativeIdAndStudentIdAndIsDeletedFalse(relativeId, studentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Relative not found"));
        entity.setRelatedStudentId(dto.getRelatedStudentId());
        entity.setRelationshipId(dto.getRelationshipId());
        entity.setNotes(trimOrNull(dto.getNotes()));
        entity.setModifiedDate(LocalDateTime.now());
        entity.setModifiedBy(currentActor());
        return toRelativeDto(studentRelativeRepository.save(entity));
    }

    @Transactional
    public void deleteRelative(Integer studentId, Integer relativeId) {
        assertStudentAccess(studentId);
        StudentRelative entity = studentRelativeRepository.findByRelativeIdAndStudentIdAndIsDeletedFalse(relativeId, studentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Relative not found"));
        entity.setIsDeleted(true);
        entity.setModifiedDate(LocalDateTime.now());
        entity.setModifiedBy(currentActor());
        studentRelativeRepository.save(entity);
    }

    public List<StudentRelativeSearchDto> searchRelatives(String q, Integer excludeStudentId) {
        if (q == null || q.trim().length() < 2) {
            return List.of();
        }
        CountryScope scope = countryScopeService.getScope();
        Specification<StudentDatum> spec = Specification
                .where(StudentDataSpec.countryScope(scope))
                .and(StudentDataSpec.visibilityFilter(scope))
                .and(StudentDataSpec.searchTerm(q.trim()));
        if (excludeStudentId != null) {
            spec = spec.and((root, query, cb) -> cb.notEqual(root.get("studentId"), excludeStudentId));
        }
        List<StudentDatum> rows = studentDataRepository.findAll(spec, PageRequest.of(0, 20)).getContent();
        Map<Integer, String> statusNames = studyStatusNameMap();
        Map<Integer, String> levelNames = levelNameMap();
        return rows.stream().map(s -> {
            StudentRelativeSearchDto dto = new StudentRelativeSearchDto();
            dto.setStudentId(s.getStudentId());
            dto.setStudentName(s.getStudentName());
            dto.setPassportNumber(s.getStudentPassportNumber());
            dto.setStudyStatusId(s.getStudentStudyStatusId());
            dto.setStudyStatusName(statusNames.get(s.getStudentStudyStatusId()));
            dto.setCurrentLevelId(s.getStudentCurrentLevelId());
            dto.setCurrentLevelName(levelNames.get(s.getStudentCurrentLevelId()));
            return dto;
        }).collect(Collectors.toList());
    }

    /**
     * Checks the parents and relatives typed into the Add Student wizard when the
     * request is submitted. They are written only once the student is approved (the
     * student has no id before that), so a bad row must be refused now, while the
     * user can still fix it, not at the final sign-off.
     */
    public void validateForOnboarding(List<StudentParentDto> parents, List<StudentRelativeDto> relatives) {
        if (parents != null) {
            parents.forEach(this::validateParentDto);
        }
        if (relatives != null) {
            Set<String> seen = new HashSet<>();
            for (StudentRelativeDto dto : relatives) {
                validateRelativeDto(null, dto);
                if (!seen.add(dto.getRelatedStudentId() + ":" + dto.getRelationshipId())) {
                    throw new IllegalArgumentException("Duplicate relative link in request.");
                }
            }
        }
    }

    private void assertStudentAccess(Integer studentId) {
        studentDataService.getStudentData(studentId);
    }

    private void assertRelationshipExists(Integer relationshipId) {
        if (!luFamilyRelationshipRepository.existsById(relationshipId)) {
            throw new IllegalArgumentException("Invalid relationship ID: " + relationshipId);
        }
    }

    private void validateParentDto(StudentParentDto dto) {
        if (dto == null) {
            throw new IllegalArgumentException("Parent payload required.");
        }
        if (dto.getRelationshipId() == null || dto.getRelationshipId() <= 0) {
            throw new IllegalArgumentException("Relationship is required.");
        }
        assertRelationshipExists(dto.getRelationshipId());
        if (dto.getParentName() == null || dto.getParentName().isBlank()) {
            throw new IllegalArgumentException("Parent name is required.");
        }
    }

    private void validateRelativeDto(Integer studentId, StudentRelativeDto dto) {
        if (dto == null) {
            throw new IllegalArgumentException("Relative payload required.");
        }
        if (dto.getRelationshipId() == null || dto.getRelationshipId() <= 0) {
            throw new IllegalArgumentException("Relationship is required.");
        }
        assertRelationshipExists(dto.getRelationshipId());
        if (dto.getRelatedStudentId() == null || dto.getRelatedStudentId() <= 0) {
            throw new IllegalArgumentException("Related student is required.");
        }
        if (Objects.equals(studentId, dto.getRelatedStudentId())) {
            throw new IllegalArgumentException("A student cannot be linked as their own relative.");
        }
        studentDataService.getStudentData(dto.getRelatedStudentId());
    }

    private void applyParentFields(StudentParent entity, StudentParentDto dto) {
        entity.setRelationshipId(dto.getRelationshipId());
        entity.setParentName(dto.getParentName().trim());
        entity.setNationalId(trimOrNull(dto.getNationalId()));
        entity.setOccupation(trimOrNull(dto.getOccupation()));
        entity.setPhone(trimOrNull(dto.getPhone()));
        entity.setAddress(trimOrNull(dto.getAddress()));
        entity.setIsAlive(dto.getIsAlive() == null || dto.getIsAlive());
        entity.setNotes(trimOrNull(dto.getNotes()));
    }

    private StudentParentDto toParentDto(StudentParent entity, Map<Integer, LuFamilyRelationship> relMap) {
        StudentParentDto dto = new StudentParentDto();
        dto.setParentId(entity.getParentId());
        dto.setStudentId(entity.getStudentId());
        dto.setRelationshipId(entity.getRelationshipId());
        LuFamilyRelationship rel = relMap.get(entity.getRelationshipId());
        if (rel != null) {
            dto.setRelationshipName(rel.getRelName());
        }
        dto.setParentName(entity.getParentName());
        dto.setNationalId(entity.getNationalId());
        dto.setOccupation(entity.getOccupation());
        dto.setPhone(entity.getPhone());
        dto.setAddress(entity.getAddress());
        dto.setIsAlive(entity.getIsAlive());
        dto.setNotes(entity.getNotes());
        return dto;
    }

    private StudentRelativeDto toRelativeDto(StudentRelative entity) {
        StudentRelativeDto dto = new StudentRelativeDto();
        dto.setRelativeId(entity.getRelativeId());
        dto.setStudentId(entity.getStudentId());
        dto.setRelatedStudentId(entity.getRelatedStudentId());
        dto.setRelationshipId(entity.getRelationshipId());
        dto.setNotes(entity.getNotes());

        luFamilyRelationshipRepository.findById(entity.getRelationshipId()).ifPresent(rel -> {
            dto.setRelationshipName(rel.getRelName());
        });

        try {
            StudentDatum related = studentDataService.getStudentData(entity.getRelatedStudentId());
            dto.setRelatedStudentName(related.getStudentName());
            dto.setRelatedStudentStatusId(related.getStudentStudyStatusId());
            dto.setRelatedStudentCurrentLevelId(related.getStudentCurrentLevelId());
            studyStatusNameMap().entrySet().stream()
                    .filter(e -> Objects.equals(e.getKey(), related.getStudentStudyStatusId()))
                    .findFirst()
                    .ifPresent(e -> dto.setRelatedStudentStatusName(e.getValue()));
            levelNameMap().entrySet().stream()
                    .filter(e -> Objects.equals(e.getKey(), related.getStudentCurrentLevelId()))
                    .findFirst()
                    .ifPresent(e -> dto.setRelatedStudentCurrentLevelName(e.getValue()));
        } catch (ResponseStatusException ignored) {
        }
        return dto;
    }

    private Map<Integer, LuFamilyRelationship> relationshipMap() {
        return luFamilyRelationshipRepository.findAllByOrderBySortOrderAsc().stream()
                .collect(Collectors.toMap(LuFamilyRelationship::getId, r -> r, (a, b) -> a));
    }

    private Map<Integer, String> studyStatusNameMap() {
        return studentStudyStatusRepository.findAll().stream()
                .collect(Collectors.toMap(StudentStudyStatus::getId,
                        s -> s.getStudentStudyStatusNameEn() != null ? s.getStudentStudyStatusNameEn() : s.getStudentStudyStatusName(),
                        (a, b) -> a));
    }

    private Map<Integer, String> levelNameMap() {
        return studentCurrentLevelRepository.findAll().stream()
                .collect(Collectors.toMap(StudentCurrentLevel::getId,
                        l -> l.getStudentCurrentLevelNameEn() != null ? l.getStudentCurrentLevelNameEn() : l.getStudentCurrentLevelName(),
                        (a, b) -> a));
    }

    private static String currentActor() {
        return String.valueOf(SecurityRequestUtils.currentUserIdOrDefault(0L));
    }

    private static String trimOrNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
