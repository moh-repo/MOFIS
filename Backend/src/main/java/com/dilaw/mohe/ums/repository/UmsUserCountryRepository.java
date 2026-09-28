package com.dilaw.mohe.ums.repository;

import com.dilaw.mohe.ums.model.UmsUserCountry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UmsUserCountryRepository extends JpaRepository<UmsUserCountry, Long> {

    Optional<UmsUserCountry> findByUserIdAndCountryId(Long userId, Integer countryId);

    List<UmsUserCountry> findByUserIdAndIsActiveTrue(Long userId);

    List<UmsUserCountry> findByCountryIdAndIsActiveTrue(Integer countryId);

    boolean existsByUserIdAndCountryIdAndIsActiveTrue(Long userId, Integer countryId);

    void deleteByUserIdAndCountryId(Long userId, Integer countryId);

    void deleteByUserId(Long userId);

    @Query("SELECT DISTINCT uc.countryId FROM UmsUserCountry uc WHERE uc.isActive = true")
    List<Integer> findDistinctActiveCountryIds();

    /**
     * Active assignments for a whole page of users at once.
     *
     * <p>One query for the page rather than one per row: the staff list shows each
     * account's attachés, and doing that per user is the N+1 that turns a 15-row
     * page into 16 round trips.
     */
    @Query("SELECT uc FROM UmsUserCountry uc WHERE uc.isActive = true AND uc.user.id IN :userIds")
    List<UmsUserCountry> findActiveForUsers(@org.springframework.data.repository.query.Param("userIds") List<Long> userIds);
}
