package com.dilaw.mohe.studentportal.repository;

import com.dilaw.mohe.studentportal.model.StudentPermission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface StudentPermissionRepository extends JpaRepository<StudentPermission, Integer> {

    /**
     * Loads all effective permissions for a student user by resolving
     * group-based and directly-assigned role permissions via a native SQL query.
     * Native query avoids JPQL UNION limitations.
     */
    @Query(value = "SELECT p.* FROM STUDENTCORE.student_permissions p " +
           "JOIN STUDENTCORE.student_role_permissions rp ON rp.permission_id = p.permission_id " +
           "WHERE rp.role_id = :roleId", nativeQuery = true)
    List<StudentPermission> findByRoleId(@Param("roleId") Integer roleId);

    @Query(value = """
        SELECT DISTINCT p.*
        FROM   STUDENTCORE.student_permissions p
        JOIN   STUDENTCORE.student_role_permissions rp ON rp.permission_id = p.permission_id
        WHERE  p.is_active = 1
          AND  rp.role_id IN (
                   -- roles via groups
                   SELECT gr.role_id
                   FROM   STUDENTCORE.student_user_groups ug
                   JOIN   STUDENTCORE.student_group_roles  gr ON gr.group_id = ug.group_id
                   WHERE  ug.student_user_id = :studentUserId
                   UNION
                   -- directly assigned roles
                   SELECT ur.role_id
                   FROM   STUDENTCORE.student_user_roles ur
                   WHERE  ur.student_user_id = :studentUserId
               )
        """, nativeQuery = true)
    List<StudentPermission> findEffectivePermissionsForUser(@Param("studentUserId") Integer studentUserId);
}
