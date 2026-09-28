package com.dilaw.mohe.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.Nationalized;

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "STUDY_FIN_RECEIVED_WHOM", schema = "MOHECORE")
public class StudyFinReceivedWhom {
    /**
     * Not an IDENTITY column in SQL Server. It used to be assigned as
     * {@code MAX(id) + 1}, which hands a deleted recipient's id to the next one
     * created — and with it every attachment still keyed to it, so one person's
     * passport scans appeared under another's name. V130 gives it a sequence
     * (plan W6-09); a number, once issued, is never issued again.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "studyFinReceivedWhomSeq")
    @SequenceGenerator(name = "studyFinReceivedWhomSeq", schema = "MOHECORE",
            sequenceName = "SEQ_STUDY_FIN_RECEIVED_WHOM", allocationSize = 1)
    @Column(name = "STUDY_RECEIVED_WHOM_ID", nullable = false)
    private Integer id;

    @Nationalized
    @Column(name = "FIN_RECEIVED_NAME")
    private String finReceivedName;

    @Nationalized
    @Column(name = "FIN_RECEIVED_PASSPORT_NUMBER")
    private String finReceivedPassportNumber;

    @Column(name = "FIN_RECEIVED_PASSPORT_ISSUE_DATE")
    private Instant finReceivedPassportIssueDate;

    @Column(name = "FIN_RECEIVED_PASSPORT_EXPIRE_DATE")
    private Instant finReceivedPassportExpireDate;

    @ColumnDefault("0")
    @Column(name = "FIN_RECEIVED_ISSUE_COUNTRY")
    private Integer finReceivedIssueCountry;

    @ColumnDefault("0")
    @Column(name = "FIN_RECIEVED_YEAR")
    private Integer finRecievedYear;

    @ColumnDefault("0")
    @Column(name = "FIN_RECIEVED_MONTH")
    private Integer finRecievedMonth;

    @ColumnDefault("0")
    @Column(name = "FIN_RECIEVED_YEAR_MONTH")
    private Integer finRecievedYearMonth;

    @ColumnDefault("0")
    @Column(name = "FIN_RECIEVED_AMOUNT")
    private Integer finRecievedAmount;

    @ColumnDefault("0")
    @Column(name = "FINANCE_COUNTRY_ID")
    private Integer financeCountryId;

    @ColumnDefault("0")
    @Column(name = "FINANCE_GOV_ID")
    private Integer financeGovId;

    @ColumnDefault("0")
    @Column(name = "FINANCE_BANKE_ID")
    private Integer financeBankeId;

    @ColumnDefault("0")
    @Column(name = "FINANCE_BANK_BRANCH_ID")
    private Integer financeBankBranchId;

    @ColumnDefault("0")
    @Column(name = "FINANCE_DOF_ID")
    private Integer financeDofId;

    @Nationalized
    @Column(name = "CHECK_ID")
    private String checkId;

    @Column(name = "CHECK_DATE")
    private Instant checkDate;

    @Nationalized
    @Lob
    @Column(name = "CHECK_DETAILS")
    private String checkDetails;

    @Nationalized
    @Column(name = "CHECK_FORM_NUMBER")
    private String checkFormNumber;

    @ColumnDefault("0")
    @Column(name = "FIN_RECIEVED_STATUS")
    private Integer finRecievedStatus;

    @Nationalized
    @Lob
    @Column(name = "NOTE_1")
    private String note1;

    @Nationalized
    @Lob
    @Column(name = "FIN_RECEIVED_REASON")
    private String finReceivedReason;

    @Nationalized
    @Column(name = "FIN_RECEIVED_RELATIONSHIP")
    private String finReceivedRelationship;

    @Nationalized
    @Column(name = "FIN_MANUAL_FORM_NUMBER")
    private String finManualFormNumber;

    @Column(name = "FIN_MANUAL_FORM_DATE")
    private Instant finManualFormDate;

    @Column(name = "STUDENT_FOR_DELEGATION_ID")
    private Integer studentForDelegationId;

    @Column(name = "LETTER_REASON_ID")
    private Integer letterReasonId;

    @Column(name = "FINANCE_PAYMENT_ID")
    private Long financePaymentId;

    @ColumnDefault("getdate()")
    @Column(name = "STUDY_RECEIVED_WHOM_MIDATE", insertable = false, updatable = false)
    private Instant studyReceivedWhomMidate;

}