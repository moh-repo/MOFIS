package com.dilaw.mohe.repository;

import com.dilaw.mohe.model.MoheQuarter;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MoheQuarterRepository extends JpaRepository<MoheQuarter, Integer> {

    /** Returns the most recent open quarter (not yet closed) — used by ministry users. */
    Optional<MoheQuarter> findTopByQuarterClosedFalseOrderByQuarterYearDescQuarterNumberDesc();

    /** Country-scoped list: rows whose countryId is in the allowed set OR is null (global). */
    List<MoheQuarter> findByCountryIdInOrCountryIdIsNull(List<Integer> allowedIds);

    /** Country-scoped current quarter (most recent open, in-scope or global). */
    @Query("SELECT q FROM MoheQuarter q WHERE q.quarterClosed = false "
         + "AND (q.countryId IN :allowedIds OR q.countryId IS NULL) "
         + "ORDER BY q.quarterYear DESC, q.quarterNumber DESC")
    List<MoheQuarter> findOpenInScope(@Param("allowedIds") List<Integer> allowedIds);

    /** Validate that no other open quarter has a higher YYYYQ value than the one being created. */
    @Query("SELECT MAX(q.quarterYear * 10 + q.quarterNumber) FROM MoheQuarter q")
    Integer findMaxYearQuarter();
}
