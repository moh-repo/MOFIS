package com.dilaw.mohe.ums.repository;

import com.dilaw.mohe.ums.model.UmsUserSession;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface UmsUserSessionRepository
        extends JpaRepository<UmsUserSession, Long>, JpaSpecificationExecutor<UmsUserSession> {

    List<UmsUserSession> findByIsActiveTrue();

    @Query("SELECT s FROM UmsUserSession s WHERE s.user.id = :userId AND s.isActive = true")
    List<UmsUserSession> findByUserIdAndIsActiveTrue(@Param("userId") Long userId);

    @Query("SELECT s FROM UmsUserSession s WHERE s.user.id = :userId AND s.tokenHash = :jti "
            + "AND s.isActive = true AND s.expiresAt > :now")
    Optional<UmsUserSession> findActiveByUserIdAndJti(
            @Param("userId") Long userId,
            @Param("jti") String jti,
            @Param("now") LocalDateTime now);

    /**
     * The session behind a token if it may still authenticate: open, unexpired, and on an
     * account that may hold one ({@code status}, never deleted). The JWT filter's read on
     * every request (R6-03).
     */
    @Query("SELECT s FROM UmsUserSession s JOIN s.user u WHERE u.id = :userId AND s.tokenHash = :jti "
            + "AND s.isActive = true AND s.expiresAt > :now "
            + "AND u.status = :status AND (u.isDeleted IS NULL OR u.isDeleted = false)")
    Optional<UmsUserSession> findLiveByUserIdAndJti(
            @Param("userId") Long userId,
            @Param("jti") String jti,
            @Param("now") LocalDateTime now,
            @Param("status") String status);

    @Query("SELECT CASE WHEN COUNT(s) > 0 THEN true ELSE false END FROM UmsUserSession s "
            + "WHERE s.user.id = :userId AND s.isActive = true AND s.expiresAt > :now")
    boolean hasActiveSession(@Param("userId") Long userId, @Param("now") LocalDateTime now);

    Page<UmsUserSession> findByIsActiveTrue(Pageable pageable);

    @Query("SELECT s FROM UmsUserSession s JOIN s.user u WHERE "
            + "(:keyword IS NULL OR LOWER(u.userName) LIKE LOWER(CONCAT('%', :keyword, '%')) OR "
            + "LOWER(COALESCE(u.email, '')) LIKE LOWER(CONCAT('%', :keyword, '%')) OR "
            + "LOWER(COALESCE(s.ipAddress, '')) LIKE LOWER(CONCAT('%', :keyword, '%')) OR "
            + "LOWER(COALESCE(s.deviceInfo, '')) LIKE LOWER(CONCAT('%', :keyword, '%')) OR "
            + "LOWER(COALESCE(s.userAgent, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))) AND "
            + "(:active IS NULL OR s.isActive = :active)")
    Page<UmsUserSession> listSessions(
            @Param("keyword") String keyword,
            @Param("active") Boolean active,
            Pageable pageable);

    @Query("DELETE FROM UmsUserSession s WHERE s.user.id = :userId")
    void deleteByUserId(@Param("userId") Long userId);
}
