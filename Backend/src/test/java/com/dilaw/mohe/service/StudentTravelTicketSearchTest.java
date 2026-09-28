package com.dilaw.mohe.service;

import com.dilaw.mohe.model.StudentTravelTicket;
import com.dilaw.mohe.scope.AsUser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * The Travel Tickets page's filter bar, end to end through the service (24 Sep 2026).
 *
 * <p>The page sends {@code studentId} and {@code travelType} with the list request;
 * the endpoint had ignored both, so the list never changed. Read as the ministry, which
 * sees every country, so the counts can be checked against the table itself.
 */
@Tag("integration")
@SpringBootTest
class StudentTravelTicketSearchTest {

    @Autowired private StudentTravelTicketService service;
    @Autowired private JdbcTemplate jdbc;

    private int count(String where, Object... args) {
        Integer n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM MOHECORE.STUDENT_TRAVEL_TICKET t " + where, Integer.class, args);
        return n == null ? 0 : n;
    }

    private List<StudentTravelTicket> search(Integer studentId, String travelType) {
        return AsUser.call(AsUser.SUPERADMIN, () -> service.search(studentId, travelType));
    }

    @Test
    @DisplayName("the list narrows to the student and the travel type it was asked for")
    void searchNarrowsByStudentAndType() {
        Map<String, Object> seed = jdbc.queryForList(
                "SELECT TOP 1 STUDENT_ID, TRAVEL_TYPE FROM MOHECORE.STUDENT_TRAVEL_TICKET ORDER BY TRAVEL_TICKET_ID")
                .stream().findFirst().orElse(null);
        assumeTrue(seed != null, "no travel ticket seeded");
        int studentId = ((Number) seed.get("STUDENT_ID")).intValue();
        String type = String.valueOf(seed.get("TRAVEL_TYPE"));

        assertThat(search(null, null))
                .as("no filter: everything")
                .hasSize(count(""));

        assertThat(search(studentId, null))
                .as("one student")
                .hasSize(count("WHERE t.STUDENT_ID = ?", studentId))
                .allSatisfy(t -> assertThat(t.getStudentId()).isEqualTo(studentId));

        assertThat(search(null, type))
                .as("one travel type")
                .hasSize(count("WHERE t.TRAVEL_TYPE = ?", type))
                .allSatisfy(t -> assertThat(t.getTravelType()).isEqualTo(type));

        assertThat(search(studentId, type))
                .as("both together")
                .hasSize(count("WHERE t.STUDENT_ID = ? AND t.TRAVEL_TYPE = ?", studentId, type))
                .isNotEmpty();

        assertThat(search(null, "NO_SUCH_TYPE"))
                .as("a type nobody has")
                .isEmpty();
    }
}
