package com.dilaw.mohe.ums.repository;

import com.dilaw.mohe.ums.model.UmsApiLink;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UmsApiLinkRepository extends JpaRepository<UmsApiLink, Long> {

    List<UmsApiLink> findByIsActiveTrue();

    Page<UmsApiLink> findByIsActiveTrue(Pageable pageable);

    @Query("SELECT a FROM UmsApiLink a WHERE a.isActive = true AND "
            + "(:keyword IS NULL OR LOWER(a.resourceCode) LIKE LOWER(CONCAT('%', :keyword, '%')) OR "
            + "LOWER(a.resourceName) LIKE LOWER(CONCAT('%', :keyword, '%')) OR "
            + "LOWER(COALESCE(a.urlPattern, '')) LIKE LOWER(CONCAT('%', :keyword, '%')) OR "
            + "LOWER(a.method) LIKE LOWER(CONCAT('%', :keyword, '%'))) AND "
            + "(:method IS NULL OR UPPER(a.method) = UPPER(:method))")
    Page<UmsApiLink> listApiLinks(
            @Param("keyword") String keyword,
            @Param("method") String method,
            Pageable pageable);
}
