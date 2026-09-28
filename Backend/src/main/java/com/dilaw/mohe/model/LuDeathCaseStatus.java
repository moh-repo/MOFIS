package com.dilaw.mohe.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** Code list LU_DEATH_CASE_STATUS (V074); shape, colours and system flag come from {@link AbstractLuLookup}. */
@Entity
@Table(name = "LU_DEATH_CASE_STATUS", schema = "MOHECORE")
public class LuDeathCaseStatus extends AbstractLuLookup {
}
