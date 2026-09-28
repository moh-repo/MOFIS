package com.dilaw.mohe.studentportal.repository;

import com.dilaw.mohe.studentportal.StudentSessionDto;
import com.dilaw.mohe.studentportal.model.StudentSession;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface StudentSessionRepository extends JpaRepository<StudentSession, Integer> {

    // Valid session: not expired and not explicitly logged out
    Optional<StudentSession> findByTokenAndExpiresAtAfterAndLoggedOutAtIsNull(String token, Instant now);

    Optional<StudentSession> findByToken(String token);

    // Admin: active sessions only (not expired, not logged out)
    @Query("SELECT s FROM StudentSession s WHERE s.expiresAt > :now AND s.loggedOutAt IS NULL ORDER BY s.createdAt DESC")
    Page<StudentSession> findAllActive(@Param("now") Instant now, Pageable pageable);

    // Admin: full history (all sessions, newest first)
    @Query("SELECT s FROM StudentSession s ORDER BY s.createdAt DESC")
    Page<StudentSession> findAllOrderByCreatedAtDesc(Pageable pageable);

    // ── DTO queries with student name joined from MOHECORE ──────────────────
    //
    // Each is scoped by the country pinned on the session at login (V036).
    // `:unscoped` is the ministry bypass; a null pinned country never matches an
    // IN list, so a pre-V036 session stays ministry-only — the missing-data rule,
    // since the country is genuinely unknown rather than deliberately global.
    // `:countryIds` must never be empty: SQL Server rejects `IN ()`.

    @Query("SELECT new com.dilaw.mohe.studentportal.StudentSessionDto(" +
           "s.sessionId, s.studentUserId, s.studentId, sd.studentName, s.token, s.expiresAt, s.createdAt, s.loggedOutAt) " +
           "FROM StudentSession s LEFT JOIN StudentDatum sd ON sd.studentId = s.studentId " +
           "WHERE (:unscoped = TRUE OR s.studyCountryInId IN :countryIds) " +
           "ORDER BY s.createdAt DESC")
    Page<StudentSessionDto> findAllDto(
            @Param("unscoped") boolean unscoped,
            @Param("countryIds") List<Integer> countryIds,
            Pageable pageable);

    @Query("SELECT new com.dilaw.mohe.studentportal.StudentSessionDto(" +
           "s.sessionId, s.studentUserId, s.studentId, sd.studentName, s.token, s.expiresAt, s.createdAt, s.loggedOutAt) " +
           "FROM StudentSession s LEFT JOIN StudentDatum sd ON sd.studentId = s.studentId " +
           "WHERE s.expiresAt > :now AND s.loggedOutAt IS NULL " +
           "AND (:unscoped = TRUE OR s.studyCountryInId IN :countryIds) " +
           "ORDER BY s.createdAt DESC")
    Page<StudentSessionDto> findAllActiveDto(
            @Param("now") Instant now,
            @Param("unscoped") boolean unscoped,
            @Param("countryIds") List<Integer> countryIds,
            Pageable pageable);

    @Query("SELECT new com.dilaw.mohe.studentportal.StudentSessionDto(" +
           "s.sessionId, s.studentUserId, s.studentId, sd.studentName, s.token, s.expiresAt, s.createdAt, s.loggedOutAt) " +
           "FROM StudentSession s LEFT JOIN StudentDatum sd ON sd.studentId = s.studentId " +
           "WHERE s.studentUserId = :studentUserId " +
           "AND (:unscoped = TRUE OR s.studyCountryInId IN :countryIds) " +
           "ORDER BY s.createdAt DESC")
    Page<StudentSessionDto> findDtoByStudentUserId(
            @Param("studentUserId") Integer studentUserId,
            @Param("unscoped") boolean unscoped,
            @Param("countryIds") List<Integer> countryIds,
            Pageable pageable);

    // Admin: list sessions for one user
    Page<StudentSession> findByStudentUserIdOrderByCreatedAtDesc(Integer studentUserId, Pageable pageable);

    // Admin: count active sessions per user
    @Query("SELECT COUNT(s) FROM StudentSession s WHERE s.studentUserId = :uid AND s.expiresAt > :now")
    long countActiveByStudentUserId(@Param("uid") Integer uid, @Param("now") Instant now);

    @Modifying
    @Query("DELETE FROM StudentSession s WHERE s.token = :token")
    void deleteByToken(String token);

    @Modifying
    @Query("DELETE FROM StudentSession s WHERE s.expiresAt < :now")
    void deleteExpired(Instant now);

    @Modifying
    @Query("DELETE FROM StudentSession s WHERE s.studentUserId = :studentUserId")
    void deleteByStudentUserId(Integer studentUserId);

    /** Every session of the account except the one holding {@code keepTokenHash}. */
    @Modifying
    @Query("DELETE FROM StudentSession s WHERE s.studentUserId = :studentUserId AND s.token <> :keepTokenHash")
    void deleteOtherSessions(Integer studentUserId, String keepTokenHash);
}
