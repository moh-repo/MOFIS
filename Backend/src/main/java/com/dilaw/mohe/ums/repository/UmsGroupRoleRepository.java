package com.dilaw.mohe.ums.repository;

import com.dilaw.mohe.ums.model.UmsGroupRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UmsGroupRoleRepository extends JpaRepository<UmsGroupRole, Long> {

    Optional<UmsGroupRole> findByGroupIdAndRoleId(Long groupId, Long roleId);

    List<UmsGroupRole> findByGroupIdAndIsActiveTrue(Long groupId);

    List<UmsGroupRole> findByRoleIdAndIsActiveTrue(Long roleId);

    boolean existsByGroupIdAndRoleIdAndIsActiveTrue(Long groupId, Long roleId);

    void deleteByGroupIdAndRoleId(Long groupId, Long roleId);
}
