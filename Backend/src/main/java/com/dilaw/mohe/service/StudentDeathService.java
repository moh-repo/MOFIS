package com.dilaw.mohe.service;

import com.dilaw.mohe.codes.DeathCaseStatus;

import com.dilaw.mohe.config.SecurityRequestUtils;
import com.dilaw.mohe.dto.StudentDeathCaseDto;
import com.dilaw.mohe.dto.StudentDeathCaseRequest;
import com.dilaw.mohe.dto.StudentDeathProcedureStepDto;
import com.dilaw.mohe.dto.StudentDeathProcedureStepUpdateRequest;
import com.dilaw.mohe.model.*;
import com.dilaw.mohe.repository.*;
import com.dilaw.mohe.util.StudentStatusTransitionValidator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Transactional(readOnly = true)
@Service
public class StudentDeathService {

    @Autowired
    private StudentDataService studentDataService;

    @Autowired
    private StudentDataRepository studentDataRepository;

    @Autowired
    private StudentDeathCaseRepository studentDeathCaseRepository;

    @Autowired
    private StudentDeathProcedureStepRepository studentDeathProcedureStepRepository;

    @Autowired
    private LuDeathProcedureRepository luDeathProcedureRepository;

    @Autowired
    private StudentStudyStatusRepository studentStudyStatusRepository;

    @Autowired
    private StudentCurrentLevelRepository studentCurrentLevelRepository;

    @Autowired
    private StudentProgressService studentProgressService;

    public Optional<StudentDeathCaseDto> getDeathCase(Integer studentId) {
        // Called purely as an access check — the returned student is discarded.
        studentDataService.getStudentData(studentId);
        return readDeathCase(studentId);
    }

    /**
     * The death case on a student's own record, read through the portal.
     *
     * <p>Same data; the access check above consults the UMS SecurityContext,
     * which a portal student does not have — see
     * {@link StudentDataService#getStudentDataForPortal}.
     */
    public Optional<StudentDeathCaseDto> getDeathCaseForPortal(Integer studentId) {
        return readDeathCase(studentId);
    }

    private Optional<StudentDeathCaseDto> readDeathCase(Integer studentId) {
        return studentDeathCaseRepository.findByStudentIdAndIsDeletedFalse(studentId)
                .map(this::toDto);
    }

    @Transactional
    public StudentDeathCaseDto createDeathCase(Integer studentId, StudentDeathCaseRequest request) {
        StudentDatum student = studentDataService.getStudentData(studentId);
        if (studentDeathCaseRepository.findByStudentIdAndIsDeletedFalse(studentId).isPresent()) {
            throw new IllegalStateException("A death case already exists for this student.");
        }
        if (request == null || request.getDateOfDeath() == null) {
            throw new IllegalArgumentException("Date of death is required.");
        }

        Integer deceasedStatusId = resolveDeceasedStatusId();
        StudentStatusTransitionValidator.validate(
                student.getStudentStudyStatusId(), deceasedStatusId, deceasedStatusId);

        LocalDateTime now = LocalDateTime.now();
        String actor = currentActor();

        StudentDeathCase deathCase = new StudentDeathCase();
        deathCase.setStudentId(studentId);
        deathCase.setDateOfDeath(request.getDateOfDeath());
        deathCase.setLevelAtDeathId(firstNonNull(request.getLevelAtDeathId(), student.getStudentCurrentLevelId()));
        deathCase.setStudyYearAtDeath(firstNonNull(request.getStudyYearAtDeath(), deriveStudyYear(studentId)));
        deathCase.setPlaceOfDeath(trimOrNull(request.getPlaceOfDeath()));
        deathCase.setCause(trimOrNull(request.getCause()));
        deathCase.setReportedDate(now);
        deathCase.setReportedBy(actor);
        deathCase.setCaseStatus(DeathCaseStatus.OPEN);
        deathCase.setNotes(trimOrNull(request.getNotes()));
        deathCase.setCreatedDate(now);
        deathCase.setCreatedBy(actor);
        StudentDeathCase savedCase = studentDeathCaseRepository.save(deathCase);

        seedProcedureSteps(savedCase.getDeathCaseId());

        student.setStudentStudyStatusId(deceasedStatusId);
        studentDataRepository.save(student);

        return toDto(studentDeathCaseRepository.findById(savedCase.getDeathCaseId()).orElseThrow());
    }

    @Transactional
    public StudentDeathCaseDto closeDeathCase(Integer studentId) {
        StudentDeathCase deathCase = studentDeathCaseRepository.findByStudentIdAndIsDeletedFalse(studentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Death case not found"));
        if (DeathCaseStatus.CLOSED.equalsIgnoreCase(deathCase.getCaseStatus())) {
            return toDto(deathCase);
        }
        List<StudentDeathProcedureStep> steps =
                studentDeathProcedureStepRepository.findByDeathCaseIdOrderByStepIdAsc(deathCase.getDeathCaseId());
        boolean allDone = steps.stream().allMatch(s -> Boolean.TRUE.equals(s.getIsCompleted()));
        if (!allDone) {
            throw new IllegalStateException("All procedure steps must be completed before closing the case.");
        }
        deathCase.setCaseStatus(DeathCaseStatus.CLOSED);
        deathCase.setModifiedDate(LocalDateTime.now());
        deathCase.setModifiedBy(currentActor());
        return toDto(studentDeathCaseRepository.save(deathCase));
    }

    @Transactional
    public StudentDeathProcedureStepDto updateProcedureStep(
            Integer studentId, Integer stepId, StudentDeathProcedureStepUpdateRequest request) {
        StudentDeathCase deathCase = studentDeathCaseRepository.findByStudentIdAndIsDeletedFalse(studentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Death case not found"));
        StudentDeathProcedureStep step = studentDeathProcedureStepRepository.findById(stepId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Procedure step not found"));
        if (!Objects.equals(step.getDeathCaseId(), deathCase.getDeathCaseId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Step does not belong to this student's death case.");
        }
        if (request == null || request.getIsCompleted() == null) {
            throw new IllegalArgumentException("isCompleted is required.");
        }
        step.setIsCompleted(request.getIsCompleted());
        if (Boolean.TRUE.equals(request.getIsCompleted())) {
            step.setCompletedDate(LocalDateTime.now());
            step.setCompletedBy(currentActor());
        } else {
            step.setCompletedDate(null);
            step.setCompletedBy(null);
        }
        if (request.getNotes() != null) {
            step.setNotes(trimOrNull(request.getNotes()));
        }
        StudentDeathProcedureStep saved = studentDeathProcedureStepRepository.save(step);
        return toStepDto(saved, procedureMap());
    }

    private void seedProcedureSteps(Integer deathCaseId) {
        List<LuDeathProcedure> procedures = luDeathProcedureRepository.findByIsDeletedFalseOrderBySortOrderAsc();
        for (LuDeathProcedure procedure : procedures) {
            StudentDeathProcedureStep step = new StudentDeathProcedureStep();
            step.setDeathCaseId(deathCaseId);
            step.setProcedureId(procedure.getId());
            step.setIsCompleted(false);
            studentDeathProcedureStepRepository.save(step);
        }
    }

    private StudentDeathCaseDto toDto(StudentDeathCase entity) {
        StudentDeathCaseDto dto = new StudentDeathCaseDto();
        dto.setDeathCaseId(entity.getDeathCaseId());
        dto.setStudentId(entity.getStudentId());
        dto.setDateOfDeath(entity.getDateOfDeath());
        dto.setLevelAtDeathId(entity.getLevelAtDeathId());
        dto.setStudyYearAtDeath(entity.getStudyYearAtDeath());
        dto.setPlaceOfDeath(entity.getPlaceOfDeath());
        dto.setCause(entity.getCause());
        dto.setReportedDate(entity.getReportedDate());
        dto.setReportedBy(entity.getReportedBy());
        dto.setCaseStatus(entity.getCaseStatus());
        dto.setNotes(entity.getNotes());

        if (entity.getLevelAtDeathId() != null) {
            studentCurrentLevelRepository.findById(entity.getLevelAtDeathId()).ifPresent(level -> {
                dto.setLevelAtDeathName(level.getStudentCurrentLevelNameEn() != null
                        ? level.getStudentCurrentLevelNameEn()
                        : level.getStudentCurrentLevelName());
            });
        }

        Map<Integer, LuDeathProcedure> procMap = procedureMap();
        List<StudentDeathProcedureStepDto> steps = studentDeathProcedureStepRepository
                .findByDeathCaseIdOrderByStepIdAsc(entity.getDeathCaseId()).stream()
                .map(step -> toStepDto(step, procMap))
                .sorted(Comparator.comparing(s -> s.getSortOrder() != null ? s.getSortOrder() : 0))
                .collect(Collectors.toList());
        dto.setSteps(steps);
        return dto;
    }

    private StudentDeathProcedureStepDto toStepDto(StudentDeathProcedureStep step, Map<Integer, LuDeathProcedure> procMap) {
        StudentDeathProcedureStepDto dto = new StudentDeathProcedureStepDto();
        dto.setStepId(step.getStepId());
        dto.setDeathCaseId(step.getDeathCaseId());
        dto.setProcedureId(step.getProcedureId());
        dto.setIsCompleted(step.getIsCompleted());
        dto.setCompletedDate(step.getCompletedDate());
        dto.setCompletedBy(step.getCompletedBy());
        dto.setNotes(step.getNotes());
        LuDeathProcedure proc = procMap.get(step.getProcedureId());
        if (proc != null) {
            dto.setProcedureCode(proc.getCode());
            dto.setProcedureNameAr(proc.getNameAr());
            dto.setProcedureNameEn(proc.getNameEn());
            dto.setSortOrder(proc.getSortOrder());
        }
        return dto;
    }

    private Map<Integer, LuDeathProcedure> procedureMap() {
        return luDeathProcedureRepository.findByIsDeletedFalseOrderBySortOrderAsc().stream()
                .collect(Collectors.toMap(LuDeathProcedure::getId, p -> p, (a, b) -> a));
    }

    private Integer resolveDeceasedStatusId() {
        return studentStudyStatusRepository.findFirstByStudentStudyStatusNameEnIgnoreCase("Deceased")
                .map(StudentStudyStatus::getId)
                .orElseThrow(() -> new IllegalStateException(
                        "DECEASED study status not found. Run migration V005__add_student_family_and_death.sql."));
    }

    private Integer deriveStudyYear(Integer studentId) {
        try {
            return studentProgressService.getProgress(studentId).getCurrentYearOrder();
        } catch (Exception e) {
            return null;
        }
    }

    private static Integer firstNonNull(Integer preferred, Integer fallback) {
        if (preferred != null && preferred > 0) {
            return preferred;
        }
        if (fallback != null && fallback > 0) {
            return fallback;
        }
        return preferred != null ? preferred : fallback;
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
