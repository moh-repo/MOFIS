package com.dilaw.mohe.dto;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class StudentExtensionReportMetadataDto {
    List<String> extensionTypes;
}
