package com.dilaw.mohe.systemsettings.repository;

import com.dilaw.mohe.systemsettings.model.ReportTypeSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ReportTypeSettingsRepository extends JpaRepository<ReportTypeSettings, Integer> {
    Optional<ReportTypeSettings> findByReportTypeCode(String code);
    List<ReportTypeSettings> findByIsActiveTrue();
}
