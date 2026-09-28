package com.dilaw.mohe.wafar.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Nationalized;

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "WAFAR_STUDENT_FEE_PAYMENT", schema = "MOHECORE")
public class WafarStudentFeePayment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "FEE_PAYMENT_ID", nullable = false)
    private Integer id;

    @Column(name = "SPENDING_ID", nullable = false)
    private Integer spendingId;

    @Column(name = "STUDENT_ID", nullable = false)
    private Integer studentId;

    @Column(name = "ACADEMIC_YEAR", length = 20)
    private String academicYear;

    @Nationalized
    @Column(name = "NOTES", length = 500)
    private String notes;

    @Column(name = "CREATED_AT", nullable = false)
    private Instant createdAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
