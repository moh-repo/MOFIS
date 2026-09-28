package com.dilaw.mohe.notification.repository;

import com.dilaw.mohe.notification.model.NotificationLogCountry;
import com.dilaw.mohe.notification.model.NotificationLogCountryId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface NotificationLogCountryRepository extends JpaRepository<NotificationLogCountry, NotificationLogCountryId> {

    List<NotificationLogCountry> findByLogId(Long logId);

    @Query("SELECT nlc.countryId FROM NotificationLogCountry nlc WHERE nlc.logId = :logId")
    List<Integer> findCountryIdsByLogId(@Param("logId") Long logId);

    /** One query for a whole history page, instead of one per row. */
    List<NotificationLogCountry> findByLogIdIn(java.util.Collection<Long> logIds);
}
