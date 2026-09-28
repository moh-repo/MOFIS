package com.dilaw.mohe.service;

import com.dilaw.mohe.dto.NetworkGraphDto;
import com.dilaw.mohe.model.Connection;
import com.dilaw.mohe.model.StudentNode;
import com.dilaw.mohe.repository.ConnectionRepository;
import com.dilaw.mohe.repository.EmploymentHistoryRepository;
import com.dilaw.mohe.repository.StudentNodeRepository;
import com.dilaw.mohe.ums.service.ReportCountryScopeHelper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The tracer graph is capped (review item P6).
 *
 * <p>Before the cap the page drew every matching student and ran a force simulation
 * over the lot; an unfiltered country meant thousands of nodes, a browser pinned at
 * 100% of a core and a picture nobody could read anyway. The cap keeps the
 * best-connected students — the hubs the graph is about — and the response says how
 * many were left out, so the page can ask for a narrower filter instead of quietly
 * answering a different question.
 */
class StudentTracerGraphCapTest {

    private final StudentNodeRepository nodes = mock(StudentNodeRepository.class);
    private final ConnectionRepository connections = mock(ConnectionRepository.class);
    private final EmploymentHistoryRepository employment = mock(EmploymentHistoryRepository.class);
    private final ReportCountryScopeHelper scope = mock(ReportCountryScopeHelper.class);

    private final StudentTracerService service =
            new StudentTracerService(nodes, connections, employment, scope);

    private static StudentNode node(int id) {
        StudentNode node = new StudentNode();
        node.setNodeId(id);
        node.setStudentId(id);
        node.setStudentName("Student " + id);
        return node;
    }

    private static Connection edge(int id, int source, int target) {
        Connection connection = new Connection();
        connection.setConnectionId(id);
        connection.setSourceNodeId(source);
        connection.setTargetNodeId(target);
        connection.setConnectionType("CLASSMATE");
        return connection;
    }

    private void given(List<StudentNode> studentNodes, List<Connection> edges) {
        when(scope.resolveCountryIds(any())).thenReturn(List.of());
        when(scope.shouldApplyCountryFilter(any())).thenReturn(false);
        when(scope.safeList(any())).thenReturn(List.of());
        when(nodes.findActiveByCountryScope(anyBoolean(), any())).thenReturn(studentNodes);
        when(connections.findByIsActiveTrue()).thenReturn(edges);
    }

    @Test
    @DisplayName("a graph within the cap is returned whole and is not marked truncated")
    void smallGraphUntouched() {
        given(IntStream.rangeClosed(1, 50).mapToObj(StudentTracerGraphCapTest::node).toList(),
                List.of(edge(1, 1, 2), edge(2, 2, 3)));

        NetworkGraphDto graph = service.getNetworkGraph(null);

        assertThat(graph.getNodes()).hasSize(50);
        assertThat(graph.isTruncated()).isFalse();
        assertThat(graph.getMatchedNodeCount()).isEqualTo(50);
        assertThat(graph.getEdges()).hasSize(2);
    }

    @Test
    @DisplayName("above the cap the best-connected students are kept, and the count of the whole match is reported")
    void largeGraphCapped() {
        int total = StudentTracerService.MAX_GRAPH_NODES + 250;
        List<StudentNode> all = IntStream.rangeClosed(1, total)
                .mapToObj(StudentTracerGraphCapTest::node).toList();

        // Give the last few students many connections, so they are the ones the cap
        // must keep rather than simply the first thousand ids.
        List<Connection> edges = new ArrayList<>();
        int id = 0;
        for (int n = total; n > total - 10; n--) {
            for (int other = total; other > total - 10; other--) {
                if (n != other) {
                    edges.add(edge(++id, n, other));
                }
            }
        }
        given(all, edges);

        NetworkGraphDto graph = service.getNetworkGraph(null);

        assertThat(graph.getNodes()).hasSize(StudentTracerService.MAX_GRAPH_NODES);
        assertThat(graph.isTruncated()).isTrue();
        assertThat(graph.getMatchedNodeCount()).isEqualTo(total);
        assertThat(graph.getNodes().stream().map(n -> n.getId()))
                .as("the well-connected students survive the cap")
                .contains(total, total - 1, total - 9);
        assertThat(graph.getStats().getTotalNodes())
                .as("the statistics describe the whole match, not the drawn subset")
                .isEqualTo((long) total);
    }
}
