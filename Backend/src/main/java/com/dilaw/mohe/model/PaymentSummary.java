package com.dilaw.mohe.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Immutable;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@Entity
@Immutable
@Table(name = "V_Payment_Summary", schema = "MOHECORE")
public class PaymentSummary {

    /** STUDENT_FINANCE.STUDENT_FIN_ID - a reference typed in by the user, which may be blank or shared. */
    @Column(name = "payment_id")
    private Integer paymentId;

    /**
     * The row's key: STUDENT_FINANCE's primary key is STUDENT_ID. Keying on payment_id
     * made Hibernate return null for a blank reference and fold rows sharing one (R5-05).
     */
    @Id
    @Column(name = "student_id")
    private Integer studentId;
    
    @Column(name = "student_name")
    private String studentName;
    
    @Column(name = "student_passport_number")
    private String studentPassportNumber;
    
    @Column(name = "payment_date")
    private LocalDate paymentDate;
    
    @Column(name = "payment_amount")
    private BigDecimal paymentAmount;
    
    @Column(name = "payment_currency")
    private String paymentCurrency;
    
    /** The same name in English; resolved by BilingualRows for an EN render. */
    @Column(name = "payment_currency_en")
    private String paymentCurrencyEn;
    
    @Column(name = "payment_status")
    private String paymentStatus;
    
    /** The same name in English; resolved by BilingualRows for an EN render. */
    @Column(name = "payment_status_en")
    private String paymentStatusEn;

    /** STUDY_FIN_RECEIVED_STATUS.CODE - the key the screen colours and groups by. */
    @Column(name = "payment_status_code")
    private String paymentStatusCode;
    
    @Column(name = "payment_type")
    private String paymentType;
    
    /** The same name in English; resolved by BilingualRows for an EN render. */
    @Column(name = "payment_type_en")
    private String paymentTypeEn;
    
    @Column(name = "payment_method")
    private String paymentMethod;
    
    @Column(name = "quarter_name")
    private String quarterName;
    
    /** The same name in English; resolved by BilingualRows for an EN render. */
    @Column(name = "quarter_name_en")
    private String quarterNameEn;
    
    @Column(name = "year_name")
    private Integer yearName;
    
    @Column(name = "university_name")
    private String universityName;
    
    /** The same name in English; resolved by BilingualRows for an EN render. */
    @Column(name = "university_name_en")
    private String universityNameEn;
    
    @Column(name = "major_name")
    private String majorName;
    
    /** The same name in English; resolved by BilingualRows for an EN render. */
    @Column(name = "major_name_en")
    private String majorNameEn;
    
    @Column(name = "payment_year")
    private Integer paymentYear;
    
    @Column(name = "payment_month")
    private Integer paymentMonth;
}

