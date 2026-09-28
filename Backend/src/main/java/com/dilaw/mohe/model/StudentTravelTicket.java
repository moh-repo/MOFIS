package com.dilaw.mohe.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Nationalized;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@Entity
@Table(name = "STUDENT_TRAVEL_TICKET", schema = "MOHECORE")
public class StudentTravelTicket {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "TRAVEL_TICKET_ID", nullable = false)
    private Integer travelTicketId;

    @Column(name = "STUDENT_ID", nullable = false)
    private Integer studentId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "STUDENT_ID", insertable = false, updatable = false)
    @JsonIgnore
    private StudentDatum student;

    @Nationalized
    @Column(name = "TRAVEL_TYPE", nullable = false, length = 50)
    private String travelType; // 'ALONE' or 'WITH_FAMILY'

    @Column(name = "TRAVEL_DATE")
    private LocalDate travelDate;

    @Nationalized
    @Column(name = "DEPARTURE_COUNTRY", length = 100)
    private String departureCountry;

    @Nationalized
    @Column(name = "DESTINATION_COUNTRY", length = 100)
    private String destinationCountry;

    @Nationalized
    @Column(name = "DEPARTURE_CITY", length = 100)
    private String departureCity;

    @Nationalized
    @Column(name = "DESTINATION_CITY", length = 100)
    private String destinationCity;

    @Nationalized
    @Column(name = "AIRLINE", length = 100)
    private String airline;

    @Nationalized
    @Column(name = "FLIGHT_NUMBER", length = 50)
    private String flightNumber;

    @Nationalized
    @Column(name = "TICKET_NUMBER", length = 100)
    private String ticketNumber;

    /** The student's own ticket cost. Family members carry their own cost; TOTAL_TICKET_COST is the sum. */
    @Column(name = "TICKET_COST", precision = 18, scale = 2)
    private BigDecimal ticketCost;

    /** Student cost + sum of family member costs, computed on create/update. */
    @Column(name = "TOTAL_TICKET_COST", precision = 18, scale = 2)
    private BigDecimal totalTicketCost;

    /** Single currency for all costs on this ticket. */
    @Column(name = "CURRENCY_ID")
    private Integer currencyId;

    /** FK to MOHECORE.LU_TRAVEL_FUNDING_SOURCE — where the money comes from (Wafar/Ministry/Other). */
    @Column(name = "FUNDING_SOURCE_ID")
    private Integer fundingSourceId;

    @Column(name = "BOOKING_DATE")
    private LocalDate bookingDate;

    @Nationalized
    @Column(name = "TRAVEL_PURPOSE", length = 500)
    private String travelPurpose;

    @Nationalized
    @Lob
    @Column(name = "NOTES")
    private String notes;

    @Column(name = "CREATED_DATE")
    private LocalDateTime createdDate;

    @Nationalized
    @Column(name = "CREATED_BY", length = 100)
    private String createdBy;

    @Column(name = "MODIFIED_DATE")
    private LocalDateTime modifiedDate;

    @Nationalized
    @Column(name = "MODIFIED_BY", length = 100)
    private String modifiedBy;

    @Column(name = "EXTENSION_ID")
    private Integer extensionId;

    /**
     * The ticket itself, a PDF in the student's documents (V116, review T3). Uploaded
     * before the ticket is submitted, since the ticket row only exists once approved;
     * the id rides in the approval payload with the other fields.
     */
    @Column(name = "DOCUMENT_ID")
    private Integer documentId;

    @OneToMany(mappedBy = "travelTicket", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<StudentTravelTicketFamilyMember> familyMembers;

    /**
     * Not persisted — carried in the approval payload so the pending-approval view can show
     * who the ticket is for. Resolved server-side from {@link #studentId}.
     */
    @Transient
    private String studentName;
}

