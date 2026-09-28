package com.dilaw.mohe.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.Nationalized;

import java.time.Instant;

/**
 * Bean constraints (plan item 2.6): the key is the student id and is the only
 * NOT NULL column, but the update route sets it from the path, so it is not
 * {@code @NotNull} here; the amounts cannot be negative.
 */
@Getter
@Setter
@Entity
@Table(name = "STUDENT_FINANCE_WAFAR", schema = "MOHECORE")
public class StudentFinanceWafar {

    @Id
    @ColumnDefault("0")
    @Column(name = "STUDENT_ID", nullable = false)
    private Integer id;

    @Nationalized
    @Column(name = "STUDENT_FIN_ID")
    private String studentFinId;

    @Nationalized
    @Column(name = "STUDENT_FIN_NAME")
    private String studentFinName;

    @ColumnDefault("0")
    @Column(name = "STUDENT_FIN_RECIEVED_YEAR")
    private Integer studentFinRecievedYear;

    @ColumnDefault("0")
    @Column(name = "STUDENT_FIN_RECIEVED_MONTH")
    private Integer studentFinRecievedMonth;

    @ColumnDefault("0")
    @Column(name = "STUDENT_FIN_RECIEVED_YEAR_MONTH")
    private Integer studentFinRecievedYearMonth;

    @PositiveOrZero
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
    @Column(name = "WAFAR_TYPE_ID")
    private Integer wafarTypeId;

    @Nationalized
    @Lob
    @Column(name = "STUDY_NOTE_1")
    private String studyNote1;

    @Column(name = "STUDY_FINANCE_MIDATE")
    private Instant studyFinanceMidate;
}
