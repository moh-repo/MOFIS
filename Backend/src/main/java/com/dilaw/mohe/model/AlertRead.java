package com.dilaw.mohe.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

/**
 * One account has read one alert ({@code MOHE_ALERTS.alert_reads}, V144; live QA N10).
 * The alert's own {@code is_read} was one flag for everybody.
 */
@Getter
@Setter
@Entity
@IdClass(AlertRead.Key.class)
@Table(name = "alert_reads", schema = "MOHE_ALERTS")
public class AlertRead {

    @Id
    @Column(name = "alert_id", nullable = false)
    private Integer alertId;

    @Id
    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "read_at", nullable = false)
    private Instant readAt;

    public AlertRead() {
    }

    public AlertRead(Integer alertId, Long userId, Instant readAt) {
        this.alertId = alertId;
        this.userId = userId;
        this.readAt = readAt;
    }

    /** The composite key. */
    public static class Key implements Serializable {
        private Integer alertId;
        private Long userId;

        public Key() {
        }

        public Key(Integer alertId, Long userId) {
            this.alertId = alertId;
            this.userId = userId;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Key k && Objects.equals(alertId, k.alertId) && Objects.equals(userId, k.userId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(alertId, userId);
        }
    }
}
