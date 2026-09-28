package com.dilaw.mohe.ums.repository;

import com.dilaw.mohe.ums.model.UmsReport;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UmsReportRepository extends JpaRepository<UmsReport, Long> {

    Optional<UmsReport> findByReportCode(String reportCode);

    boolean existsByReportCode(String reportCode);

    List<UmsReport> findByIsActiveTrue();

    List<UmsReport> findByIsActiveTrueOrderBySortOrderAscIdAsc();

    List<UmsReport> findByCategoryAndIsActiveTrue(String category);

    Page<UmsReport> findByIsActiveTrue(Pageable pageable);

    @Query("SELECT r FROM UmsReport r WHERE r.isActive = true AND "
            + "(:keyword IS NULL OR LOWER(r.reportCode) LIKE LOWER(CONCAT('%', :keyword, '%')) OR "
            + "LOWER(r.reportName) LIKE LOWER(CONCAT('%', :keyword, '%')) OR "
            + "LOWER(COALESCE(r.reportNameAr, '')) LIKE LOWER(CONCAT('%', :keyword, '%')) OR "
            + "LOWER(COALESCE(r.description, '')) LIKE LOWER(CONCAT('%', :keyword, '%')) OR "
            + "LOWER(COALESCE(r.category, '')) LIKE LOWER(CONCAT('%', :keyword, '%')) OR "
            + "LOWER(COALESCE(r.route, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))) AND "
            + "(:category IS NULL OR r.category = :category)")
    Page<UmsReport> listReports(
            @Param("keyword") String keyword,
            @Param("category") String category,
            Pageable pageable);

    @Query("SELECT DISTINCT rpt FROM UmsReport rpt " +
            "JOIN UmsPermission p ON p.resourceType = 'REPORT' AND p.resource.id = rpt.id " +
            "JOIN UmsRolePermission rp ON rp.permission.id = p.id " +
            "JOIN UmsRole r ON r.id = rp.role.id " +
            "LEFT JOIN UmsGroupRole gr ON gr.role.id = r.id " +
            "LEFT JOIN UmsGroup g ON g.id = gr.group.id " +
            "LEFT JOIN UmsUserGroup ug ON ug.group.id = g.id " +
            "LEFT JOIN UmsUserRole ur ON ur.role.id = r.id " +
            "WHERE rpt.isActive = true AND p.actionType = 'VIEW' AND p.isActive = true " +
            "AND rp.isActive = true AND r.isActive = true " +
            "AND ((ug.user.id = :userId AND ug.isActive = true AND gr.isActive = true) " +
            "OR (ur.user.id = :userId AND ur.isActive = true))")
    List<UmsReport> findAccessibleReportsByUserId(@Param("userId") Long userId);
}
