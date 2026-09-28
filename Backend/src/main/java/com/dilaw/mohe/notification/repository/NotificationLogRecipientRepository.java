package com.dilaw.mohe.notification.repository;

import com.dilaw.mohe.notification.model.NotificationLogRecipient;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotificationLogRecipientRepository extends JpaRepository<NotificationLogRecipient, Long> {

    /** In the order the dispatcher worked through the batch. */
    List<NotificationLogRecipient> findByLogIdOrderByIdAsc(Long logId);
}
