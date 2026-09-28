package com.dilaw.mohe.repository;

import com.dilaw.mohe.model.FinReceivedReason;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FinReceivedReasonRepository extends JpaRepository<FinReceivedReason, Integer> {

    List<FinReceivedReason> findAllByActiveTrueOrderBySortOrderAsc();
}
