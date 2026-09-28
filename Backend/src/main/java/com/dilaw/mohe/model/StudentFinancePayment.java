package com.dilaw.mohe.model;

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
import org.hibernate.annotations.Nationalized;

import java.time.Instant;

/** Payment / disbursement history per student (multiple rows per student). */
@Getter
@Setter
@Entity
@Table(name = "STUDENT_FINANCE_PAYMENT", schema = "MOHECORE")
public class StudentFinancePayment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "PAYMENT_ID", nullable = false)
    private Long paymentId;

    @Column(name = "STUDENT_ID", nullable = false)
    private Integer studentId;

    @Column(name = "MOHE_QUARTER_ID")
    private Integer moheQuarterId;

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

    @ColumnDefault("0")
    @Column(name = "STUDY_FIN_RECIEVED_STATUS")
    private Integer studyFinRecievedStatus;

    @ColumnDefault("0")
    @Column(name = "STUDY_RECEIVED_WHOM_STATUS_ID")
    private Integer studyReceivedWhomStatusId;

    @ColumnDefault("0")
    @Column(name = "STUDY_RECEIVED_WHOM_ID")
    private Integer studyReceivedWhomId;

    @Nationalized
    @Lob
    @Column(name = "PAYMENT_NOTES")
    private String paymentNotes;

    @Column(name = "INSERTED_AT")
    private Instant insertedAt = Instant.now();

    /**
     * The table defaults these NOT NULL columns to 0 (the name to ''), but a default only
     * applies to a column the INSERT leaves out, and Hibernate lists every mapped column.
     * A null field is therefore an INSERT of NULL into a NOT NULL column. That is how
     * approving a wizard-created student failed at its last level (request 20868): the
     * onboarding draft has no payment status, bank or received-by yet. Quarter close
     * writes through here too, from finance rows that mostly have no status.
     *
     * <p>STUDY_COUNTRY_IN_ID is left alone: it has a foreign key, so 0 would only trade
     * one error for another, and a finance row always names its attaché.
     */
    @PrePersist
    void applyColumnDefaults() {
        if (studentFinName == null) studentFinName = "";
        studentFinRecievedYear = zeroIfNull(studentFinRecievedYear);
        studentFinRecievedMonth = zeroIfNull(studentFinRecievedMonth);
        studentFinRecievedYearMonth = zeroIfNull(studentFinRecievedYearMonth);
        studentFinRecievedAmount = zeroIfNull(studentFinRecievedAmount);
        studyGovInId = zeroIfNull(studyGovInId);
        studyFinanceBankeId = zeroIfNull(studyFinanceBankeId);
        studyFinanceBankBranchId = zeroIfNull(studyFinanceBankBranchId);
        studyFinanceDofId = zeroIfNull(studyFinanceDofId);
        studyFinRecievedStatus = zeroIfNull(studyFinRecievedStatus);
        studyReceivedWhomStatusId = zeroIfNull(studyReceivedWhomStatusId);
        studyReceivedWhomId = zeroIfNull(studyReceivedWhomId);
        if (insertedAt == null) insertedAt = Instant.now();
    }

    private static Integer zeroIfNull(Integer v) {
        return v != null ? v : 0;
    }
}
