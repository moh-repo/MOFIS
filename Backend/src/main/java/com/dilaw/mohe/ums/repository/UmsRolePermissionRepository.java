package com.dilaw.mohe.ums.repository;

import com.dilaw.mohe.ums.model.UmsRolePermission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UmsRolePermissionRepository extends JpaRepository<UmsRolePermission, Long> {

    Optional<UmsRolePermission> findByRoleIdAndPermissionId(Long roleId, Long permissionId);

    List<UmsRolePermission> findByRoleIdAndIsActiveTrue(Long roleId);

    List<UmsRolePermission> findByPermissionIdAndIsActiveTrue(Long permissionId);

    boolean existsByRoleIdAndPermissionIdAndIsActiveTrue(Long roleId, Long permissionId);

    void deleteByRoleIdAndPermissionId(Long roleId, Long permissionId);

    void deleteByRoleId(Long roleId);
}
