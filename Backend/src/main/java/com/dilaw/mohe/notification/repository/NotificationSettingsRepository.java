package com.dilaw.mohe.notification.repository;

import com.dilaw.mohe.notification.model.NotificationSettings;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationSettingsRepository extends JpaRepository<NotificationSettings, Integer> {

    java.util.Optional<NotificationSettings> findByCountryId(Integer countryId);

    java.util.Optional<NotificationSettings> findFirstByCountryIdIsNull();
}
