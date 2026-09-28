package com.dilaw.mohe.service;

import com.dilaw.mohe.model.EmploymentHistory;
import com.dilaw.mohe.model.StudentDatum;
import com.dilaw.mohe.model.StudentNode;
import com.dilaw.mohe.model.StudentTravelTicket;
import com.dilaw.mohe.repository.EmploymentHistoryRepository;
import com.dilaw.mohe.repository.StudentNodeRepository;
import com.dilaw.mohe.repository.StudentTravelTicketFamilyMemberRepository;
import com.dilaw.mohe.repository.StudentTravelTicketRepository;
import com.dilaw.mohe.ums.service.CountryScopeService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * FIX-PLAN N2 (25 Sep 2026): two student sub-resources whose tables carry no
 * country, so row-level security never covered them, and whose handlers checked
 * nothing — a travel ticket's family members (names, passports, birth dates) and
 * a tracer node's employment history.
 */
class StudentSubResourceScopeUnitTest {

    private static final int OTHER_COUNTRY_STUDENT = 2;
    private static final int JORDAN = 1004;
    private static final int EGYPT = 1;

    @Nested
    @ExtendWith(MockitoExtension.class)
    @MockitoSettings(strictness = Strictness.LENIENT)
    @DisplayName("travel-ticket family members")
    class FamilyMembers {

        @Mock private StudentTravelTicketRepository tickets;
        @Mock private StudentTravelTicketFamilyMemberRepository members;
        @Mock private CountryScopeService countryScope;
        // The ticket's student decides, through the guard (N14 replaced the country check).
        @Mock private StudentAccessGuard guard;
        @InjectMocks private StudentTravelTicketService service;

        private void ticketOf(int studyCountry) {
            StudentDatum student = new StudentDatum();
            student.setStudentId(OTHER_COUNTRY_STUDENT);
            student.setStudyCountryIn(studyCountry);
            StudentTravelTicket ticket = new StudentTravelTicket();
            ticket.setStudentId(OTHER_COUNTRY_STUDENT);
            ReflectionTestUtils.setField(ticket, "student", student);
            when(tickets.findById(45)).thenReturn(Optional.of(ticket));
            if (studyCountry == EGYPT) {
                when(guard.requireStudent(OTHER_COUNTRY_STUDENT)).thenThrow(
                        new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied: country not in scope"));
            } else {
                when(guard.requireStudent(OTHER_COUNTRY_STUDENT)).thenReturn(student);
            }
        }

        @Test
        @DisplayName("another country's ticket: refused, members never read")
        void otherCountryRefused() {
            ticketOf(EGYPT);

            assertThatThrownBy(() -> service.getFamilyMembersByTicketId(45))
                    .isInstanceOf(ResponseStatusException.class)
                    .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                    .isEqualTo(HttpStatus.FORBIDDEN);
            verify(members, never()).findByTravelTicketIdOrderByBirthdateDesc(anyInt());
        }

        @Test
        @DisplayName("a ticket hidden by row-level security: not found, members never read")
        void hiddenTicketNotFound() {
            when(tickets.findById(45)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getFamilyMembersByTicketId(45))
                    .isInstanceOf(ResponseStatusException.class)
                    .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                    .isEqualTo(HttpStatus.NOT_FOUND);
            verify(members, never()).findByTravelTicketIdOrderByBirthdateDesc(anyInt());
        }

        @Test
        @DisplayName("an own-country ticket: members returned")
        void ownCountryReturned() {
            ticketOf(JORDAN);
            when(members.findByTravelTicketIdOrderByBirthdateDesc(45)).thenReturn(List.of());

            assertThat(service.getFamilyMembersByTicketId(45)).isEmpty();
            verify(members).findByTravelTicketIdOrderByBirthdateDesc(45);
        }
    }

    @Nested
    @ExtendWith(MockitoExtension.class)
    @MockitoSettings(strictness = Strictness.LENIENT)
    @DisplayName("tracer employment history")
    class TracerEmployment {

        @Mock private StudentNodeRepository nodes;
        @Mock private EmploymentHistoryRepository employment;
        @Mock private StudentAccessGuard guard;
        @InjectMocks private TracerEmploymentService service;

        private void nodeOf(int nodeId, int studentId) {
            StudentNode node = new StudentNode();
            ReflectionTestUtils.setField(node, "nodeId", nodeId);
            ReflectionTestUtils.setField(node, "studentId", studentId);
            when(nodes.findById(nodeId)).thenReturn(Optional.of(node));
        }

        private void refuseOtherCountry() {
            when(guard.requireStudent(OTHER_COUNTRY_STUDENT))
                    .thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied: country not in scope"));
        }

        @Test
        @DisplayName("another country's node: read, add, update and delete are refused and nothing is written")
        void otherCountryRefused() {
            refuseOtherCountry();
            nodeOf(7, OTHER_COUNTRY_STUDENT);
            EmploymentHistory row = new EmploymentHistory();
            row.setEmploymentId(70);
            row.setNodeId(7);
            when(employment.findById(70)).thenReturn(Optional.of(row));

            assertThatThrownBy(() -> service.byNode(7)).isInstanceOf(ResponseStatusException.class);
            assertThatThrownBy(() -> service.add(7, new EmploymentHistory())).isInstanceOf(ResponseStatusException.class);
            assertThatThrownBy(() -> service.update(70, new EmploymentHistory())).isInstanceOf(ResponseStatusException.class);
            assertThatThrownBy(() -> service.delete(70)).isInstanceOf(ResponseStatusException.class);
            verify(employment, never()).save(any());
            verify(employment, never()).deleteById(any());
            verify(employment, never()).findByNodeId(anyInt());
        }

        @Test
        @DisplayName("an update keeps the row on its own node")
        void updateCannotMoveTheRow() {
            nodeOf(8, 10117);
            EmploymentHistory stored = new EmploymentHistory();
            stored.setEmploymentId(80);
            stored.setNodeId(8);
            when(employment.findById(80)).thenReturn(Optional.of(stored));
            when(employment.save(any())).thenAnswer(inv -> inv.getArgument(0));

            EmploymentHistory body = new EmploymentHistory();
            body.setNodeId(7);
            assertThat(service.update(80, body).getNodeId()).isEqualTo(8);
            verify(guard).requireStudent(10117);
        }
    }
}
