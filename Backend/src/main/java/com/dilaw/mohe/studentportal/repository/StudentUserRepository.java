package com.dilaw.mohe.studentportal.repository;

import com.dilaw.mohe.studentportal.model.StudentUser;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface StudentUserRepository
        extends JpaRepository<StudentUser, Integer>, JpaSpecificationExecutor<StudentUser> {

    /**
     * Portal login lookups. Unscoped on purpose — they run before a scope exists,
     * and the portal restricts every query to the caller's own student id anyway.
     */
    Optional<StudentUser> findByUsernameAndIsDeletedFalse(String username);

    Optional<StudentUser> findByStudentIdAndIsDeletedFalse(Integer studentId);

    boolean existsByStudentId(Integer studentId);

    // The four admin list finders that used to live here are gone. They took no
    // country and there was no way to pass one, so any caller reaching for them
    // by name got an unscoped list of every attaché's students. The admin list is
    // built from specifications in StudentPortalAdminService instead, where the
    // scope is composed in one place and cannot be forgotten.
}
