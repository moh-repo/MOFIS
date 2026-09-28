package com.dilaw.mohe.service;

import com.dilaw.mohe.model.ReportTemplate;
import com.dilaw.mohe.repository.ReportTemplateRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Transactional(readOnly = true)
@Service
public class ReportTemplateService {
    
    @Autowired
    private ReportTemplateRepository repository;
    
    public List<ReportTemplate> getAllTemplates() {
        return repository.findAllOrderedByUsageCount();
    }
    
    public List<ReportTemplate> getTemplatesByReportType(String reportType) {
        return repository.findByReportType(reportType);
    }
    
    public List<ReportTemplate> getPublicTemplates() {
        return repository.findPublicTemplates();
    }
        
    public List<ReportTemplate> getRecentlyUsedTemplates() {
        return repository.findAllOrderedByLastUsedDate();
    }
}

