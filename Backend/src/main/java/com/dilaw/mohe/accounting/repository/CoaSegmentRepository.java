package com.dilaw.mohe.accounting.repository;

import com.dilaw.mohe.accounting.model.CoaSegment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CoaSegmentRepository extends JpaRepository<CoaSegment, Integer> {
}
