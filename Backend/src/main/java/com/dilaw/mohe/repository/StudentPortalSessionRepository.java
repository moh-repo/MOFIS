package com.dilaw.mohe.repository;

import com.dilaw.mohe.model.StudentPortalSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

@Repository
public interface StudentPortalSessionRepository extends JpaRepository<StudentPortalSession, Integer> {

    Optional<StudentPortalSession> findByTokenAndExpiresAtAfter(String token, Instant now);

    void deleteByStudentId(Integer studentId);

    @Modifying
    @Query("DELETE FROM StudentPortalSession s WHERE s.expiresAt < :now")
    void deleteExpiredSessions(@Param("now") Instant now);
}
