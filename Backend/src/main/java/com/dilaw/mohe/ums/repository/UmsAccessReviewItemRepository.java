package com.dilaw.mohe.ums.repository;

import com.dilaw.mohe.ums.model.UmsAccessReviewItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface UmsAccessReviewItemRepository extends JpaRepository<UmsAccessReviewItem, Long> {
    List<UmsAccessReviewItem> findByReviewIdOrderByUserId(Long reviewId);
    long countByReviewId(Long reviewId);
    long countByReviewIdAndDecisionIsNull(Long reviewId);

    /** Undecided items of open reviews past their due date and not yet flagged. */
    @Query("SELECT i FROM UmsAccessReviewItem i WHERE i.decision IS NULL AND i.overdueFlaggedAt IS NULL "
            + "AND i.reviewId IN (SELECT r.id FROM UmsAccessReview r WHERE r.status = 'OPEN' AND r.dueAt < :now)")
    List<UmsAccessReviewItem> findOverdue(@Param("now") LocalDateTime now);
}
