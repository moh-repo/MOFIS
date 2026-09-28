package com.dilaw.mohe.ums.repository;

import com.dilaw.mohe.ums.model.UmsUserMfaRecoveryCode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UmsUserMfaRecoveryCodeRepository extends JpaRepository<UmsUserMfaRecoveryCode, Long> {
    List<UmsUserMfaRecoveryCode> findByUserId(Long userId);
    long countByUserIdAndUsedAtIsNull(Long userId);
    void deleteByUserId(Long userId);
}
