package com.dilaw.mohe.ums.repository;

import com.dilaw.mohe.ums.model.UmsPage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UmsPageRepository extends JpaRepository<UmsPage, Long> {

        Optional<UmsPage> findByResourceCode(String resourceCode);

        Optional<UmsPage> findByRoute(String route);

        boolean existsByResourceCode(String resourceCode);

        List<UmsPage> findByIsActiveTrueOrderByDisplayOrder();

        List<UmsPage> findByIsActiveTrueAndIsVisibleTrueOrderByDisplayOrder();

        List<UmsPage> findByParentResourceIsNullAndIsActiveTrueOrderByDisplayOrder();

        List<UmsPage> findByParentResourceIdAndIsActiveTrueOrderByDisplayOrder(Long parentResourceId);

        Page<UmsPage> findByIsActiveTrue(Pageable pageable);

        @Query("SELECT p FROM UmsPage p WHERE p.isActive = true AND "
                        + "(:keyword IS NULL OR LOWER(p.resourceCode) LIKE LOWER(CONCAT('%', :keyword, '%')) OR "
                        + "LOWER(p.resourceName) LIKE LOWER(CONCAT('%', :keyword, '%')) OR "
                        + "LOWER(COALESCE(p.resourceNameAr, '')) LIKE LOWER(CONCAT('%', :keyword, '%')) OR "
                        + "LOWER(COALESCE(p.route, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))) AND "
                        + "(:visible IS NULL OR p.isVisible = :visible)")
        Page<UmsPage> listPages(
                        @Param("keyword") String keyword,
                        @Param("visible") Boolean visible,
                        Pageable pageable);

        @Query("SELECT p FROM UmsPage p WHERE p.isActive = true AND " +
                        "(LOWER(p.resourceCode) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
                        "LOWER(p.resourceName) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
                        "LOWER(p.resourceNameAr) LIKE LOWER(CONCAT('%', :keyword, '%')))")
        Page<UmsPage> searchPages(@Param("keyword") String keyword, Pageable pageable);

        // Get accessible pages for a user based on VIEW permissions
        @Query("SELECT DISTINCT pg FROM UmsPage pg " +
                        "JOIN UmsPermission p ON p.resourceType = 'PAGE' AND p.resource.id = pg.id " +
                        "JOIN UmsRolePermission rp ON rp.permission.id = p.id " +
                        "JOIN UmsRole r ON r.id = rp.role.id " +
                        "LEFT JOIN UmsGroupRole gr ON gr.role.id = r.id " +
                        "LEFT JOIN UmsGroup g ON g.id = gr.group.id " +
                        "LEFT JOIN UmsUserGroup ug ON ug.group.id = g.id " +
                        "LEFT JOIN UmsUserRole ur ON ur.role.id = r.id " +
                        "WHERE pg.isActive = true AND p.actionType = 'VIEW' AND p.isActive = true " +
                        "AND rp.isActive = true AND r.isActive = true " +
                        "AND ((ug.user.id = :userId AND ug.isActive = true AND gr.isActive = true) " +
                        "OR (ur.user.id = :userId AND ur.isActive = true)) " +
                        "ORDER BY pg.displayOrder")
        List<UmsPage> findAccessiblePagesByUserId(@Param("userId") Long userId);
}
