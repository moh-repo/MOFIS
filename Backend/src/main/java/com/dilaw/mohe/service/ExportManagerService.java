package com.dilaw.mohe.service;

import com.dilaw.mohe.model.ExportManager;
import com.dilaw.mohe.repository.ExportManagerRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Transactional(readOnly = true)
@Service
public class ExportManagerService {
    
    @Autowired
    private ExportManagerRepository repository;
    
    public List<ExportManager> getAllExports() {
        return repository.findAllOrderedByLastExportDate();
    }
    
    public List<ExportManager> getExportsByStatus(String status) {
        return repository.findByExportStatus(status);
    }
    
    public List<ExportManager> getAvailableExports() {
        return repository.findAvailableExports();
    }
    
    public List<ExportManager> getExportsByFormat(String format) {
        return repository.findByDefaultFormat(format);
    }
}

