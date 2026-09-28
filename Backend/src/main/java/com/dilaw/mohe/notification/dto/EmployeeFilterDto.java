package com.dilaw.mohe.notification.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class EmployeeFilterDto {
    private List<Long> groupIds;
    private List<Long> roleIds;
}
