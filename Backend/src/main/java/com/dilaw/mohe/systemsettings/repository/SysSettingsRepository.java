package com.dilaw.mohe.systemsettings.repository;

import com.dilaw.mohe.systemsettings.model.SysSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SysSettingsRepository extends JpaRepository<SysSettings, Integer> {
}
