package com.dilaw.mohe.ums.repository;

import com.dilaw.mohe.ums.model.UmsAccessReview;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface UmsAccessReviewRepository extends JpaRepository<UmsAccessReview, Long> {
    Optional<UmsAccessReview> findByPeriodAndCountryId(String period, Integer countryId);
    Optional<UmsAccessReview> findByPeriodAndCountryIdIsNull(String period);
    List<UmsAccessReview> findAllByOrderByOpenedAtDescCountryIdAsc();
    List<UmsAccessReview> findByCountryIdInOrderByOpenedAtDesc(Collection<Integer> countryIds);
    List<UmsAccessReview> findByStatus(String status);
}
