package com.dilaw.mohe.service;

import com.dilaw.mohe.model.ScheduledReport;
import com.dilaw.mohe.repository.ScheduledReportRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Transactional(readOnly = true)
@Service
public class ScheduledReportService {
    
    @Autowired
    private ScheduledReportRepository repository;
    
    public List<ScheduledReport> getAllScheduledReports() {
        return repository.findAllOrderedByNextRunDate();
    }
    
    public List<ScheduledReport> getScheduledReportsByStatus(String status) {
        return repository.findByScheduleStatus(status);
    }
    
    public List<ScheduledReport> getEnabledSchedules() {
        return repository.findEnabledSchedules();
    }
    
    public List<ScheduledReport> getScheduledReportsByFrequency(String frequency) {
        return repository.findByFrequency(frequency);
    }
}

