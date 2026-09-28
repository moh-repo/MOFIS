package com.dilaw.mohe.service;

import com.dilaw.mohe.approval.model.ApprovalStatus;

import com.dilaw.mohe.approval.model.RequestType;
import com.dilaw.mohe.approval.service.ApprovalIntegrationService;
import com.dilaw.mohe.config.SecurityRequestUtils;
import com.dilaw.mohe.dto.StudentQuickEditDto;
import com.dilaw.mohe.model.StudentDatum;
import com.dilaw.mohe.model.StudentFinance;
import com.dilaw.mohe.repository.StudentDataRepository;
import com.dilaw.mohe.repository.StudentFinanceRepository;
import com.dilaw.mohe.repository.spec.StudentDataSpec;
import com.dilaw.mohe.ums.service.CountryScopeService;
import com.dilaw.mohe.util.PhoneNumbers;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Transactional(readOnly = true)
@Service
public class StudentQuickEditService {

    @Autowired
    private StudentAccessGuard studentAccessGuard;

    @Autowired
    private StudentDataRepository studentDataRepository;

    @Autowired
    private StudentFinanceRepository studentFinanceRepository;

    @Autowired
    private CountryScopeService countryScopeService;

    @Autowired
    private ApprovalIntegrationService approvalIntegrationService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private com.dilaw.mohe.security.field.FieldAccessService fieldAccess;

    @Autowired
    private StudentReferenceValidator studentReferenceValidator;

    public Page<StudentQuickEditDto> getAllCombinedData(Pageable pageable) {
        Page<StudentDatum> studentDataPage = studentDataRepository.findAll(
                StudentDataSpec.countryScope(countryScopeService.getScope()), pageable);
        List<StudentQuickEditDto> dtoList = new ArrayList<>();

        for (StudentDatum studentData : studentDataPage.getContent()) {
            StudentQuickEditDto dto = mapToDto(studentData);
            dtoList.add(dto);
        }

        return new PageImpl<>(dtoList, pageable, studentDataPage.getTotalElements());
    }

    public List<StudentQuickEditDto> getAllCombinedData() {
        List<StudentDatum> studentDataList = studentDataRepository.findAll(
                StudentDataSpec.countryScope(countryScopeService.getScope()));
        List<StudentQuickEditDto> dtoList = new ArrayList<>();

        for (StudentDatum studentData : studentDataList) {
            StudentQuickEditDto dto = mapToDto(studentData);
            dtoList.add(dto);
        }

        return dtoList;
    }

    public StudentQuickEditDto getById(Integer studentId) {
        // The student record's own rule (N14): canAccessCountry(null) let a student with no
        // study country through to a country account.
        StudentDatum studentData = studentAccessGuard.requireStudent(studentId);
        return mapToDto(studentData);
    }

    /**
     * Quick edits are not applied directly — they are routed through the approval workflow,
     * exactly like the full student edit. STUDENT_DATA and STUDENT_FINANCE changes each raise
     * their own UPDATE approval request; the live rows are only written once approved.
     *
     * The managed entities are never mutated here (detached clones carry the proposed values),
     * so nothing is persisted outside the approval request itself.
     */
    @Transactional
    public Map<String, Object> update(Integer studentId, StudentQuickEditDto dto) {
        // The student record's own rule (N14): canAccessCountry(null) let a student with no
        // study country through to a country account.
        StudentDatum studentData = studentAccessGuard.requireStudent(studentId);

        Long userId = SecurityRequestUtils.requireUserId();

        // R-18 (D-9 a): a masked field the caller may not EDIT is refused before anything is raised.
        fieldAccess.assertMayEdit(StudentDatum.class, studentData, dto, false);
        // Only numbers this edit changes are checked; see PhoneNumbers.
        PhoneNumbers.requireIfChanged("studentCellphone",
                studentData.getStudentCellphone(), dto.getStudentCellphone(), true);
        PhoneNumbers.requireIfChanged("studentHomeNumber",
                studentData.getStudentHomeNumber(), dto.getStudentHomeNumber(), false);
        PhoneNumbers.requireIfChanged("studentEmergencyContact",
                studentData.getStudentEmergencyContact(), dto.getStudentEmergencyContact(), false);

        // ── STUDENT_DATA: detached original snapshot + proposed with edits applied ──
        StudentDatum originalData = cloneStudentDatum(studentData);
        StudentDatum proposedData = cloneStudentDatum(studentData);
        updateStudentDataFromDto(proposedData, dto);
        studentReferenceValidator.requireExistingReferences(proposedData);
        boolean dataChanged = !objectMapper.valueToTree(originalData)
                .equals(objectMapper.valueToTree(proposedData));

        // ── STUDENT_FINANCE (when a finance row exists) ──
        StudentFinance studentFinance = studentFinanceRepository.findById(studentId).orElse(null);
        StudentFinance originalFinance = null;
        StudentFinance proposedFinance = null;
        boolean financeChanged = false;
        if (studentFinance != null) {
            originalFinance = cloneStudentFinance(studentFinance);
            proposedFinance = cloneStudentFinance(studentFinance);
            updateStudentFinanceFromDto(proposedFinance, dto);
            financeChanged = !objectMapper.valueToTree(originalFinance)
                    .equals(objectMapper.valueToTree(proposedFinance));
        }

        Map<String, Object> result = new LinkedHashMap<>();
        if (!dataChanged && !financeChanged) {
            result.put("status", "NO_CHANGES");
            result.put("message", "No changes detected. Nothing was submitted for approval.");
            return result;
        }

        if (dataChanged) {
            approvalIntegrationService.createStudentDataRequest(
                    RequestType.UPDATE, proposedData, originalData, userId);
        }
        if (financeChanged) {
            approvalIntegrationService.createStudentFinanceRequest(
                    RequestType.UPDATE, proposedFinance, originalFinance, userId,
                    studentData.getStudyCountryIn(), null);
        }

        result.put("status", ApprovalStatus.PENDING.name());
        result.put("message", "Update request submitted for approval.");
        result.put("dataChanged", dataChanged);
        result.put("financeChanged", financeChanged);
        return result;
    }

    private StudentDatum cloneStudentDatum(StudentDatum src) {
        try {
            return objectMapper.treeToValue(objectMapper.valueToTree(src), StudentDatum.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to snapshot student data: " + e.getMessage(), e);
        }
    }

    private StudentFinance cloneStudentFinance(StudentFinance src) {
        try {
            return objectMapper.treeToValue(objectMapper.valueToTree(src), StudentFinance.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to snapshot student finance: " + e.getMessage(), e);
        }
    }

    private StudentQuickEditDto mapToDto(StudentDatum studentData) {
        StudentQuickEditDto dto = new StudentQuickEditDto();
        
        // Map from STUDENT_DATA
        dto.setStudentId(studentData.getStudentId());
        dto.setStudentName(studentData.getStudentName());
        dto.setStudentDelegationDecisionYear(studentData.getStudentDelegationDecisionYear());
        dto.setStudentDegreeId(studentData.getStudentDegreeId());
        dto.setStudentMajorId(studentData.getStudentMajorId());
        dto.setStudentDelegateDecisionNo(studentData.getStudentDelegateDecisionNo());
        dto.setStudentStudyNoOfYears(studentData.getStudentStudyNoOfYears());
        dto.setStudentCurrentLevelId(studentData.getStudentCurrentLevelId());
        dto.setStudentFirstReceiptOfDue(studentData.getStudentFirstReceiptOfDue());
        dto.setStudentAmount(studentData.getStudentAmount());
        dto.setStudentNote1(studentData.getStudentNote1());
        dto.setStudentNote2(studentData.getStudentNote2());
        dto.setStudentStudyStatusId(studentData.getStudentStudyStatusId());
        dto.setStudentAddedQuarter(studentData.getStudentAddedQuarter());
        dto.setStudentGender(studentData.getStudentGender());
        dto.setStudentPassportNumber(studentData.getStudentPassportNumber());
        dto.setStudyUniversityFeeId(studentData.getStudyUniversityFeeId());
        dto.setStudyUniversityFeeStatusId(studentData.getStudyUniversityFeeStatusId());
        dto.setStudentLastQuarter(studentData.getStudentLastQuarter());
        dto.setCurrentQuarter(studentData.getCurrentQuarter());
        dto.setStudentAgencyTypeId(studentData.getStudentAgencyTypeId());
        dto.setStudentMinistryTypeId(studentData.getStudentMinistryTypeId());
        dto.setStudentStudyTypeId(studentData.getStudentStudyTypeId());
        dto.setStudentUniversityTypeId(studentData.getStudentUniversityTypeId());
        dto.setStudentBirthdatePlace(studentData.getStudentBirthdatePlace());
        dto.setStudentPassportIssueCountryId(studentData.getStudentPassportIssueCountryId());
        dto.setStudentEmail(studentData.getStudentEmail());
        dto.setStudentCellphone(studentData.getStudentCellphone());
        dto.setStudentHomeNumber(studentData.getStudentHomeNumber());
        dto.setStudentEmergencyContact(studentData.getStudentEmergencyContact());

        // Map from STUDENT_FINANCE if exists (using same ID as STUDENT_DATA)
        Optional<StudentFinance> studentFinanceOpt = studentFinanceRepository.findById(studentData.getStudentId());
        if (studentFinanceOpt.isPresent()) {
            StudentFinance studentFinance = studentFinanceOpt.get();
            dto.setStudentFinId(studentFinance.getStudentFinId());
            dto.setStudentFinName(studentFinance.getStudentFinName());
            dto.setStudentFinRecievedYear(studentFinance.getStudentFinRecievedYear());
            dto.setStudentFinRecievedMonth(studentFinance.getStudentFinRecievedMonth());
            dto.setStudentFinRecievedYearMonth(studentFinance.getStudentFinRecievedYearMonth());
            dto.setStudentFinRecievedAmount(studentFinance.getStudentFinRecievedAmount());
            dto.setStudyCountryInId(studentFinance.getStudyCountryInId());
            dto.setStudyGovInId(studentFinance.getStudyGovInId());
            dto.setStudyFinanceBankeId(studentFinance.getStudyFinanceBankeId());
            dto.setStudyFinanceBankBranchId(studentFinance.getStudyFinanceBankBranchId());
            dto.setStudyFinanceDofId(studentFinance.getStudyFinanceDofId());
            dto.setStudyFinRecievedStatus(studentFinance.getStudyFinRecievedStatus());
            dto.setStudyNote1(studentFinance.getStudyNote1());
            dto.setStudyReceivedWhomStatusId(studentFinance.getStudyReceivedWhomStatusId());
            dto.setStudyReceivedWhomId(studentFinance.getStudyReceivedWhomId());
        }

        return dto;
    }

    private void updateStudentDataFromDto(StudentDatum studentData, StudentQuickEditDto dto) {
        studentData.setStudentName(dto.getStudentName());
        studentData.setStudentDelegationDecisionYear(dto.getStudentDelegationDecisionYear());
        studentData.setStudentDegreeId(dto.getStudentDegreeId());
        studentData.setStudentMajorId(dto.getStudentMajorId());
        studentData.setStudentDelegateDecisionNo(dto.getStudentDelegateDecisionNo());
        studentData.setStudentStudyNoOfYears(dto.getStudentStudyNoOfYears());
        studentData.setStudentCurrentLevelId(dto.getStudentCurrentLevelId());
        studentData.setStudentFirstReceiptOfDue(dto.getStudentFirstReceiptOfDue());
        studentData.setStudentAmount(dto.getStudentAmount());
        studentData.setStudentNote1(dto.getStudentNote1());
        studentData.setStudentNote2(dto.getStudentNote2());
        studentData.setStudentStudyStatusId(dto.getStudentStudyStatusId());
        studentData.setStudentAddedQuarter(dto.getStudentAddedQuarter());
        studentData.setStudentGender(dto.getStudentGender());
        studentData.setStudentPassportNumber(dto.getStudentPassportNumber());
        studentData.setStudyUniversityFeeId(dto.getStudyUniversityFeeId());
        studentData.setStudyUniversityFeeStatusId(dto.getStudyUniversityFeeStatusId());
        studentData.setStudentLastQuarter(dto.getStudentLastQuarter());
        studentData.setCurrentQuarter(dto.getCurrentQuarter());
        studentData.setStudentAgencyTypeId(dto.getStudentAgencyTypeId());
        studentData.setStudentMinistryTypeId(dto.getStudentMinistryTypeId());
        studentData.setStudentStudyTypeId(dto.getStudentStudyTypeId());
        studentData.setStudentUniversityTypeId(dto.getStudentUniversityTypeId());
        studentData.setStudentBirthdatePlace(dto.getStudentBirthdatePlace());
        studentData.setStudentPassportIssueCountryId(dto.getStudentPassportIssueCountryId());
        studentData.setStudentEmail(dto.getStudentEmail());
        studentData.setStudentCellphone(dto.getStudentCellphone());
        studentData.setStudentHomeNumber(dto.getStudentHomeNumber());
        studentData.setStudentEmergencyContact(dto.getStudentEmergencyContact());
    }

    private void updateStudentFinanceFromDto(StudentFinance studentFinance, StudentQuickEditDto dto) {
        if (dto.getStudentFinId() != null) {
            studentFinance.setStudentFinId(dto.getStudentFinId());
        }
        if (dto.getStudentFinName() != null) {
            studentFinance.setStudentFinName(dto.getStudentFinName());
        }
        if (dto.getStudentFinRecievedYear() != null) {
            studentFinance.setStudentFinRecievedYear(dto.getStudentFinRecievedYear());
        }
        if (dto.getStudentFinRecievedMonth() != null) {
            studentFinance.setStudentFinRecievedMonth(dto.getStudentFinRecievedMonth());
        }
        if (dto.getStudentFinRecievedYearMonth() != null) {
            studentFinance.setStudentFinRecievedYearMonth(dto.getStudentFinRecievedYearMonth());
        }
        if (dto.getStudentFinRecievedAmount() != null) {
            studentFinance.setStudentFinRecievedAmount(dto.getStudentFinRecievedAmount());
        }
        if (dto.getStudyCountryInId() != null) {
            studentFinance.setStudyCountryInId(dto.getStudyCountryInId());
        }
        if (dto.getStudyGovInId() != null) {
            studentFinance.setStudyGovInId(dto.getStudyGovInId());
        }
        if (dto.getStudyFinanceBankeId() != null) {
            studentFinance.setStudyFinanceBankeId(dto.getStudyFinanceBankeId());
        }
        if (dto.getStudyFinanceBankBranchId() != null) {
            studentFinance.setStudyFinanceBankBranchId(dto.getStudyFinanceBankBranchId());
        }
        if (dto.getStudyFinanceDofId() != null) {
            studentFinance.setStudyFinanceDofId(dto.getStudyFinanceDofId());
        }
        if (dto.getStudyFinRecievedStatus() != null) {
            studentFinance.setStudyFinRecievedStatus(dto.getStudyFinRecievedStatus());
        }
        if (dto.getStudyNote1() != null) {
            studentFinance.setStudyNote1(dto.getStudyNote1());
        }
        if (dto.getStudyReceivedWhomStatusId() != null) {
            studentFinance.setStudyReceivedWhomStatusId(dto.getStudyReceivedWhomStatusId());
        }
        if (dto.getStudyReceivedWhomId() != null) {
            studentFinance.setStudyReceivedWhomId(dto.getStudyReceivedWhomId());
        }
    }
}

