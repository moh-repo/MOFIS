package com.dilaw.mohe.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class NetworkGraphDto {
    private List<NodeDto> nodes;
    private List<EdgeDto> edges;
    private NetworkStatsDto stats;
    /**
     * How many students matched the filter, which is more than {@code nodes} holds
     * when the graph was capped (review item P6). The statistics below still
     * describe the whole match, so the page can say "showing 1,000 of 4,312"
     * instead of quietly drawing a different graph from the one asked for.
     */
    private long matchedNodeCount;
    /** True when {@code nodes} was cut to the cap. */
    private boolean truncated;
}

