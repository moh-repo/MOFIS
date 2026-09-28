package com.dilaw.mohe.accounting.model;

import com.dilaw.mohe.codes.FeeTypeCode;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Nationalized;

/**
 * A stream the budget divides into: stipend, books, travel, university fees, other.
 *
 * <p>{@link #sourceTable} names the table that already records that stream, so
 * the ledger round knows where to read the detail from. Those names are facts
 * about this database, not a taxonomy invented here.
 */
@Getter
@Setter
@Entity
@Table(name = "FEE_TYPE", schema = "FINCORE")
public class FeeType {

    public static final String STIPEND = FeeTypeCode.STIPEND;
    public static final String BOOKS = FeeTypeCode.BOOKS;
    public static final String TRAVEL = FeeTypeCode.TRAVEL;
    public static final String UNIVERSITY_FEE = FeeTypeCode.UNIVERSITY_FEE;
    public static final String OTHER = FeeTypeCode.OTHER;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "FEE_TYPE_ID", nullable = false)
    private Integer id;

    @Column(name = "FEE_TYPE_CODE", nullable = false, length = 30)
    private String feeTypeCode;

    @Nationalized
    @Column(name = "NAME_AR", nullable = false, length = 150)
    private String nameAr;

    @Nationalized
    @Column(name = "NAME_EN", nullable = false, length = 150)
    private String nameEn;

    @Nationalized
    @Column(name = "SOURCE_TABLE", length = 128)
    private String sourceTable;

    @Column(name = "IS_ACTIVE", nullable = false)
    private Boolean active = true;
}
