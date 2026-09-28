package com.dilaw.mohe.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.Nationalized;
import org.hibernate.generator.EventType;

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "STUDENT_FINANCE_ARCH", schema = "MOHECORE")
public class StudentFinanceArch {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "FIN_ARCH_ENTRY_ID", nullable = false)
    private Long financeArchEntryId;

    @Column(name = "STUDENT_ID", nullable = false)
    private Integer studentId;

    @Nationalized
    @Column(name = "STUDENT_FIN_ID")
    private String studentFinId;

    @Nationalized
    @Column(name = "STUDENT_FIN_NAME", nullable = false)
    private String studentFinName;

    @ColumnDefault("0")
    @Column(name = "STUDENT_FIN_RECIEVED_YEAR", nullable = false)
    private Integer studentFinRecievedYear;

    @ColumnDefault("0")
    @Column(name = "STUDENT_FIN_RECIEVED_MONTH", nullable = false)
    private Integer studentFinRecievedMonth;

    @ColumnDefault("0")
    @Column(name = "STUDENT_FIN_RECIEVED_YEAR_MONTH", nullable = false)
    private Integer studentFinRecievedYearMonth;

    @ColumnDefault("0")
    @Column(name = "STUDENT_FIN_RECIEVED_AMOUNT")
    private Integer studentFinRecievedAmount;

    @ColumnDefault("0")
    @Column(name = "STUDY_COUNTRY_IN_ID")
    private Integer studyCountryInId;

    @ColumnDefault("0")
    @Column(name = "STUDY_GOV_IN_ID")
    private Integer studyGovInId;

    @ColumnDefault("0")
    @Column(name = "STUDY_FINANCE_BANKE_ID")
    private Integer studyFinanceBankeId;

    @ColumnDefault("0")
    @Column(name = "STUDY_FINANCE_BANK_BRANCH_ID")
    private Integer studyFinanceBankBranchId;

    @ColumnDefault("0")
    @Column(name = "STUDY_FINANCE_DOF_ID")
    private Integer studyFinanceDofId;

    @Nationalized
    @Column(name = "STUDY_CHECK_ID")
    private String studyCheckId;

    @Column(name = "STUDY_CHECK_DATE")
    private Instant studyCheckDate;

    @Nationalized
    @Lob
    @Column(name = "STUDY_CHECK_DETAILS")
    private String studyCheckDetails;

    @Nationalized
    @Column(name = "STUDY_CHECK_FORM_NUMBER")
    private String studyCheckFormNumber;

    @ColumnDefault("0")
    @Column(name = "STUDY_FIN_RECIEVED_STATUS")
    private Integer studyFinRecievedStatus;

    @Nationalized
    @Lob
    @Column(name = "STUDY_NOTE_1")
    private String studyNote1;

    @ColumnDefault("0")
    @Column(name = "STUDY_RECEIVED_WHOM_STATUS_ID")
    private Integer studyReceivedWhomStatusId;

    @ColumnDefault("0")
    @Column(name = "STUDY_RECEIVED_WHOM_ID")
    private Integer studyReceivedWhomId;

    @ColumnDefault("getdate()")
    @Column(name = "STUDY_FINANCE_MIDATE")
    private Instant studyFinanceMidate;

    @Column(name = "MOHE_QUARTER_ID")
    private Integer moheQuarterId;

    @ColumnDefault("getdate()")
    @Column(name = "ARCHIEVED_DATE")
    private Instant archievedDate;

    /**
     * Stamps the archive date on insert (round-5 QA R5-23). The column default never
     * fired: Hibernate names the column in its INSERT and sends NULL, so rows archived by
     * the student-delete step and by the quarter rollover had no date.
     */
    @PrePersist
    public void stampArchiveDate() {
        if (archievedDate == null) {
            archievedDate = Instant.now();
        }
    }

    @ColumnDefault("0")
    @Column(name = "WAFAR_AMOUNT")
    private Integer wafarAmount;

    @ColumnDefault("0")
    @Column(name = "WAFAR_TYPE_ID")
    private Integer wafarTypeId;

    /**
     * Net of wafar for this archived row — a PERSISTED computed column (V015), so the
     * quarter-close archive mapper never writes it. Archived rows keep the net they had
     * when they were archived, since this table carries its own amount and wafar copies.
     */
    @Generated(event = { EventType.INSERT, EventType.UPDATE })
    @Column(name = "NET_AMOUNT")
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Integer netAmount;

    @ColumnDefault("0")
    @Column(name = "STUDENT_BANK_ID")
    private Integer studentBankId;

    @Nationalized
    @Column(name = "STUDENT_ACCOUNT_NUMBER")
    private String studentAccountNumber;

    @Nationalized
    @Column(name = "STUDENT_ACCOUNT_ENGLISH_NAME")
    private String studentAccountEnglishName;

    @Nationalized
    @Column(name = "STUDENT_ACCOUNT_END_DATE")
    private String studentAccountEndDate;

    @Nationalized
    @Lob
    @Column(name = "STUDENT_BANK_NOTES")
    private String studentBankNotes;

}