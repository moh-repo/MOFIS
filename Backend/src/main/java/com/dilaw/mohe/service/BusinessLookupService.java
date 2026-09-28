package com.dilaw.mohe.service;

import com.dilaw.mohe.model.*;
import com.dilaw.mohe.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Transactional(readOnly = true)
@Service
public class BusinessLookupService {

    @Autowired private LuUniversityFeeStatusRepository universityFeeStatusRepository;
    @Autowired private LuFeeSourceRepository feeSourceRepository;
    @Autowired private LuFeeCheckPayorRepository feeCheckPayorRepository;
    @Autowired private LuFeeScopeRepository feeScopeRepository;
    @Autowired private LuAlertStatusRepository alertStatusRepository;
    @Autowired private LuAlertSeverityRepository alertSeverityRepository;
    @Autowired private LuYesNoRepository yesNoRepository;
    @Autowired private LuStatusActiveRepository statusActiveRepository;
    @Autowired private LuAcademicYearRepository academicYearRepository;
    @Autowired private LuSemesterNumberRepository semesterNumberRepository;
    @Autowired private LuBooksAllowancePayStatusRepository booksAllowancePayStatusRepository;
    @Autowired private LuLoanStatusRepository loanStatusRepository;
    @Autowired private LuUserStatusRepository userStatusRepository;
    @Autowired private LuUserPrivilegeRepository userPrivilegeRepository;
    @Autowired private LuHttpMethodRepository httpMethodRepository;
    @Autowired private LuPermissionResourceTypeRepository permissionResourceTypeRepository;
    @Autowired private LuPermissionActionTypeRepository permissionActionTypeRepository;
    @Autowired private LuResourceKindRepository resourceKindRepository;
    @Autowired private LuFieldTypeRepository fieldTypeRepository;
    @Autowired private LuApprovalRequestTypeRepository approvalRequestTypeRepository;
    @Autowired private LuApprovalStatusRepository approvalStatusRepository;
    @Autowired private LuBooleanFilterRepository booleanFilterRepository;
    @Autowired private LuVisibilityFilterRepository visibilityFilterRepository;
    @Autowired private LuReportDimensionRepository reportDimensionRepository;
    @Autowired private LuTrendClassRepository trendClassRepository;
    @Autowired private LuReportLabelRepository reportLabelRepository;
    @Autowired private LuDeathProcedureRepository deathProcedureRepository;

    public List<LuDeathProcedure> getDeathProcedures() {
        return deathProcedureRepository.findByIsDeletedFalseOrderBySortOrderAsc();
    }

    public List<LuUniversityFeeStatus> getUniversityFeeStatuses() {
        return universityFeeStatusRepository.findAll();
    }

    public List<LuFeeSource> getFeeSources() {
        return feeSourceRepository.findAll();
    }

    public List<LuFeeCheckPayor> getFeeCheckPayors() {
        return feeCheckPayorRepository.findAll();
    }

    public List<LuFeeScope> getFeeScopes(String scopeType) {
        if (scopeType == null || scopeType.isBlank()) {
            return feeScopeRepository.findAll();
        }
        return feeScopeRepository.findByScopeTypeOrderBySortOrderAsc(scopeType);
    }

    public List<LuAlertStatus> getAlertStatuses() {
        return alertStatusRepository.findAll();
    }

    public List<LuAlertSeverity> getAlertSeverities() {
        return alertSeverityRepository.findAll();
    }

    public List<LuYesNo> getYesNoOptions() {
        return yesNoRepository.findAll();
    }

    public List<LuStatusActive> getStatusActiveOptions() {
        return statusActiveRepository.findAll();
    }

    public List<LuAcademicYear> getAcademicYears() {
        return academicYearRepository.findAll();
    }

    public List<LuSemesterNumber> getSemesterNumbers() {
        return semesterNumberRepository.findAll();
    }

    public List<LuBooksAllowancePayStatus> getBooksAllowancePayStatuses() {
        return booksAllowancePayStatusRepository.findByIsDeletedFalseOrderBySortOrderAsc();
    }

    public List<LuLoanStatus> getLoanStatuses() {
        return loanStatusRepository.findByIsDeletedFalseOrderBySortOrderAsc();
    }

    public List<LuUserStatus> getUserStatuses() {
        return userStatusRepository.findByIsDeletedFalseOrderBySortOrderAsc();
    }

    public List<LuUserPrivilege> getUserPrivileges() {
        return userPrivilegeRepository.findByIsDeletedFalseOrderBySortOrderAsc();
    }

    public List<LuHttpMethod> getHttpMethods() {
        return httpMethodRepository.findByIsDeletedFalseOrderBySortOrderAsc();
    }

    public List<LuPermissionResourceType> getPermissionResourceTypes() {
        return permissionResourceTypeRepository.findByIsDeletedFalseOrderBySortOrderAsc();
    }

    public List<LuPermissionActionType> getPermissionActionTypes() {
        return permissionActionTypeRepository.findByIsDeletedFalseOrderBySortOrderAsc();
    }

    public List<LuResourceKind> getResourceKinds() {
        return resourceKindRepository.findByIsDeletedFalseOrderBySortOrderAsc();
    }

    public List<LuFieldType> getFieldTypes() {
        return fieldTypeRepository.findByIsDeletedFalseOrderBySortOrderAsc();
    }

    public List<LuApprovalRequestType> getApprovalRequestTypes() {
        return approvalRequestTypeRepository.findByIsDeletedFalseOrderBySortOrderAsc();
    }

    public List<LuApprovalStatus> getApprovalStatuses() {
        return approvalStatusRepository.findByIsDeletedFalseOrderBySortOrderAsc();
    }

    public List<LuBooleanFilter> getBooleanFilters() {
        return booleanFilterRepository.findByIsDeletedFalseOrderBySortOrderAsc();
    }

    public List<LuVisibilityFilter> getVisibilityFilters() {
        return visibilityFilterRepository.findByIsDeletedFalseOrderBySortOrderAsc();
    }

    public List<LuReportDimension> getReportDimensions(String groupCode) {
        return reportDimensionRepository.findByGroupCodeAndIsDeletedFalseOrderBySortOrderAsc(groupCode);
    }

    public List<LuTrendClass> getTrendClasses(String category) {
        return trendClassRepository.findByCategoryAndIsDeletedFalseOrderBySortOrderAsc(category);
    }

    public List<LuReportLabel> getReportLabels() {
        return reportLabelRepository.findByIsDeletedFalseOrderBySortOrderAsc();
    }

    public Optional<LuReportLabel> getReportLabelByCode(String code) {
        return reportLabelRepository.findByCodeAndIsDeletedFalse(code);
    }

    public boolean isValidAlertSeverity(String severity) {
        if (severity == null || severity.isBlank()) {
            return true;
        }
        String normalized = severity.trim().toUpperCase();
        return alertSeverityRepository.findAll().stream()
                .filter(s -> !Boolean.TRUE.equals(s.getIsDeleted()))
                .anyMatch(s -> normalized.equals(s.getCode().toUpperCase()));
    }
}
