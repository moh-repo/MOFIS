package com.dilaw.mohe.accounting.repository;

import com.dilaw.mohe.accounting.model.PaymentBatch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PaymentBatchRepository extends JpaRepository<PaymentBatch, Integer> {

    List<PaymentBatch> findByMoheQuarterKeyAndStudyCountryInIdOrderByBatchNoAsc(
            Integer moheQuarterKey, Integer studyCountryInId);

    /** Every attaché's batches for a quarter, for the ministry view; scoped afterwards by the service. */
    List<PaymentBatch> findByMoheQuarterKeyOrderByStudyCountryInIdAscBatchNoAsc(Integer moheQuarterKey);

    /** The highest DOF number so far for a quarter and attaché; the next is this plus one. */
    @Query("""
        SELECT COALESCE(MAX(b.batchNo), 0) FROM PaymentBatch b
        WHERE b.moheQuarterKey = :quarterKey AND b.studyCountryInId = :countryId
        """)
    Short highestBatchNo(@Param("quarterKey") Integer quarterKey, @Param("countryId") Integer countryId);
}
