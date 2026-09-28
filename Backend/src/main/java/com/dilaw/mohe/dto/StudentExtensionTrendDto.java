package com.dilaw.mohe.dto;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class StudentExtensionTrendDto {
    int year;
    String quarter;
    long extensionCount;
    long approvedCount;
    String trend;
}
