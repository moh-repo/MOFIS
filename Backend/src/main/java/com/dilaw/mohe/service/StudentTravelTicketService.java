package com.dilaw.mohe.service;

import com.dilaw.mohe.model.StudentDocument;
import com.dilaw.mohe.model.StudentTravelTicket;
import com.dilaw.mohe.model.StudentTravelTicketFamilyMember;
import com.dilaw.mohe.repository.StudentDocumentRepository;
import com.dilaw.mohe.repository.StudentTravelTicketRepository;
import com.dilaw.mohe.repository.StudentTravelTicketFamilyMemberRepository;
import com.dilaw.mohe.repository.spec.StudentTravelSpec;
import com.dilaw.mohe.ums.service.CountryScopeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Transactional(readOnly = true)
@Service
public class StudentTravelTicketService {

    @Autowired
    private StudentDocumentRepository studentDocumentRepository;

    @Autowired
    private StudentTravelTicketRepository travelTicketRepository;

    @Autowired
    private StudentTravelTicketFamilyMemberRepository familyMemberRepository;

    @Autowired
    private CountryScopeService countryScopeService;

    @Autowired
    private StudentAccessGuard studentAccessGuard;

    public List<StudentTravelTicket> getAll() {
        return search(null, null);
    }

    /**
     * The list behind the Travel Tickets page: everything in the caller's country scope,
     * narrowed by whichever of the two filters was sent. The page sends them as query
     * parameters of the collection URL, which the endpoint used to ignore, so choosing a
     * student or a travel type in the filter bar changed nothing (24 Sep 2026).
     */
    public List<StudentTravelTicket> search(Integer studentId, String travelType) {
        return travelTicketRepository.findAll(
                StudentTravelSpec.countryScope(countryScopeService.getScope())
                        .and(StudentTravelSpec.byStudent(studentId))
                        .and(StudentTravelSpec.byType(travelType)));
    }

    public StudentTravelTicket getById(Integer id) {
        StudentTravelTicket ticket = travelTicketRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Travel ticket not found"));
        // The ticket's student, by the student record's own rule (N14): a student with no
        // study country passed the check this replaces.
        studentAccessGuard.requireStudent(ticket.getStudentId());
        return ticket;
    }

    /** Per-student read: the student must be in the caller's country scope (403 otherwise). */
    public List<StudentTravelTicket> getByStudentId(Integer studentId) {
        if (studentId == null) {
            return List.of();
        }
        studentAccessGuard.requireStudent(studentId);
        return travelTicketRepository.findByStudentIdOrderByTravelDateDesc(studentId);
    }

    /** Tickets of one travel type, within the caller's country scope — it used to return every country's. */
    public List<StudentTravelTicket> getByTravelType(String travelType) {
        return travelTicketRepository.findAll(
                StudentTravelSpec.countryScope(countryScopeService.getScope())
                        .and(StudentTravelSpec.byType(travelType)));
    }

    /** DOCUMENT_TYPES name of a ticket's PDF (V116). STUDENT_DOCUMENTS stores the type by name. */
    public static final String TICKET_DOCUMENT_TYPE = "تذكرة سفر";

    @Autowired(required = false)
    private TravelTicketDocumentCleanup documentCleanup;

    /**
     * The ticket document {@code documentId}, if it is one and belongs to {@code studentId}.
     * A ticket names its document by id, and that id arrives from the client: without
     * this, a ticket could point at another student's file, or at a passport.
     */
    public StudentDocument requireTicketDocument(Integer studentId, Integer documentId) {
        StudentDocument doc = studentDocumentRepository.findById(documentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ticket document not found"));
        if (studentId == null || !studentId.equals(doc.getStudentId())
                || !TICKET_DOCUMENT_TYPE.equals(doc.getDocumentType())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "The attached document is not this student's travel ticket");
        }
        return doc;
    }

    /**
     * A ticket's PDF is stored hidden when uploaded, because the ticket may yet be
     * rejected; it joins the student's documents when the ticket is written.
     */
    private void attachDocument(StudentTravelTicket ticket) {
        if (ticket.getDocumentId() == null) {
            return;
        }
        StudentDocument doc = requireTicketDocument(ticket.getStudentId(), ticket.getDocumentId());
        if (!Boolean.TRUE.equals(doc.getIsActive())) {
            doc.setIsActive(true);
            studentDocumentRepository.save(doc);
        }
    }

    @Transactional
    public StudentTravelTicket create(StudentTravelTicket travelTicket) {
        attachDocument(travelTicket);
        if (travelTicket.getCreatedDate() == null) {
            travelTicket.setCreatedDate(LocalDateTime.now());
        }
        travelTicket.setTotalTicketCost(TravelTicketWafarService.computeTotal(travelTicket));

        StudentTravelTicket savedTicket = travelTicketRepository.save(travelTicket);
        
        // Save family members if travel type is WITH_FAMILY
        if ("WITH_FAMILY".equals(travelTicket.getTravelType()) && travelTicket.getFamilyMembers() != null) {
            List<StudentTravelTicketFamilyMember> members = travelTicket.getFamilyMembers();
            if (members != null && !members.isEmpty()) {
                Integer ticketId = savedTicket.getTravelTicketId();
                if (ticketId != null) {
                    for (StudentTravelTicketFamilyMember member : members) {
                        member.setTravelTicketId(ticketId);
                        if (member.getCreatedDate() == null) {
                            member.setCreatedDate(LocalDateTime.now());
                        }
                        // Age will be calculated by @PrePersist hook
                    }
                    familyMemberRepository.saveAll(members);
                    // Reload to get family members
                    return travelTicketRepository.findById(ticketId)
                            .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to retrieve created ticket"));
                }
            }
        }
        
        return savedTicket;
    }

    @Transactional
    public StudentTravelTicket update(Integer id, StudentTravelTicket travelTicket) {
        Optional<StudentTravelTicket> existingOptional = travelTicketRepository.findById(id);
        if (existingOptional.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Travel ticket not found");
        }

        StudentTravelTicket existing = existingOptional.get();
        
        // Update basic fields
        existing.setStudentId(travelTicket.getStudentId());
        existing.setTravelType(travelTicket.getTravelType());
        existing.setTravelDate(travelTicket.getTravelDate());
        existing.setDepartureCountry(travelTicket.getDepartureCountry());
        existing.setDestinationCountry(travelTicket.getDestinationCountry());
        existing.setDepartureCity(travelTicket.getDepartureCity());
        existing.setDestinationCity(travelTicket.getDestinationCity());
        existing.setAirline(travelTicket.getAirline());
        existing.setFlightNumber(travelTicket.getFlightNumber());
        existing.setTicketNumber(travelTicket.getTicketNumber());
        existing.setTicketCost(travelTicket.getTicketCost());
        existing.setCurrencyId(travelTicket.getCurrencyId());
        existing.setFundingSourceId(travelTicket.getFundingSourceId());
        existing.setTotalTicketCost(TravelTicketWafarService.computeTotal(travelTicket));
        existing.setBookingDate(travelTicket.getBookingDate());
        existing.setTravelPurpose(travelTicket.getTravelPurpose());
        existing.setNotes(travelTicket.getNotes());
        attachDocument(travelTicket);
        Integer replacedDocument = existing.getDocumentId();
        existing.setDocumentId(travelTicket.getDocumentId());
        // The PDF this edit replaced is nobody's now (review T3, W4-09).
        if (replacedDocument != null && !replacedDocument.equals(travelTicket.getDocumentId()) && documentCleanup != null) {
            documentCleanup.removeAfterCommit(replacedDocument);
        }
        existing.setModifiedDate(LocalDateTime.now());
        existing.setModifiedBy(travelTicket.getModifiedBy());

        // Remove the old family members THROUGH the managed collection so orphanRemoval deletes
        // them and the parent's in-memory collection stays consistent. (Deleting them via the
        // repository and then saving the parent cascaded a merge onto already-deleted instances
        // -> ObjectDeletedException.)
        if (existing.getFamilyMembers() != null) {
            existing.getFamilyMembers().clear();
        }
        travelTicketRepository.save(existing);

        // Insert the new members with the same mechanism create() uses (FK written via the
        // TRAVEL_TICKET_ID column), avoiding any reliance on cascade to set the foreign key.
        if ("WITH_FAMILY".equals(travelTicket.getTravelType()) && travelTicket.getFamilyMembers() != null) {
            List<StudentTravelTicketFamilyMember> members = travelTicket.getFamilyMembers();
            if (!members.isEmpty()) {
                for (StudentTravelTicketFamilyMember member : members) {
                    member.setFamilyMemberId(null); // force insert as a new row
                    member.setTravelTicketId(id);
                    if (member.getCreatedDate() == null) {
                        member.setCreatedDate(LocalDateTime.now());
                    }
                }
                familyMemberRepository.saveAll(members);
            }
        }

        return travelTicketRepository.findById(id).orElse(existing);
    }

    @Transactional
    public void delete(Integer id) {
        Optional<StudentTravelTicket> ticketOptional = travelTicketRepository.findById(id);
        if (ticketOptional.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Travel ticket not found");
        }
        
        // Family members will be deleted automatically due to CASCADE
        travelTicketRepository.deleteById(id);
    }

    /**
     * The family members of a ticket the caller may reach (FIX-PLAN N2): names,
     * passport numbers and birth dates. The member table carries no country, so
     * row-level security never covered it; the ticket's own scope check does.
     */
    public List<StudentTravelTicketFamilyMember> getFamilyMembersByTicketId(Integer travelTicketId) {
        if (travelTicketId == null) {
            return List.of();
        }
        getById(travelTicketId);
        return familyMemberRepository.findByTravelTicketIdOrderByBirthdateDesc(travelTicketId);
    }
}

