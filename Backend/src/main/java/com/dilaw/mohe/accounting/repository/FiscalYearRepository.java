package com.dilaw.mohe.accounting.repository;

import com.dilaw.mohe.accounting.model.FiscalYear;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface FiscalYearRepository extends JpaRepository<FiscalYear, Integer> {

    Optional<FiscalYear> findByYearNo(Integer yearNo);

    List<FiscalYear> findAllByOrderByYearNoAsc();

    /** The year a date falls in. Years cannot overlap, so at most one matches. */
    @Query("""
        SELECT y FROM FiscalYear y
        WHERE y.startDate <= :on AND y.endDate >= :on
        """)
    Optional<FiscalYear> findForDate(@Param("on") LocalDate on);
}
