package com.dilaw.mohe.wafar.model;

import com.dilaw.mohe.codes.WafarSpendingStatus;
import com.dilaw.mohe.codes.WafarSpendingStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Nationalized;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Getter
@Setter
@Entity
@Table(name = "WAFAR_SPENDING", schema = "MOHECORE")
public class WafarSpending {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "SPENDING_ID", nullable = false)
    private Integer id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "CATEGORY_ID", nullable = false)
    private WafarSpendingCategory category;

    @Column(name = "AMOUNT", nullable = false, precision = 18, scale = 2)
    private BigDecimal amount;

    @Column(name = "SPENDING_DATE", nullable = false)
    private LocalDate spendingDate;

    @Column(name = "REFERENCE_NUMBER", length = 100)
    private String referenceNumber;

    @Nationalized
    @Column(name = "DESCRIPTION_AR", length = 500)
    private String descriptionAr;

    @Column(name = "DESCRIPTION_EN", length = 500)
    private String descriptionEn;

    @Nationalized
    @Column(name = "PAYEE_NAME_AR", length = 255)
    private String payeeNameAr;

    @Column(name = "PAYEE_NAME_EN", length = 255)
    private String payeeNameEn;

    @Column(name = "STUDENT_ID")
    private Integer studentId;

    /**
     * The attaché whose Wafar this was spent from. Wafar stays at the attaché that
     * holds it, so this is what decides who may see and approve the transaction.
     *
     * <p>The column existed but was never mapped, so nothing set it and nothing
     * read it: 11 of the 17 rows are NULL. Those eleven are attaché running costs
     * — rent, salaries, transport — with no student and no ticket to derive it
     * from, so V046 left them alone and they are ministry-only. New rows get it
     * stamped in {@code WafarSpendingService.create}.
     */
    @Column(name = "STUDY_COUNTRY_IN_ID")
    private Integer studyCountryInId;

    /** Who the money goes to: STUDENT, MINISTRY, CULTURAL_AFFAIRS or OTHER. */
    @Column(name = "BENEFICIARY_TYPE", length = 30)
    private String beneficiaryType;

    /** Set when this spending was created from a student travel ticket, so it can be updated/reversed. */
    @Column(name = "TRAVEL_TICKET_ID")
    private Integer travelTicketId;

    @Column(name = "STATUS", nullable = false, length = 20)
    private String status = WafarSpendingStatus.PENDING;

    @Nationalized
    @Column(name = "NOTES", length = 1000)
    private String notes;

    @Column(name = "CREATED_BY")
    private Integer createdBy;

    @Column(name = "CREATED_AT", nullable = false)
    private Instant createdAt;

    @Column(name = "APPROVED_BY")
    private Integer approvedBy;

    @Column(name = "APPROVED_AT")
    private Instant approvedAt;

    @Nationalized
    @Column(name = "REJECTION_REASON", length = 500)
    private String rejectionReason;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (status == null) {
            status = WafarSpendingStatus.PENDING;
        }
    }
}
