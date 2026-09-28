package com.dilaw.mohe.model;

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
@Table(name = "PAYMENT_AUTH", schema = "MOHECORE")
public class PaymentAuth {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "PAYMENT_AUTH_ID", nullable = false)
    private Integer id;

    @Nationalized
    @Column(name = "AUTH_FORM_NUMBER")
    private String authFormNumber;

    @Column(name = "AUTH_DATE")
    private LocalDate authDate;

    @Column(name = "STUDENT_ID")
    private Integer studentId;

    @Nationalized
    @Column(name = "STUDENT_NAME")
    private String studentName;

    @Nationalized
    @Column(name = "MINISTRY_NAME")
    private String ministryName;

    @Nationalized
    @Column(name = "DEPT_NAME")
    private String deptName;

    @Nationalized
    @Column(name = "PROVINCE_NAME")
    private String provinceName;

    @Nationalized
    @Column(name = "BANK_NAME")
    private String bankName;

    @Nationalized
    @Column(name = "BANK_BRANCH")
    private String bankBranch;

    @Nationalized
    @Column(name = "SENT_TO")
    private String sentTo;

    /** FINANCE | WAFAR | UNIVERSITY_FEE | COMBINATION | ALL */
    @Column(name = "AMOUNT_SOURCE")
    private String amountSource;

    @Column(name = "REF_FINANCE_ID")
    private Integer refFinanceId;

    @Column(name = "REF_WAFAR_ID")
    private Integer refWafarId;

    @Column(name = "REF_UNIVERSITY_FEE_ID")
    private Integer refUniversityFeeId;

    @Column(name = "ORIGINAL_AMOUNT", precision = 18, scale = 2)
    private BigDecimal originalAmount;

    @Column(name = "DEDUCTIONS", precision = 18, scale = 2)
    private BigDecimal deductions;

    @Column(name = "NET_AMOUNT", precision = 18, scale = 2)
    private BigDecimal netAmount;

    @Nationalized
    @Column(name = "PAYMENT_PURPOSE")
    private String paymentPurpose;

    @Nationalized
    @Column(name = "CHECK_NUMBER")
    private String checkNumber;

    @Column(name = "CHECK_DATE")
    private LocalDate checkDate;

    @Nationalized
    @Column(name = "BUDGET_BOOK_NUMBER")
    private String budgetBookNumber;

    @Nationalized
    @Column(name = "RECIPIENT_NAME")
    private String recipientName;

    @Nationalized
    @Column(name = "RECIPIENT_ID_NUMBER")
    private String recipientIdNumber;

    /** DRAFT | APPROVED | PAID */
    @Column(name = "AUTH_STATUS")
    private String authStatus;

    @Nationalized
    @Lob
    @Column(name = "NOTES")
    private String notes;

    @Column(name = "CREATED_BY")
    private Integer createdBy;

    /**
     * Who released the payment ({@code bulkAuthorize}): a UMS user id. BIGINT
     * rather than {@link #createdBy}'s INT because V065 gave it a foreign key to
     * {@code UMSCORE.Users(Id)}, which is BIGINT. Until V065 the service computed
     * the authoriser and returned it in the response without ever writing it, so
     * the record of who approved a payment was a log line.
     */
    @Column(name = "AUTHORIZED_BY")
    private Long authorizedBy;

    @Column(name = "AUTH_MIDATE")
    private Instant authMidate;
}
