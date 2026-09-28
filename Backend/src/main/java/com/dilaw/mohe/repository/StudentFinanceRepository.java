package com.dilaw.mohe.repository;

import com.dilaw.mohe.model.StudentFinance;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StudentFinanceRepository extends JpaRepository<StudentFinance, Integer>,
        JpaSpecificationExecutor<StudentFinance> {

    /**
     * Unpaid finance records for students whose last quarter matches: any status that
     * does not mean money moved. It used to test for {@code NULL} or {@code 0}, which
     * V135 moved onto NOT_SPECIFIED (live QA H2); "paid" is now the set of ids the
     * caller resolves by CODE ({@code StudyFinReceivedStatusService.paidStatusIds}).
     */
    @Query("""
            SELECT sf FROM StudentFinance sf
            JOIN StudentDatum sd ON sd.studentId = sf.id
            WHERE sd.studentLastQuarter = :quarterId
              AND (sd.isDeleted = false OR sd.isDeleted IS NULL)
              AND (sf.studyFinRecievedStatus IS NULL OR sf.studyFinRecievedStatus NOT IN :paidStatusIds)
            """)
    List<StudentFinance> findUnpaidForQuarter(@Param("quarterId") Integer quarterId,
                                              @Param("paidStatusIds") java.util.Collection<Integer> paidStatusIds);
    
    // findBySearchTerm and findByStudyFinRecievedStatus were removed (FIX-PLAN P5-11): no
    // caller, and neither excluded deleted students. The Finance Records list reads through
    // StudentFinanceSpec.liveStudent().

    /**
     * Total received amount over the whole live table, for a ministry-wide quarter close.
     * Summed in the database: the close used to stream every row into memory for this.
     */
    @Query("SELECT COALESCE(SUM(f.studentFinRecievedAmount), 0L) FROM StudentFinance f WHERE " + OF_LIVE_STUDENT)
    Long sumReceivedAmount();

    /**
     * FIN-08: received amounts of one quarter's finance rows (year and quarter number,
     * as STUDENT_FIN_RECIEVED_YEAR / _MONTH hold them), per currency: rows
     * {@code [studyCurrencyInId, amount]}. For the quarter-close budget check, which
     * used to add every quarter and every currency together.
     */
    @Query("SELECT f.studyCurrencyInId, COALESCE(SUM(f.studentFinRecievedAmount), 0L) FROM StudentFinance f "
            + "WHERE f.studentFinRecievedYear = :year AND f.studentFinRecievedMonth = :quarter AND " + OF_LIVE_STUDENT
            + " GROUP BY f.studyCurrencyInId")
    List<Object[]> sumReceivedByCurrencyForQuarter(@Param("year") Integer year, @Param("quarter") Integer quarter);

    /**
     * The finance row {@code f} belongs to a student who is not deleted (live QA H7; FIX-PLAN
     * P5-11: the ministry quarter close summed deleted students too and saved the total as
     * MOHE_QUARTER.PAID_AMOUNT). Stated in the query rather than left to StudentDatum's
     * {@code @Where}, which Hibernate 6.3+ deprecates.
     */
    String OF_LIVE_STUDENT = "EXISTS (SELECT 1 FROM StudentDatum live WHERE live.studentId = f.id "
            + "AND (live.isDeleted = false OR live.isDeleted IS NULL))";

    /**
     * Total received amount for the students studying in {@code countryIds}, for a
     * country-scoped quarter close. Scoped through {@code STUDENT_DATA.STUDY_COUNTRY_IN}
     * (the column {@code StudentDataSpec.countryScope} filters on) rather than the finance
     * row's own {@code STUDY_COUNTRY_IN_ID}, so the sum covers exactly the students the
     * close archives. {@code countryIds} must not be empty (SQL Server rejects {@code IN ()}).
     */
    @Query("""
            SELECT COALESCE(SUM(f.studentFinRecievedAmount), 0L) FROM StudentFinance f
             WHERE f.id IN (SELECT s.studentId FROM StudentDatum s WHERE s.studyCountryIn IN :countryIds
                              AND (s.isDeleted = false OR s.isDeleted IS NULL))
            """)
    Long sumReceivedAmountForStudyCountries(@Param("countryIds") java.util.Collection<Integer> countryIds);

    /**
     * Per-quarter totals for the live finance table, for the MOHE Quarters screen.
     *
     * <p>Rows are {@code [year, quarter, received, wafar, net, studentCount]}. One
     * query for the whole screen rather than one per quarter.
     *
     * <p>{@code unscoped = 1} sums every country; otherwise only {@code countryIds}.
     * A ministry user is summed unscoped rather than by their (complete) country
     * list, so a finance row whose {@code STUDY_COUNTRY_IN_ID} is null or points at
     * a country the lookup has lost is still counted. Silently dropping money is
     * worse than showing it against no country.
     *
     * <p>{@code countryIds} must never be empty — SQL Server rejects {@code IN ()}.
     * Callers with no scope have nothing to show and should not reach here.
     */
    @Query(value = """
            SELECT STUDENT_FIN_RECIEVED_YEAR,
                   STUDENT_FIN_RECIEVED_MONTH,
                   SUM(CAST(ISNULL(STUDENT_FIN_RECIEVED_AMOUNT, 0) AS bigint)),
                   SUM(CAST(ISNULL(WAFAR_AMOUNT, 0) AS bigint)),
                   SUM(CAST(ISNULL(NET_AMOUNT, 0) AS bigint)),
                   COUNT(*)
              FROM MOHECORE.STUDENT_FINANCE sf
             WHERE STUDENT_FIN_RECIEVED_YEAR IS NOT NULL
               AND STUDENT_FIN_RECIEVED_MONTH IS NOT NULL
               AND EXISTS (SELECT 1 FROM MOHECORE.STUDENT_DATA live_sd
                           WHERE live_sd.STUDENT_ID = sf.STUDENT_ID AND ISNULL(live_sd.IS_DELETED, 0) = 0)
               AND (:unscoped = 1 OR STUDY_COUNTRY_IN_ID IN (:countryIds))
             GROUP BY STUDENT_FIN_RECIEVED_YEAR, STUDENT_FIN_RECIEVED_MONTH
            """, nativeQuery = true)
    List<Object[]> sumByQuarter(@Param("unscoped") int unscoped,
                                @Param("countryIds") java.util.Collection<Integer> countryIds);
}
