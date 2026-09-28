package com.dilaw.mohe.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Nationalized;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "STUDENT_TRAVEL_TICKET_FAMILY_MEMBER", schema = "MOHECORE")
public class StudentTravelTicketFamilyMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "FAMILY_MEMBER_ID", nullable = false)
    private Integer familyMemberId;

    @Column(name = "TRAVEL_TICKET_ID", nullable = false)
    private Integer travelTicketId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "TRAVEL_TICKET_ID", insertable = false, updatable = false)
    @JsonIgnore
    private StudentTravelTicket travelTicket;

    @Nationalized
    @Column(name = "MEMBER_NAME", nullable = false, length = 255)
    private String memberName;

    @Nationalized
    @Column(name = "RELATIONSHIP", length = 50)
    private String relationship; // 'SPOUSE', 'CHILD', 'PARENT', etc.

    @Column(name = "BIRTHDATE", nullable = false)
    private LocalDate birthdate;

    @Column(name = "AGE")
    private Integer age; // Calculated from birthdate

    /** This family member's own ticket cost (same currency as the ticket). */
    @Column(name = "TICKET_COST", precision = 18, scale = 2)
    private BigDecimal ticketCost;

    @Nationalized
    @Column(name = "PASSPORT_NUMBER", length = 100)
    private String passportNumber;

    @Column(name = "PASSPORT_EXPIRY_DATE")
    private LocalDate passportExpiryDate;

    @Column(name = "GENDER")
    private Integer gender;

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

    /**
     * Calculate age from birthdate
     */
    @PrePersist
    @PreUpdate
    public void calculateAge() {
        if (birthdate != null) {
            LocalDate now = LocalDate.now();
            this.age = now.getYear() - birthdate.getYear();
            if (now.getDayOfYear() < birthdate.getDayOfYear()) {
                this.age--;
            }
        }
    }
}

