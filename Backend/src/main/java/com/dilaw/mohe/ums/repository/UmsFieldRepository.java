package com.dilaw.mohe.ums.repository;

import com.dilaw.mohe.ums.model.UmsField;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UmsFieldRepository extends JpaRepository<UmsField, Long> {

        Optional<UmsField> findByParentResourceIdAndResourceCode(Long parentResourceId, String resourceCode);

        List<UmsField> findByParentResourceIdAndIsActiveTrue(Long parentResourceId);

        // findBySectionIdAndIsActiveTrue is redundant if Section is also
        // ParentResource. Removed to avoid confusion.
        // If needed, caller can use look up by parentResourceId.

        List<UmsField> findByIsSensitiveTrueAndIsActiveTrue();

        @Query("SELECT DISTINCT f FROM UmsField f " +
                        "JOIN UmsPermission p ON p.resourceType = 'FIELD' AND p.resource.id = f.id " +
                        "JOIN UmsRolePermission rp ON rp.permission.id = p.id " +
                        "JOIN UmsRole r ON r.id = rp.role.id " +
                        "LEFT JOIN UmsGroupRole gr ON gr.role.id = r.id " +
                        "LEFT JOIN UmsGroup g ON g.id = gr.group.id " +
                        "LEFT JOIN UmsUserGroup ug ON ug.group.id = g.id " +
                        "LEFT JOIN UmsUserRole ur ON ur.role.id = r.id " +
                        "WHERE f.isActive = true AND f.parentResource.id = :parentResourceId AND p.actionType = :actionType "
                        +
                        "AND p.isActive = true AND rp.isActive = true AND r.isActive = true " +
                        "AND ((ug.user.id = :userId AND ug.isActive = true AND gr.isActive = true) " +
                        "OR (ur.user.id = :userId AND ur.isActive = true))")
        List<UmsField> findAccessibleFieldsByUserIdAndParentResourceId(@Param("userId") Long userId,
                        @Param("parentResourceId") Long parentResourceId,
                        @Param("actionType") String actionType);
}
