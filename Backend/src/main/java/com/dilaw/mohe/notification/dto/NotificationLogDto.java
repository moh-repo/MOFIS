package com.dilaw.mohe.notification.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.List;

@Getter
@Setter
public class NotificationLogDto {
    private Long id;
    private List<Integer> countryIds;
    private List<String> countryNames;
    private String batchId;
    private String channel;
    private String recipientType;
    private String scope;
    private String subject;
    private String body;
    private Integer totalCount;
    private Integer sentCount;
    private Integer failedCount;
    private String status;
    private String errorSummary;
    private Integer createdBy;
    private Instant createdAt;
    private Instant sentAt;
}
