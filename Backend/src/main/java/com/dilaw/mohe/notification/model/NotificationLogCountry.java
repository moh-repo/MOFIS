package com.dilaw.mohe.notification.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@IdClass(NotificationLogCountryId.class)
@Table(name = "NOTIFICATION_LOG_COUNTRY", schema = "MOHECORE")
public class NotificationLogCountry {

    @Id
    @Column(name = "LOG_ID", nullable = false)
    private Long logId;

    @Id
    @Column(name = "COUNTRY_ID", nullable = false)
    private Integer countryId;
}
