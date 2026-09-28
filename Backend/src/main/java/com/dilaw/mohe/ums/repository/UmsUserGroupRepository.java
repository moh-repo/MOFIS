package com.dilaw.mohe.ums.repository;

import com.dilaw.mohe.ums.model.UmsUserGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UmsUserGroupRepository extends JpaRepository<UmsUserGroup, Long> {

    Optional<UmsUserGroup> findByUserIdAndGroupId(Long userId, Long groupId);

    List<UmsUserGroup> findByUserIdAndIsActiveTrue(Long userId);

    List<UmsUserGroup> findByGroupIdAndIsActiveTrue(Long groupId);

    boolean existsByUserIdAndGroupIdAndIsActiveTrue(Long userId, Long groupId);

    void deleteByUserIdAndGroupId(Long userId, Long groupId);
}
