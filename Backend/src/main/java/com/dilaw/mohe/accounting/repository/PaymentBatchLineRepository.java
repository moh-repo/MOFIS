package com.dilaw.mohe.accounting.repository;

import com.dilaw.mohe.accounting.model.PaymentBatchLine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface PaymentBatchLineRepository extends JpaRepository<PaymentBatchLine, Integer> {

    List<PaymentBatchLine> findByBatchIdOrderByIdAsc(Integer batchId);

    /**
     * Line count and the two sums per batch, in one query for a whole list.
     *
     * <p>Rows are {@code [batchId, count, entitled, disbursed]}.
     *
     * @param batchIds never empty — SQL Server rejects {@code IN ()}
     */
    @Query("""
        SELECT l.batchId, COUNT(l),
               COALESCE(SUM(l.entitledAmount), 0), COALESCE(SUM(l.disbursedAmount), 0)
        FROM PaymentBatchLine l
        WHERE l.batchId IN :batchIds
        GROUP BY l.batchId
        """)
    List<Object[]> totalsByBatch(@Param("batchIds") Collection<Integer> batchIds);
}
