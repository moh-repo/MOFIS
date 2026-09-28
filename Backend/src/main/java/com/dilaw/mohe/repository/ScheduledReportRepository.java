package com.dilaw.mohe.repository;

import com.dilaw.mohe.model.ScheduledReport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ScheduledReportRepository extends JpaRepository<ScheduledReport, Long> {
    
    @Query("SELECT s FROM ScheduledReport s WHERE s.scheduleStatus = :status")
    List<ScheduledReport> findByScheduleStatus(@Param("status") String status);
    
    @Query("SELECT s FROM ScheduledReport s WHERE s.isEnabled = 1")
    List<ScheduledReport> findEnabledSchedules();
    
    @Query("SELECT s FROM ScheduledReport s WHERE s.frequency = :frequency")
    List<ScheduledReport> findByFrequency(@Param("frequency") String frequency);
    
    @Query("SELECT s FROM ScheduledReport s ORDER BY s.nextRunDate ASC")
    List<ScheduledReport> findAllOrderedByNextRunDate();
}

