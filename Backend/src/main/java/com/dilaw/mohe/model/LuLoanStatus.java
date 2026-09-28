package com.dilaw.mohe.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "LU_LOAN_STATUS", schema = "MOHECORE")
public class LuLoanStatus extends AbstractLuLookup {
}
