package com.dilaw.mohe.service;

import com.dilaw.mohe.model.DataVisualization;
import com.dilaw.mohe.repository.DataVisualizationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Transactional(readOnly = true)
@Service
public class DataVisualizationService {
    
    @Autowired
    private DataVisualizationRepository repository;
    
    public List<DataVisualization> getAllVisualizations() {
        return repository.findAllOrderedBySortOrder();
    }
    
    public List<DataVisualization> getVisualizationsByChartType(String chartType) {
        return repository.findByChartType(chartType);
    }
    
    public List<DataVisualization> getVisualizationsByChartName(String chartName) {
        return repository.findByChartName(chartName);
    }
    
    public List<DataVisualization> getVisualizationsByCategory(String category) {
        return repository.findByCategory(category);
    }
    
    public List<String> getAllChartNames() {
        return repository.findAllChartNames();
    }
}

