package com.dilaw.mohe.repository;

import com.dilaw.mohe.model.StudentFinanceArch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface StudentFinanceArchRepository extends JpaRepository<StudentFinanceArch, Long>, JpaSpecificationExecutor<StudentFinanceArch> {

    java.util.List<StudentFinanceArch> findByStudentIdOrderByFinanceArchEntryIdAsc(Integer studentId);

    /**
     * The archive row already written for one student quarter, if any.
     *
     * <p>Unique by V038 ({@code UX_STUDENT_FINANCE_ARCH_STUDENT_QUARTER}), so an
     * {@link java.util.Optional} is safe. Quarter create and quarter close both go
     * through this so a snapshot is refreshed in place rather than inserted twice —
     * the second insert is exactly what that index rejects.
     */
    java.util.Optional<StudentFinanceArch> findByStudentIdAndStudentFinRecievedYearAndStudentFinRecievedMonth(
            Integer studentId, Integer studentFinRecievedYear, Integer studentFinRecievedMonth);

    /** Candidates for a page of finance rows at once (quarter create, review Q4); matched on month in memory. */
    java.util.List<StudentFinanceArch> findByStudentIdInAndStudentFinRecievedYearIn(
            java.util.Collection<Integer> studentIds, java.util.Collection<Integer> studentFinRecievedYears);

    /**
     * Per-student totals across archived finance rows, for the archive list's
     * Total Received / Total Wafar / Total Net columns.
     *
     * Rows are {@code [studentId, totalReceived, totalWafar, totalNet]}. Called once per
     * page with the page's student ids rather than once per row, so the list stays a
     * two-query screen.
     *
     * <p>Deleted students are not excluded, here or in {@link #sumByQuarter}: the archive is
     * the record of what was paid and is kept whole (decision of 26 Sep 2026, FIX-PLAN P5-11).
     */
    @Query(value = """
            SELECT STUDENT_ID,
                   SUM(ISNULL(STUDENT_FIN_RECIEVED_AMOUNT, 0)),
                   SUM(ISNULL(WAFAR_AMOUNT, 0)),
                   SUM(ISNULL(NET_AMOUNT, 0))
              FROM MOHECORE.STUDENT_FINANCE_ARCH
             WHERE STUDENT_ID IN (:studentIds)
             GROUP BY STUDENT_ID
            """, nativeQuery = true)
    java.util.List<Object[]> sumFinanceTotalsByStudentIds(@Param("studentIds") java.util.Collection<Integer> studentIds);

    /**
     * Per-quarter totals for the archive, in the same shape as
     * {@link StudentFinanceRepository#sumByQuarter}:
     * {@code [year, quarter, received, wafar, net, studentCount]}.
     *
     * <p>This is where every quarter but the current one gets its figure — the live
     * table is emptied of a quarter when it rolls over, so the archive is the only
     * place the history exists.
     */
    @Query(value = """
            SELECT STUDENT_FIN_RECIEVED_YEAR,
                   STUDENT_FIN_RECIEVED_MONTH,
                   SUM(CAST(ISNULL(STUDENT_FIN_RECIEVED_AMOUNT, 0) AS bigint)),
                   SUM(CAST(ISNULL(WAFAR_AMOUNT, 0) AS bigint)),
                   SUM(CAST(ISNULL(NET_AMOUNT, 0) AS bigint)),
                   COUNT(*)
              FROM MOHECORE.STUDENT_FINANCE_ARCH
             WHERE STUDENT_FIN_RECIEVED_YEAR IS NOT NULL
               AND STUDENT_FIN_RECIEVED_MONTH IS NOT NULL
               AND (:unscoped = 1 OR STUDY_COUNTRY_IN_ID IN (:countryIds))
             GROUP BY STUDENT_FIN_RECIEVED_YEAR, STUDENT_FIN_RECIEVED_MONTH
            """, nativeQuery = true)
    java.util.List<Object[]> sumByQuarter(@Param("unscoped") int unscoped,
                                          @Param("countryIds") java.util.Collection<Integer> countryIds);
}
