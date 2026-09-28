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
public class GeoBreakdownDto {
    private String name;
    private Long value;
    @Builder.Default
    private List<GeoBreakdownDto> children = new ArrayList<>();

    public void addChild(GeoBreakdownDto child) {
        if (child != null) {
            children.add(child);
        }
    }
}


