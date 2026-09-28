package com.dilaw.mohe.studentportal.repository;

import com.dilaw.mohe.studentportal.model.StudentRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface StudentRoleRepository extends JpaRepository<StudentRole, Integer> {
    Optional<StudentRole> findByRoleName(String roleName);
}
