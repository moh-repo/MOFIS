package com.dilaw.mohe.notification.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PreviewCountResponse {
    private int count;

    public PreviewCountResponse(int count) {
        this.count = count;
    }
}
