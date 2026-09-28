package com.dilaw.mohe.studentportal.repository;

import com.dilaw.mohe.studentportal.model.StudentRolePermission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public interface StudentRolePermissionRepository extends JpaRepository<StudentRolePermission, Integer> {
    boolean existsByRoleIdAndPermissionId(Integer roleId, Integer permissionId);

    @Modifying
    @Transactional
    @Query("DELETE FROM StudentRolePermission r WHERE r.roleId = :roleId AND r.permissionId = :permissionId")
    void deleteByRoleIdAndPermissionId(@Param("roleId") Integer roleId, @Param("permissionId") Integer permissionId);
}
