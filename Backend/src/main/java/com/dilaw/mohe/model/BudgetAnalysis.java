package com.dilaw.mohe.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Immutable;

import java.math.BigDecimal;

@Getter
@Setter
@Entity
@Immutable
@Table(name = "V_Budget_Analysis", schema = "MOHECORE")
public class BudgetAnalysis {

    @Id
    @Column(name = "budget_id")
    private Long budgetId;

    @Column(name = "budget_category")
    private String budgetCategory;

    @Column(name = "allocated_amount")
    private BigDecimal allocatedAmount;

    @Column(name = "spent_amount")
    private BigDecimal spentAmount;

    @Column(name = "remaining_amount")
    private BigDecimal remainingAmount;

    @Column(name = "utilization_percentage")
    private Integer utilizationPercentage;

    @Column(name = "budget_year")
    private Integer budgetYear;

    @Column(name = "budget_quarter")
    private Integer budgetQuarter;

    @Column(name = "study_country_in_id")
    private Integer studyCountryInId;
}

