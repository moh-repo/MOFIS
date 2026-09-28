package com.dilaw.mohe.studentportal.repository;

import com.dilaw.mohe.studentportal.model.StudentGroupRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public interface StudentGroupRoleRepository extends JpaRepository<StudentGroupRole, Integer> {
    List<StudentGroupRole> findByGroupId(Integer groupId);
    boolean existsByGroupIdAndRoleId(Integer groupId, Integer roleId);

    @Modifying
    @Transactional
    @Query("DELETE FROM StudentGroupRole g WHERE g.groupId = :groupId AND g.roleId = :roleId")
    void deleteByGroupIdAndRoleId(@Param("groupId") Integer groupId, @Param("roleId") Integer roleId);
}
