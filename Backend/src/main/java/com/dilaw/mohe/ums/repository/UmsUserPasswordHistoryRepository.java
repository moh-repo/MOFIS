package com.dilaw.mohe.ums.repository;

import com.dilaw.mohe.ums.model.UmsUserPasswordHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UmsUserPasswordHistoryRepository extends JpaRepository<UmsUserPasswordHistory, Long> {

    /** Newest first; the caller keeps the first {@code n} and deletes the rest. */
    List<UmsUserPasswordHistory> findByUserIdOrderByChangedAtDesc(Long userId);
}
