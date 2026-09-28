package com.dilaw.mohe.ums.repository;

import com.dilaw.mohe.ums.model.UmsGroup;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UmsGroupRepository extends JpaRepository<UmsGroup, Long> {

    Optional<UmsGroup> findByGroupName(String groupName);

    boolean existsByGroupName(String groupName);

    List<UmsGroup> findByIsActiveTrue();

        Page<UmsGroup> findByIsActiveTrue(Pageable pageable);

        @Query("SELECT g FROM UmsGroup g WHERE g.isActive = true AND "
                        + "(:keyword IS NULL OR LOWER(g.groupName) LIKE LOWER(CONCAT('%', :keyword, '%')) OR "
                        + "LOWER(COALESCE(g.groupNameAr, '')) LIKE LOWER(CONCAT('%', :keyword, '%')) OR "
                        + "LOWER(COALESCE(g.description, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))) AND "
                        + "(:system IS NULL OR g.isSystem = :system)")
        Page<UmsGroup> listGroups(
                        @Param("keyword") String keyword,
                        @Param("system") Boolean system,
                        Pageable pageable);

        @Query("SELECT g FROM UmsGroup g WHERE g.isActive = true AND " +
            "(LOWER(g.groupName) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
            "LOWER(g.groupNameAr) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
            "LOWER(g.description) LIKE LOWER(CONCAT('%', :keyword, '%')))")
        Page<UmsGroup> searchGroups(@Param("keyword") String keyword, Pageable pageable);

    @Query("SELECT DISTINCT g FROM UmsGroup g " +
            "LEFT JOIN g.userGroups ug " +
            "WHERE g.isActive = true AND ug.user.id = :userId AND ug.isActive = true")
    List<UmsGroup> findGroupsByUserId(@Param("userId") Long userId);
}
