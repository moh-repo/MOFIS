package com.dilaw.mohe.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GeoLocationDto {
    private String country;
    private Double latitude;
    private Double longitude;
    private Long studentCount;
    @Builder.Default
    private List<GeoBreakdownDto> governorates = new ArrayList<>();

    public void addGovernorate(GeoBreakdownDto governorate) {
        if (governorate != null) {
            governorates.add(governorate);
        }
    }
}


