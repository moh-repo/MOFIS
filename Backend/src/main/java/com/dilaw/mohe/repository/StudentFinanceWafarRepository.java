package com.dilaw.mohe.repository;

import com.dilaw.mohe.model.StudentFinanceWafar;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;

@Repository
public interface StudentFinanceWafarRepository extends JpaRepository<StudentFinanceWafar, Integer>,
        JpaSpecificationExecutor<StudentFinanceWafar> {

    /**
     * Wafar deposited at the caller's attachés.
     *
     * <p>Wafar stays where it was saved — Cairo's savings pay only Cairo's
     * students — so a ministry-wide total is the wrong number for anyone but the
     * ministry. All 23 rows carry a country, so nothing is lost to a NULL here.
     *
     * @param unscoped   true for a ministry user; {@code countryIds} is then ignored
     * @param countryIds never empty, because SQL Server rejects {@code IN ()}
     */
    @Query("""
            SELECT COALESCE(SUM(w.studentFinRecievedAmount), 0) FROM StudentFinanceWafar w
            WHERE (:unscoped = TRUE OR w.studyCountryInId IN :countryIds)
            """)
    BigDecimal sumTotalDeposits(@Param("unscoped") boolean unscoped,
                                @Param("countryIds") Collection<Integer> countryIds);
}
