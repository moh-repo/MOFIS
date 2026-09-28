package com.dilaw.mohe.ums.repository;

import com.dilaw.mohe.ums.model.UmsRole;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UmsRoleRepository extends JpaRepository<UmsRole, Long> {

    Optional<UmsRole> findByRoleName(String roleName);

    boolean existsByRoleName(String roleName);

    /**
     * The roles a group holds, as loaded entities. Reading them through
     * {@code UmsGroupRole.getRole()} gave LAZY proxies that Jackson serialised after
     * the transaction closed, so {@code GET /ums/groups/{id}/roles} answered 500 for
     * every group holding a role (live QA #5, QA B10).
     */
    @Query("SELECT r FROM UmsGroupRole gr JOIN gr.role r " +
            "WHERE gr.group.id = :groupId AND gr.isActive = true ORDER BY r.priority, r.roleName")
    List<UmsRole> findRolesByGroupId(@Param("groupId") Long groupId);

    List<UmsRole> findByIsActiveTrue();

    List<UmsRole> findByIsActiveTrueOrderByPriorityDesc();

        Page<UmsRole> findByIsActiveTrue(Pageable pageable);

        @Query("SELECT r FROM UmsRole r WHERE r.isActive = true AND "
                        + "(:keyword IS NULL OR LOWER(r.roleName) LIKE LOWER(CONCAT('%', :keyword, '%')) OR "
                        + "LOWER(COALESCE(r.roleNameAr, '')) LIKE LOWER(CONCAT('%', :keyword, '%')) OR "
                        + "LOWER(COALESCE(r.description, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))) AND "
                        + "(:system IS NULL OR r.isSystem = :system)")
        Page<UmsRole> listRoles(
                        @Param("keyword") String keyword,
                        @Param("system") Boolean system,
                        Pageable pageable);

        @Query("SELECT r FROM UmsRole r WHERE r.isActive = true AND " +
            "(LOWER(r.roleName) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
            "LOWER(r.roleNameAr) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
            "LOWER(r.description) LIKE LOWER(CONCAT('%', :keyword, '%')))")
        Page<UmsRole> searchRoles(@Param("keyword") String keyword, Pageable pageable);

    // Get roles for a user (through groups)
    @Query("SELECT DISTINCT r FROM UmsRole r " +
            "LEFT JOIN r.groupRoles gr " +
            "LEFT JOIN gr.group g " +
            "LEFT JOIN g.userGroups ug " +
            "WHERE r.isActive = true AND gr.isActive = true AND ug.user.id = :userId AND ug.isActive = true")
    List<UmsRole> findRolesByUserIdThroughGroups(@Param("userId") Long userId);

    // Get roles directly assigned to user
    @Query("SELECT DISTINCT r FROM UmsRole r " +
            "LEFT JOIN r.userRoles ur " +
            "WHERE r.isActive = true AND ur.user.id = :userId AND ur.isActive = true")
    List<UmsRole> findRolesByUserIdDirect(@Param("userId") Long userId);
}
