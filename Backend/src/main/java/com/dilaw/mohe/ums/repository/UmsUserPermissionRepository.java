package com.dilaw.mohe.ums.repository;

import com.dilaw.mohe.ums.model.UmsUserPermission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UmsUserPermissionRepository extends JpaRepository<UmsUserPermission, Long> {

    Optional<UmsUserPermission> findByUserIdAndPermissionId(Long userId, Long permissionId);

    boolean existsByUserIdAndPermissionIdAndIsActiveTrue(Long userId, Long permissionId);

    List<UmsUserPermission> findByUserIdAndIsActiveTrue(Long userId);

    @Query("SELECT up.permission.id FROM UmsUserPermission up WHERE up.user.id = :userId AND up.isActive = true")
    List<Long> findPermissionIdsByUserId(@Param("userId") Long userId);

    /**
     * The active grants of one account with their permission rows loaded in the
     * same query. {@code findByUserIdAndIsActiveTrue} leaves {@code permission}
     * a lazy proxy: walking it cost one select per row, and handing the proxies
     * to Jackson failed the whole response on the proxy's
     * {@code hibernateLazyInitializer} (GET /ums/users/{id}/permissions).
     */
    @Query("SELECT up FROM UmsUserPermission up JOIN FETCH up.permission "
            + "WHERE up.user.id = :userId AND up.isActive = true")
    List<UmsUserPermission> findActiveWithPermission(@Param("userId") Long userId);

    /** R-14: active and not lapsed. */
    @Query("SELECT CASE WHEN COUNT(up) > 0 THEN true ELSE false END FROM UmsUserPermission up "
            + "WHERE up.user.id = :userId AND up.permission.id = :permissionId AND up.isActive = true "
            + "AND (up.expiresAt IS NULL OR up.expiresAt > CURRENT_TIMESTAMP)")
    boolean existsEffective(@Param("userId") Long userId, @Param("permissionId") Long permissionId);

    /** R-14: every live exception across accounts, for the review job and the report. */
    @Query("SELECT up FROM UmsUserPermission up JOIN FETCH up.permission JOIN FETCH up.user u "
            + "WHERE up.isActive = true AND u.isDeleted = false ORDER BY up.expiresAt ASC, u.userName ASC")
    List<UmsUserPermission> findAllActive();
}
