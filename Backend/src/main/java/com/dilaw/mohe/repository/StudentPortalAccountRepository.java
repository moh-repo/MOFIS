package com.dilaw.mohe.repository;

import com.dilaw.mohe.model.StudentPortalAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface StudentPortalAccountRepository extends JpaRepository<StudentPortalAccount, Integer> {

    Optional<StudentPortalAccount> findByLoginKeyAndIsActiveTrue(String loginKey);

    Optional<StudentPortalAccount> findByLoginKey(String loginKey);

    boolean existsByLoginKey(String loginKey);

    Optional<StudentPortalAccount> findByStudentId(Integer studentId);
}
