package com.dilaw.mohe.ums.repository;

import com.dilaw.mohe.ums.model.UmsUserRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UmsUserRoleRepository extends JpaRepository<UmsUserRole, Long> {

    Optional<UmsUserRole> findByUserIdAndRoleId(Long userId, Long roleId);

    List<UmsUserRole> findByUserIdAndIsActiveTrue(Long userId);

    List<UmsUserRole> findByRoleIdAndIsActiveTrue(Long roleId);

    /** R-21: active rows whose window has closed. */
    List<UmsUserRole> findByIsActiveTrueAndValidToBefore(java.time.LocalDateTime now);

    /** R-21: rows waiting for their window; a row removed by hand has no window left (see removeRoleFromUser). */
    @org.springframework.data.jpa.repository.Query(
            "SELECT ur FROM UmsUserRole ur WHERE ur.isActive = false AND ur.validFrom IS NOT NULL AND ur.validFrom <= :now "
            + "AND (ur.validTo IS NULL OR ur.validTo > :now)")
    List<UmsUserRole> findDueToOpen(@org.springframework.data.repository.query.Param("now") java.time.LocalDateTime now);

    boolean existsByUserIdAndRoleIdAndIsActiveTrue(Long userId, Long roleId);

    void deleteByUserIdAndRoleId(Long userId, Long roleId);
}
