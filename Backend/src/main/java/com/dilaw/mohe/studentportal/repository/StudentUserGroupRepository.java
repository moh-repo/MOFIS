package com.dilaw.mohe.studentportal.repository;

import com.dilaw.mohe.studentportal.model.StudentUserGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public interface StudentUserGroupRepository extends JpaRepository<StudentUserGroup, Integer> {
    List<StudentUserGroup> findByStudentUserId(Integer studentUserId);
    boolean existsByStudentUserIdAndGroupId(Integer studentUserId, Integer groupId);

    @Modifying
    @Transactional
    @Query("DELETE FROM StudentUserGroup g WHERE g.studentUserId = :userId AND g.groupId = :groupId")
    void deleteByStudentUserIdAndGroupId(@Param("userId") Integer userId, @Param("groupId") Integer groupId);
}
