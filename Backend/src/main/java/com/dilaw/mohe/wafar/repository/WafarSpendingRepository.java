package com.dilaw.mohe.wafar.repository;

import com.dilaw.mohe.wafar.model.WafarSpending;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

@Repository
public interface WafarSpendingRepository extends JpaRepository<WafarSpending, Integer> {

    List<WafarSpending> findByStatus(String status);

    List<WafarSpending> findByStudentId(Integer studentId);

    /** The active (non-cancelled) spending linked to a travel ticket, if any. */
    List<WafarSpending> findByTravelTicketIdAndStatusNot(Integer travelTicketId, String status);

    /*
     * The scoped queries below all take the same two parameters:
     *
     *   unscoped   true for a ministry user, and then countryIds is ignored
     *   countryIds the caller's attachés; must never be empty, because SQL Server
     *              rejects IN (). A caller with no scope has nothing to ask for
     *              and should not reach here.
     *
     * A row whose country is NULL matches neither, so it is ministry-only. That is
     * the intended reading: on this table a NULL is missing data, not "everyone's".
     */

    @Query("""
        SELECT COALESCE(SUM(s.amount), 0) FROM WafarSpending s
        WHERE s.status = :status
          AND (:unscoped = TRUE OR s.studyCountryInId IN :countryIds)
        """)
    BigDecimal sumByStatus(@Param("status") String status, @Param("unscoped") boolean unscoped,
                                 @Param("countryIds") Collection<Integer> countryIds);

    @Query("""
        SELECT COUNT(s) FROM WafarSpending s
        WHERE s.status IN :statuses
          AND (:unscoped = TRUE OR s.studyCountryInId IN :countryIds)
        """)
    long countAll(@Param("statuses") Collection<String> statuses,
                  @Param("unscoped") boolean unscoped,
                  @Param("countryIds") Collection<Integer> countryIds);

    @Query("""
        SELECT s FROM WafarSpending s
        WHERE (:categoryId IS NULL OR s.category.id = :categoryId)
          AND (:status IS NULL OR s.status = :status)
          AND (:studentId IS NULL OR s.studentId = :studentId)
          AND (:dateFrom IS NULL OR s.spendingDate >= :dateFrom)
          AND (:dateTo IS NULL OR s.spendingDate <= :dateTo)
          AND (:unscoped = TRUE OR s.studyCountryInId IN :countryIds)
        ORDER BY s.createdAt DESC
        """)
    List<WafarSpending> findFiltered(
            @Param("categoryId") Integer categoryId,
            @Param("status") String status,
            @Param("studentId") Integer studentId,
            @Param("dateFrom") LocalDate dateFrom,
            @Param("dateTo") LocalDate dateTo,
            @Param("unscoped") boolean unscoped,
            @Param("countryIds") Collection<Integer> countryIds);
}
