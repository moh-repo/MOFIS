package com.dilaw.mohe.ums.repository;

import com.dilaw.mohe.ums.model.UmsPageSection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UmsPageSectionRepository extends JpaRepository<UmsPageSection, Long> {

    Optional<UmsPageSection> findByParentResourceIdAndResourceCode(Long parentResourceId, String resourceCode);

    List<UmsPageSection> findByParentResourceIdAndIsActiveTrueOrderByDisplayOrder(Long parentResourceId);

    List<UmsPageSection> findByIsActiveTrue();
}
