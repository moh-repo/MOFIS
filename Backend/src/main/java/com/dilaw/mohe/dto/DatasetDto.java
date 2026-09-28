package com.dilaw.mohe.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DatasetDto {
    private String label;
    private List<Number> data;
    private String backgroundColor;
    private String borderColor;
}

