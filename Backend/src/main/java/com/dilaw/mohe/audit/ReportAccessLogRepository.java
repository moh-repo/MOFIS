package com.dilaw.mohe.audit;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ReportAccessLogRepository extends JpaRepository<ReportAccessLog, Long> {
}
