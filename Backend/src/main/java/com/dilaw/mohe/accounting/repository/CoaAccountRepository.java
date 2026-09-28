package com.dilaw.mohe.accounting.repository;

import com.dilaw.mohe.accounting.model.CoaAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CoaAccountRepository extends JpaRepository<CoaAccount, Integer>,
        JpaSpecificationExecutor<CoaAccount> {

    List<CoaAccount> findByFiscalYearIdOrderByFullCodeAsc(Integer fiscalYearId);

    List<CoaAccount> findBySegmentIdAndFiscalYearIdOrderByFullCodeAsc(Integer segmentId, Integer fiscalYearId);

    List<CoaAccount> findByParentAccountId(Integer parentAccountId);

    Optional<CoaAccount> findBySegmentIdAndFiscalYearIdAndFullCode(
            Integer segmentId, Integer fiscalYearId, String fullCode);

    boolean existsByParentAccountId(Integer parentAccountId);

    /** Leaves that may actually receive postings — what the ledger round will offer. */
    @Query("""
        SELECT a FROM CoaAccount a
        WHERE a.fiscalYearId = :fiscalYearId
          AND a.postable = true
          AND a.active = true
        ORDER BY a.fullCode
        """)
    List<CoaAccount> findPostable(@Param("fiscalYearId") Integer fiscalYearId);
}
