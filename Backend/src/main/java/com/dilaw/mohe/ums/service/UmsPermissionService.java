package com.dilaw.mohe.ums.service;

import com.dilaw.mohe.ums.model.*;
import com.dilaw.mohe.ums.repository.*;
import com.dilaw.mohe.ums.model.UmsUserPermission;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class UmsPermissionService {

    @Autowired
    private UmsPermissionRepository permissionRepository;

    @Autowired
    private UmsPageRepository pageRepository;

    @Autowired
    private UmsFieldRepository fieldRepository;

    @Autowired
    private UmsReportRepository reportRepository;

    @Autowired
    private UmsUserRepository userRepository;

    @Autowired
    private UmsUserPermissionRepository userPermissionRepository;

    // ========== Permission CRUD ==========

    public List<UmsPermission> getAllActivePermissions() {
        return permissionRepository.findByIsActiveTrue();
    }

    public Page<UmsPermission> listPermissions(String keyword, String resourceType, String actionType,
            Pageable pageable) {
        return permissionRepository.listPermissions(trimNull(keyword), trimNull(resourceType), trimNull(actionType),
                pageable);
    }

    private static String trimNull(String value) {
        if (value == null) {
            return null;
        }
        String t = value.trim();
        return t.isEmpty() ? null : t;
    }

    public Page<UmsPermission> getAllPermissions(Pageable pageable) {
        return listPermissions(null, null, null, pageable);
    }

    public Page<UmsPermission> searchPermissions(String keyword, Pageable pageable) {
        return listPermissions(trimNull(keyword), null, null, pageable);
    }

    public Optional<UmsPermission> getPermissionById(Long id) {
        return permissionRepository.findById(id);
    }

    public Optional<UmsPermission> getPermissionByCode(String permissionCode) {
        return permissionRepository.findByPermissionCode(permissionCode);
    }

    public List<UmsPermission> getPermissionsByResourceType(String resourceType) {
        return permissionRepository.findByResourceTypeAndIsActiveTrue(resourceType);
    }

    public List<UmsPermission> getPermissionsByActionType(String actionType) {
        return permissionRepository.findByActionTypeAndIsActiveTrue(actionType);
    }

    public UmsPermission createPermission(UmsPermission permission) {
        permission.setIsActive(true);
        return permissionRepository.save(permission);
    }

    public UmsPermission updatePermission(Long id, UmsPermission updatedPermission) {
        return permissionRepository.findById(id)
                .map(existing -> {
                    existing.setPermissionName(updatedPermission.getPermissionName());
                    existing.setPermissionNameAr(updatedPermission.getPermissionNameAr());
                    existing.setPermissionCode(updatedPermission.getPermissionCode());
                    existing.setDescription(updatedPermission.getDescription());
                    existing.setResourceType(updatedPermission.getResourceType());
                    existing.setActionType(updatedPermission.getActionType());
                    return permissionRepository.save(existing);
                })
                .orElseThrow(() -> new RuntimeException("Permission not found with id: " + id));
    }

    /** Switches the permission off; 404 for an id with no row (round-5 QA R5-22), which used to read as success. */
    public void deletePermission(Long id) {
        UmsPermission permission = permissionRepository.findById(id)
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.NOT_FOUND, "Permission not found"));
        permission.setIsActive(false);
        permissionRepository.save(permission);
    }

    // ========== Permission Checking ==========

    /**
     * Check if user has a specific permission by code
     * 
     * @param userId         User ID
     * @param permissionCode Permission code (e.g., "STUDENTS.VIEW")
     * @return true if user has the permission
     */
    @Transactional(readOnly = true)
    public boolean hasPermission(Long userId, String permissionCode) {
        // Super admin has all permissions
        Optional<UmsUser> user = userRepository.findById(userId);
        if (user.isPresent() && user.get().getIsSuperAdmin()) {
            return true;
        }

        // Check permissions from roles and groups
        boolean hasRolePermission = permissionRepository.hasPermission(userId, permissionCode);
        if (hasRolePermission) {
            return true;
        }

        // Check direct user permissions
        Optional<UmsPermission> permission = permissionRepository.findByPermissionCode(permissionCode);
        if (permission.isPresent() && userPermissionRepository != null) {
            // R-14: a lapsed exception no longer counts.
            return userPermissionRepository.existsEffective(userId, permission.get().getId());
        }

        return false;
    }

    /**
     * Check if user has permission for a specific resource and action
     * 
     * @param userId       User ID
     * @param resourceType Resource type (PAGE, SECTION, FIELD, REPORT)
     * @param resourceId   Resource ID
     * @param actionType   Action type (SEARCH, VIEW, ADD, EDIT, DELETE, EXPORT)
     * @return true if user has the permission
     */
    public boolean hasPermissionForResource(Long userId, String resourceType, Long resourceId, String actionType) {
        // Super admin has all permissions
        Optional<UmsUser> user = userRepository.findById(userId);
        if (user.isPresent() && user.get().getIsSuperAdmin()) {
            return true;
        }

        return permissionRepository.hasPermissionForResource(userId, resourceType, resourceId, actionType);
    }

    /**
     * Check if user can access a page
     * 
     * @param userId   User ID
     * @param pageCode Page code (e.g., "STUDENTS")
     * @return true if user can VIEW the page
     */
    public boolean canAccessPage(Long userId, String pageCode) {
        return hasPermission(userId, pageCode + ".VIEW");
    }

    /**
     * Check if user can perform action on a page
     * 
     * @param userId     User ID
     * @param pageCode   Page code
     * @param actionType Action type
     * @return true if user has the permission
     */
    public boolean canPerformPageAction(Long userId, String pageCode, String actionType) {
        return hasPermission(userId, pageCode + "." + actionType);
    }

    // ========== Get User Permissions ==========

    /**
     * Get all permissions for a user
     * 
     * @param userId User ID
     * @return Set of permissions
     */
    @Transactional(readOnly = true)
    public Set<UmsPermission> getUserPermissions(Long userId) {
        // Super admin has all permissions
        Optional<UmsUser> user = userRepository.findById(userId);
        if (user.isPresent() && user.get().getIsSuperAdmin()) {
            return new HashSet<>(permissionRepository.findByIsActiveTrue());
        }

        // Get permissions from roles and groups
        Set<UmsPermission> permissions = permissionRepository.findAllPermissionsByUserId(userId);
        
        // Add direct user permissions
        if (userPermissionRepository != null) {
            List<UmsPermission> directPermissions = userPermissionRepository.findByUserIdAndIsActiveTrue(userId)
                    .stream()
                    .filter(UmsUserPermission::isEffective) // R-14: lapsed exceptions are ignored
                    .map(UmsUserPermission::getPermission)
                    .filter(p -> p.getIsActive())
                    .toList();
            permissions.addAll(directPermissions);
        }

        return permissions;
    }

    /**
     * Get permission codes for a user (for frontend)
     * 
     * @param userId User ID
     * @return List of permission codes
     */
    @Transactional(readOnly = true)
    public List<String> getUserPermissionCodes(Long userId) {
        return getUserPermissions(userId).stream()
                .map(UmsPermission::getPermissionCode)
                .toList();
    }

    /**
     * Get accessible pages for a user
     * 
     * @param userId User ID
     * @return List of accessible pages
     */
    public List<UmsPage> getAccessiblePages(Long userId) {
        // Super admin can access all pages
        Optional<UmsUser> user = userRepository.findById(userId);
        if (user.isPresent() && user.get().getIsSuperAdmin()) {
            return pageRepository.findByIsActiveTrueAndIsVisibleTrueOrderByDisplayOrder();
        }

        return pageRepository.findAccessiblePagesByUserId(userId);
    }

    /**
     * Get accessible fields for a user on a specific page
     * 
     * @param userId     User ID
     * @param pageId     Page ID
     * @param actionType Action type (VIEW or EDIT)
     * @return List of accessible fields
     */
    public List<UmsField> getAccessibleFields(Long userId, Long pageId, String actionType) {
        // Super admin can access all fields
        Optional<UmsUser> user = userRepository.findById(userId);
        if (user.isPresent() && user.get().getIsSuperAdmin()) {
            return fieldRepository.findByParentResourceIdAndIsActiveTrue(pageId);
        }

        return fieldRepository.findAccessibleFieldsByUserIdAndParentResourceId(userId, pageId, actionType);
    }

    /**
     * Get accessible field codes for a user on a specific page
     * 
     * @param userId     User ID
     * @param pageCode   Page code
     * @param actionType Action type
     * @return List of field codes
     */
    public List<String> getAccessibleFieldCodes(Long userId, String pageCode, String actionType) {
        Optional<UmsPage> page = pageRepository.findByResourceCode(pageCode);
        if (page.isEmpty()) {
            return Collections.emptyList();
        }

        return getAccessibleFields(userId, page.get().getId(), actionType).stream()
                .map(UmsField::getFieldCode)
                .toList();
    }

    /**
     * Get accessible reports for a user
     * 
     * @param userId User ID
     * @return List of accessible reports
     */
    public List<UmsReport> getAccessibleReports(Long userId) {
        // Super admin can access all reports
        Optional<UmsUser> user = userRepository.findById(userId);
        if (user.isPresent() && user.get().getIsSuperAdmin()) {
            return reportRepository.findByIsActiveTrue();
        }

        return reportRepository.findAccessibleReportsByUserId(userId);
    }

    // ========== Permission Map for Frontend ==========

    /**
     * Get a permission map for frontend (grouped by page)
     * 
     * @param userId User ID
     * @return Map of page code to list of actions
     */
    @Transactional(readOnly = true)
    public Map<String, List<String>> getPermissionMap(Long userId) {
        Map<String, List<String>> permissionMap = new HashMap<>();

        Set<UmsPermission> permissions = getUserPermissions(userId);
        for (UmsPermission permission : permissions) {
            String[] parts = permission.getPermissionCode().split("\\.");
            if (parts.length == 2) {
                String pageCode = parts[0];
                String action = parts[1];
                permissionMap.computeIfAbsent(pageCode, k -> new ArrayList<>()).add(action);
            }
        }

        return permissionMap;
    }
}
