package com.dilaw.mohe.ums.repository;

import com.dilaw.mohe.ums.model.UmsPermission;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public interface UmsPermissionRepository extends JpaRepository<UmsPermission, Long> {

        Optional<UmsPermission> findByPermissionCode(String permissionCode);

        boolean existsByPermissionCode(String permissionCode);

        List<UmsPermission> findByIsActiveTrue();

        List<UmsPermission> findByResourceTypeAndIsActiveTrue(String resourceType);

        /** R-18: the codes of every active field-level permission, for the field masks. */
        @Query("SELECT p.permissionCode FROM UmsPermission p WHERE p.resourceType = 'FIELD' AND p.isActive = true")
        List<String> findActiveFieldPermissionCodes();

        @Query("SELECT p FROM UmsPermission p WHERE p.resourceType = :resourceType AND p.resource.id = :resourceId AND p.isActive = true")
        List<UmsPermission> findByResourceTypeAndResource_IdAndIsActiveTrue(@Param("resourceType") String resourceType,
                        @Param("resourceId") Long resourceId);

        List<UmsPermission> findByActionTypeAndIsActiveTrue(String actionType);

        Page<UmsPermission> findByIsActiveTrue(Pageable pageable);

        @Query("SELECT p FROM UmsPermission p WHERE p.isActive = true AND "
                        + "(:keyword IS NULL OR LOWER(p.permissionCode) LIKE LOWER(CONCAT('%', :keyword, '%')) OR "
                        + "LOWER(p.permissionName) LIKE LOWER(CONCAT('%', :keyword, '%'))) AND "
                        + "(:resourceType IS NULL OR p.resourceType = :resourceType) AND "
                        + "(:actionType IS NULL OR p.actionType = :actionType)")
        Page<UmsPermission> listPermissions(
                        @Param("keyword") String keyword,
                        @Param("resourceType") String resourceType,
                        @Param("actionType") String actionType,
                        Pageable pageable);

        @Query("SELECT p FROM UmsPermission p WHERE p.isActive = true AND " +
                        "(LOWER(p.permissionCode) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
                        "LOWER(p.permissionName) LIKE LOWER(CONCAT('%', :keyword, '%')))")
        Page<UmsPermission> searchPermissions(@Param("keyword") String keyword, Pageable pageable);

        // Get all permissions for a role
        @Query("SELECT p FROM UmsPermission p " +
                        "JOIN UmsRolePermission rp ON rp.permission.id = p.id " +
                        "WHERE p.isActive = true AND rp.isActive = true AND rp.role.id = :roleId")
        List<UmsPermission> findPermissionsByRoleId(@Param("roleId") Long roleId);

        // Get all permissions for a user (through roles, groups, and direct assignments)
        @Query("SELECT DISTINCT p FROM UmsPermission p " +
                        "WHERE p.isActive = true AND (" +
                        "p.id IN (SELECT DISTINCT rp.permission.id FROM UmsRolePermission rp " +
                        "JOIN UmsRole r ON r.id = rp.role.id " +
                        "LEFT JOIN UmsGroupRole gr ON gr.role.id = r.id " +
                        "LEFT JOIN UmsGroup g ON g.id = gr.group.id " +
                        "LEFT JOIN UmsUserGroup ug ON ug.group.id = g.id " +
                        "LEFT JOIN UmsUserRole ur ON ur.role.id = r.id " +
                        "WHERE rp.isActive = true AND r.isActive = true " +
                        "AND ((ug.user.id = :userId AND ug.isActive = true AND gr.isActive = true) " +
                        "OR (ur.user.id = :userId AND ur.isActive = true))) " +
                        "OR p.id IN (SELECT up.permission.id FROM UmsUserPermission up " +
                        "WHERE up.user.id = :userId AND up.isActive = true " +
                        "AND (up.expiresAt IS NULL OR up.expiresAt > CURRENT_TIMESTAMP)))")
        Set<UmsPermission> findAllPermissionsByUserId(@Param("userId") Long userId);

        // Check if user has specific permission (from roles, groups, or direct)
        @Query("SELECT CASE WHEN COUNT(p) > 0 THEN true ELSE false END FROM UmsPermission p " +
                        "WHERE p.permissionCode = :permissionCode AND p.isActive = true AND (" +
                        "p.id IN (SELECT DISTINCT rp.permission.id FROM UmsRolePermission rp " +
                        "JOIN UmsRole r ON r.id = rp.role.id " +
                        "LEFT JOIN UmsGroupRole gr ON gr.role.id = r.id " +
                        "LEFT JOIN UmsGroup g ON g.id = gr.group.id " +
                        "LEFT JOIN UmsUserGroup ug ON ug.group.id = g.id " +
                        "LEFT JOIN UmsUserRole ur ON ur.role.id = r.id " +
                        "WHERE rp.isActive = true AND r.isActive = true " +
                        "AND ((ug.user.id = :userId AND ug.isActive = true AND gr.isActive = true) " +
                        "OR (ur.user.id = :userId AND ur.isActive = true))) " +
                        "OR p.id IN (SELECT up.permission.id FROM UmsUserPermission up " +
                        "WHERE up.user.id = :userId AND up.isActive = true " +
                        "AND (up.expiresAt IS NULL OR up.expiresAt > CURRENT_TIMESTAMP)))")
        boolean hasPermission(@Param("userId") Long userId, @Param("permissionCode") String permissionCode);

        // Check if user has permission for specific resource and action (from roles, groups, or direct)
        @Query("SELECT CASE WHEN COUNT(p) > 0 THEN true ELSE false END FROM UmsPermission p " +
                        "WHERE p.resourceType = :resourceType AND p.resource.id = :resourceId AND p.actionType = :actionType " +
                        "AND p.isActive = true AND (" +
                        "p.id IN (SELECT DISTINCT rp.permission.id FROM UmsRolePermission rp " +
                        "JOIN UmsRole r ON r.id = rp.role.id " +
                        "LEFT JOIN UmsGroupRole gr ON gr.role.id = r.id " +
                        "LEFT JOIN UmsGroup g ON g.id = gr.group.id " +
                        "LEFT JOIN UmsUserGroup ug ON ug.group.id = g.id " +
                        "LEFT JOIN UmsUserRole ur ON ur.role.id = r.id " +
                        "WHERE rp.isActive = true AND r.isActive = true " +
                        "AND ((ug.user.id = :userId AND ug.isActive = true AND gr.isActive = true) " +
                        "OR (ur.user.id = :userId AND ur.isActive = true))) " +
                        "OR p.id IN (SELECT up.permission.id FROM UmsUserPermission up " +
                        "WHERE up.user.id = :userId AND up.isActive = true " +
                        "AND (up.expiresAt IS NULL OR up.expiresAt > CURRENT_TIMESTAMP)))")
        boolean hasPermissionForResource(@Param("userId") Long userId,
                        @Param("resourceType") String resourceType,
                        @Param("resourceId") Long resourceId,
                        @Param("actionType") String actionType);
}
