package com.dilaw.mohe.service;

import com.dilaw.mohe.dto.*;
import com.dilaw.mohe.model.Connection;
import com.dilaw.mohe.model.StudentNode;
import com.dilaw.mohe.repository.ConnectionRepository;
import com.dilaw.mohe.repository.EmploymentHistoryRepository;
import com.dilaw.mohe.repository.StudentNodeRepository;
import com.dilaw.mohe.ums.service.ReportCountryScopeHelper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Transactional(readOnly = true)
@Service
@RequiredArgsConstructor
public class StudentTracerService {

    private final StudentNodeRepository studentNodeRepository;
    private final ConnectionRepository connectionRepository;
    private final EmploymentHistoryRepository employmentHistoryRepository;
    private final ReportCountryScopeHelper countryScopeHelper;

    /**
     * Most students a single graph is drawn from.
     *
     * <p>Review item P6: the tracer sent every matching student to the browser and
     * vis-network then ran a force simulation over all of them, which on an
     * unfiltered country takes the page from responsive to unusable — and the
     * result is unreadable long before it is slow. Above the cap the graph is the
     * best-connected students, and the page says how many were left out so the
     * answer is to narrow the filter rather than to wait.
     */
    static final int MAX_GRAPH_NODES = 1000;

    public NetworkGraphDto getNetworkGraph(TracerFilterDto filter) {
        List<StudentNode> matched = getFilteredNodes(filter);
        List<Connection> connections = getFilteredConnections(filter);
        List<StudentNode> nodes = capNodes(matched, connections);

        // Filter connections to only include those between visible nodes
        Set<Integer> nodeIds = nodes.stream()
                .map(StudentNode::getNodeId)
                .collect(Collectors.toSet());

        List<Connection> filteredConnections = connections.stream()
                .filter(c -> nodeIds.contains(c.getSourceNodeId()) && nodeIds.contains(c.getTargetNodeId()))
                .collect(Collectors.toList());

        // Build connection count map
        Map<Integer, Long> connectionCountMap = buildConnectionCountMap(filteredConnections);

        List<NodeDto> nodeDtos = nodes.stream()
                .map(node -> mapToNodeDto(node, connectionCountMap.getOrDefault(node.getNodeId(), 0L)))
                .collect(Collectors.toList());

        List<EdgeDto> edgeDtos = filteredConnections.stream()
                .map(this::mapToEdgeDto)
                .collect(Collectors.toList());

        // Statistics describe the whole match, not the capped graph: the cards above
        // the network answer "how many students are there", and a capped graph must
        // not quietly change that number (review item P6).
        Set<Integer> matchedIds = matched.stream().map(StudentNode::getNodeId).collect(Collectors.toSet());
        List<Connection> matchedConnections = matched.size() == nodes.size()
                ? filteredConnections
                : connections.stream()
                        .filter(c -> matchedIds.contains(c.getSourceNodeId())
                                && matchedIds.contains(c.getTargetNodeId()))
                        .collect(Collectors.toList());
        NetworkStatsDto stats = calculateStats(matched, matchedConnections);

        return NetworkGraphDto.builder()
                .nodes(nodeDtos)
                .edges(edgeDtos)
                .stats(stats)
                .matchedNodeCount(matched.size())
                .truncated(matched.size() > nodes.size())
                .build();
    }

    /**
     * The first {@link #MAX_GRAPH_NODES} students by number of connections, so a
     * capped graph keeps the hubs the picture is about rather than an arbitrary
     * thousand. Returns the list unchanged when it is already within the cap.
     */
    private List<StudentNode> capNodes(List<StudentNode> nodes, List<Connection> connections) {
        if (nodes.size() <= MAX_GRAPH_NODES) {
            return nodes;
        }
        Map<Integer, Long> degrees = buildConnectionCountMap(connections);
        return nodes.stream()
                .sorted(Comparator.comparingLong(
                        (StudentNode n) -> degrees.getOrDefault(n.getNodeId(), 0L)).reversed()
                        .thenComparing(StudentNode::getNodeId))
                .limit(MAX_GRAPH_NODES)
                .collect(Collectors.toList());
    }

    private List<StudentNode> getFilteredNodes(TracerFilterDto filter) {
        Integer requestCountryId = filter != null ? filter.getCountryId() : null;
        countryScopeHelper.ensureScopeOrThrow();
        List<Integer> countryIds = countryScopeHelper.resolveCountryIds(requestCountryId);
        boolean applyCountry = countryScopeHelper.shouldApplyCountryFilter(countryIds);
        List<Integer> safeIds = countryScopeHelper.safeList(countryIds);

        if (filter == null || isFilterEmpty(filter)) {
            return studentNodeRepository.findActiveByCountryScope(applyCountry, safeIds);
        }

        return studentNodeRepository.searchNodes(
                applyCountry,
                safeIds,
                filter.getUniversity(),
                filter.getMajor(),
                filter.getCountry(),
                filter.getEmploymentStatus(),
                filter.getDegreeLevel()
        );
    }

    private List<Connection> getFilteredConnections(TracerFilterDto filter) {
        if (filter == null || (filter.getConnectionType() == null && filter.getMinConnectionStrength() == null)) {
            return connectionRepository.findByIsActiveTrue();
        }

        return connectionRepository.searchConnections(
                filter.getConnectionType(),
                filter.getMinConnectionStrength()
        );
    }

    private boolean isFilterEmpty(TracerFilterDto filter) {
        return filter.getUniversity() == null &&
               filter.getMajor() == null &&
               filter.getCountry() == null &&
               filter.getEmploymentStatus() == null &&
               filter.getDegreeLevel() == null &&
               filter.getConnectionType() == null &&
               filter.getMinConnectionStrength() == null;
    }

    private Map<Integer, Long> buildConnectionCountMap(List<Connection> connections) {
        Map<Integer, Long> countMap = new HashMap<>();
        
        for (Connection conn : connections) {
            countMap.merge(conn.getSourceNodeId(), 1L, Long::sum);
            countMap.merge(conn.getTargetNodeId(), 1L, Long::sum);
        }
        
        return countMap;
    }

    private NodeDto mapToNodeDto(StudentNode node, Long connectionCount) {
        String tooltip = buildTooltip(node);
        String shape = determineShape(node.getDegreeLevel());
        Integer size = calculateNodeSize(node, connectionCount);

        return NodeDto.builder()
                .id(node.getNodeId())
                .studentId(node.getStudentId())
                .label(node.getStudentName())
                .title(tooltip)
                .group(node.getDegreeLevel())
                .color(node.getNodeColor() != null ? node.getNodeColor() : getColorByDegreeLevel(node.getDegreeLevel()))
                .size(size)
                .shape(shape)
                .x(node.getLongitude())
                .y(node.getLatitude())
                .university(node.getUniversity())
                .major(node.getMajor())
                .graduationYear(node.getGraduationYear())
                .degreeLevel(node.getDegreeLevel())
                .country(node.getCountry())
                .employmentStatus(node.getCurrentEmploymentStatus())
                .currentPosition(node.getCurrentPosition())
                .currentCompany(node.getCurrentCompany())
                .email(node.getEmail())
                .phone(node.getPhone())
                .linkedinUrl(node.getLinkedinUrl())
                .connectionCount(connectionCount)
                .build();
    }

    private EdgeDto mapToEdgeDto(Connection connection) {
        String tooltip = buildConnectionTooltip(connection);
        Integer width = Math.max(1, connection.getConnectionStrength() != null ? connection.getConnectionStrength() / 2 : 1);
        String color = getColorByConnectionType(connection.getConnectionType());

        return EdgeDto.builder()
                .id(connection.getConnectionId())
                .from(connection.getSourceNodeId())
                .to(connection.getTargetNodeId())
                .label(connection.getConnectionType())
                .title(tooltip)
                .width(width)
                .color(color)
                .connectionType(connection.getConnectionType())
                .connectionStrength(connection.getConnectionStrength())
                .build();
    }

    private String buildTooltip(StudentNode node) {
        StringBuilder sb = new StringBuilder();
        sb.append("<b>").append(node.getStudentName()).append("</b><br/>");
        if (node.getUniversity() != null) {
            sb.append("University: ").append(node.getUniversity()).append("<br/>");
        }
        if (node.getMajor() != null) {
            sb.append("Major: ").append(node.getMajor()).append("<br/>");
        }
        if (node.getDegreeLevel() != null) {
            sb.append("Degree: ").append(node.getDegreeLevel()).append("<br/>");
        }
        if (node.getCurrentEmploymentStatus() != null) {
            sb.append("Status: ").append(node.getCurrentEmploymentStatus()).append("<br/>");
        }
        if (node.getCurrentCompany() != null) {
            sb.append("Company: ").append(node.getCurrentCompany()).append("<br/>");
        }
        return sb.toString();
    }

    private String buildConnectionTooltip(Connection connection) {
        StringBuilder sb = new StringBuilder();
        sb.append("<b>").append(connection.getConnectionType()).append("</b><br/>");
        if (connection.getConnectionStrength() != null) {
            sb.append("Strength: ").append(connection.getConnectionStrength()).append("/10<br/>");
        }
        if (connection.getDescription() != null) {
            sb.append(connection.getDescription());
        }
        return sb.toString();
    }

    private String determineShape(String degreeLevel) {
        if (degreeLevel == null) return "dot";
        switch (degreeLevel.toLowerCase()) {
            case "bachelor":
                return "dot";
            case "master":
                return "square";
            case "phd":
                return "triangle";
            default:
                return "dot";
        }
    }

    private Integer calculateNodeSize(StudentNode node, Long connectionCount) {
        int baseSize = node.getNodeSize() != null ? node.getNodeSize() : 25;
        int connectionBonus = connectionCount.intValue() * 2;
        return Math.min(baseSize + connectionBonus, 60); // Cap at 60
    }

    private String getColorByDegreeLevel(String degreeLevel) {
        if (degreeLevel == null) return "#1890ff";
        switch (degreeLevel.toLowerCase()) {
            case "bachelor":
                return "#1890ff"; // Blue
            case "master":
                return "#52c41a"; // Green
            case "phd":
                return "#fa8c16"; // Orange
            default:
                return "#722ed1"; // Purple
        }
    }

    private String getColorByConnectionType(String connectionType) {
        if (connectionType == null) return "#d9d9d9";
        switch (connectionType.toUpperCase()) {
            case "CLASSMATE":
                return "#1890ff"; // Blue
            case "COLLEAGUE":
                return "#52c41a"; // Green
            case "MENTOR":
                return "#fa8c16"; // Orange
            case "REFERRAL":
                return "#722ed1"; // Purple
            case "FRIEND":
                return "#eb2f96"; // Pink
            default:
                return "#d9d9d9"; // Gray
        }
    }

    private NetworkStatsDto calculateStats(List<StudentNode> nodes, List<Connection> connections) {
        long totalNodes = nodes.size();
        long totalConnections = connections.size();
        
        long employedCount = nodes.stream()
                .filter(n -> "Employed".equalsIgnoreCase(n.getCurrentEmploymentStatus()) ||
                            "Self-Employed".equalsIgnoreCase(n.getCurrentEmploymentStatus()))
                .count();

        double avgStrength = connections.stream()
                .filter(c -> c.getConnectionStrength() != null)
                .mapToInt(Connection::getConnectionStrength)
                .average()
                .orElse(0.0);

        String mostCommonType = connections.stream()
                .collect(Collectors.groupingBy(Connection::getConnectionType, Collectors.counting()))
                .entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("N/A");

        long totalUniversities = nodes.stream()
                .map(StudentNode::getUniversity)
                .filter(Objects::nonNull)
                .distinct()
                .count();

        long totalMajors = nodes.stream()
                .map(StudentNode::getMajor)
                .filter(Objects::nonNull)
                .distinct()
                .count();

        long totalCountries = nodes.stream()
                .map(StudentNode::getCountry)
                .filter(Objects::nonNull)
                .distinct()
                .count();

        return NetworkStatsDto.builder()
                .totalNodes(totalNodes)
                .totalConnections(totalConnections)
                .employedCount(employedCount)
                .avgConnectionStrength(Math.round(avgStrength * 10.0) / 10.0)
                .mostCommonConnectionType(mostCommonType)
                .totalUniversities(totalUniversities)
                .totalMajors(totalMajors)
                .totalCountries(totalCountries)
                .build();
    }

    public Map<String, List<String>> getFilterOptions(Integer countryId) {
        countryScopeHelper.ensureScopeOrThrow();
        List<Integer> countryIds = countryScopeHelper.resolveCountryIds(countryId);
        boolean applyCountry = countryScopeHelper.shouldApplyCountryFilter(countryIds);
        List<Integer> safeIds = countryScopeHelper.safeList(countryIds);

        Map<String, List<String>> options = new HashMap<>();
        options.put("universities", studentNodeRepository.findDistinctUniversities(applyCountry, safeIds));
        options.put("majors", studentNodeRepository.findDistinctMajors(applyCountry, safeIds));
        options.put("countries", studentNodeRepository.findDistinctCountries(applyCountry, safeIds));
        options.put("employmentStatuses", studentNodeRepository.findDistinctEmploymentStatuses(applyCountry, safeIds));
        options.put("degreeLevels", studentNodeRepository.findDistinctDegreeLevels(applyCountry, safeIds));
        options.put("connectionTypes", connectionRepository.findDistinctConnectionTypes());
        return options;
    }
}
