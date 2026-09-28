package com.dilaw.mohe.service;

import com.dilaw.mohe.approval.model.ApprovalRequest;
import com.dilaw.mohe.approval.repository.ApprovalRequestRepository;
import com.dilaw.mohe.model.*;
import com.dilaw.mohe.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.*;

/**
 * Assembles a chronological timeline of events for a single student:
 * approvals, year results, extensions, and travel tickets.
 *
 * <p>Each section is loaded independently and a failing one is left out rather than
 * failing the whole timeline, but it is logged at WARN with the section and student
 * id. The four catches used to be empty, so a broken query simply made a section
 * vanish with nothing in the log to say so.
 */
@Service
@Transactional(readOnly = true)
public class StudentTimelineService {

    private static final Logger log = LoggerFactory.getLogger(StudentTimelineService.class);

    @Autowired private ApprovalRequestRepository approvalRequestRepository;
    @Autowired private StudentYearResultRepository yearResultRepository;
    @Autowired private StudentExtensionRepository extensionRepository;
    @Autowired private StudentTravelTicketRepository travelTicketRepository;

    public List<Map<String, Object>> buildTimeline(Integer studentId) {
        List<Map<String, Object>> events = new ArrayList<>();

        // Approval requests for this student
        try {
            List<ApprovalRequest> approvals = approvalRequestRepository
                    .findByEntityTypeAndEntityIdOrderByRequestedAtDesc("STUDENT_DATA", studentId.toString());
            for (ApprovalRequest a : approvals) {
                Instant ts = a.getRequestedAt();
                events.add(event("APPROVAL", ts,
                        a.getRequestType() + " — " + a.getStatus(),
                        Map.of("requestId", a.getId(),
                               "status", String.valueOf(a.getStatus()),
                               "requestType", String.valueOf(a.getRequestType()))));
            }
        } catch (Exception e) {
            logSectionSkipped("approvals", studentId, e);
        }

        // Year results
        try {
            List<StudentYearResult> results = yearResultRepository
                    .findByStudentIdOrderByYearNumberAsc(studentId);
            for (StudentYearResult r : results) {
                events.add(event("YEAR_RESULT", r.getUpdatedAt(),
                        "Year result — year " + r.getYearNumber() + ": " + (Boolean.TRUE.equals(r.getPassed()) ? "Passed" : "Failed"),
                        Map.of("yearResultId", r.getId(),
                               "yearNumber", r.getYearNumber(),
                               "passed", Boolean.TRUE.equals(r.getPassed()),
                               "grade", r.getGrade() != null ? r.getGrade() : "")));
            }
        } catch (Exception e) {
            logSectionSkipped("year results", studentId, e);
        }

        // Extensions
        try {
            List<StudentExtension> extensions = extensionRepository
                    .findByFilters(studentId, null, null, null, null);
            for (StudentExtension e : extensions) {
                Instant ts = e.getCreatedDate() != null
                        ? e.getCreatedDate().toInstant(ZoneOffset.UTC) : null;
                events.add(event("EXTENSION", ts,
                        "Extension: " + (e.getExtensionType() != null ? e.getExtensionType() : "") +
                                (e.getExtensionQuarters() != null ? " (" + e.getExtensionQuarters() + " quarters)" : ""),
                        Map.of("extensionId", e.getExtensionId(),
                               "extensionType", e.getExtensionType() != null ? e.getExtensionType() : "",
                               "quarters", e.getExtensionQuarters() != null ? e.getExtensionQuarters() : 0)));
            }
        } catch (Exception e) {
            logSectionSkipped("extensions", studentId, e);
        }

        // Travel tickets
        try {
            List<StudentTravelTicket> tickets = travelTicketRepository
                    .findByStudentIdOrderByTravelDateDesc(studentId);
            for (StudentTravelTicket t : tickets) {
                Instant ts = t.getCreatedDate() != null
                        ? t.getCreatedDate().toInstant(ZoneOffset.UTC) : null;
                events.add(event("TRAVEL_TICKET", ts,
                        "Travel: " + (t.getTravelType() != null ? t.getTravelType() : "") +
                                (t.getDestinationCountry() != null ? " → " + t.getDestinationCountry() : ""),
                        Map.of("ticketId", t.getTravelTicketId(),
                               "travelType", t.getTravelType() != null ? t.getTravelType() : "",
                               "travelDate", t.getTravelDate() != null ? t.getTravelDate().toString() : "")));
            }
        } catch (Exception e) {
            logSectionSkipped("travel tickets", studentId, e);
        }

        // Sort newest-first; null dates fall to the bottom
        events.sort((a, b) -> {
            Instant da = (Instant) a.get("date");
            Instant db = (Instant) b.get("date");
            if (da == null && db == null) return 0;
            if (da == null) return 1;
            if (db == null) return -1;
            return db.compareTo(da);
        });

        return events;
    }

    private static void logSectionSkipped(String section, Integer studentId, Exception e) {
        log.warn("Timeline for student {}: {} section skipped: {}", studentId, section, e.toString());
    }

    private Map<String, Object> event(String type, Instant date, String summary, Map<String, Object> detail) {
        Map<String, Object> e = new LinkedHashMap<>();
        e.put("type", type);
        e.put("date", date);
        e.put("summary", summary);
        e.put("detail", detail);
        return e;
    }
}
