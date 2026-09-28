package com.dilaw.mohe.notification.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SendMessageResponse {
    private String batchId;
    private String status;
    private String message;

    public SendMessageResponse(String batchId, String status, String message) {
        this.batchId = batchId;
        this.status = status;
        this.message = message;
    }
}
