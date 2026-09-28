package com.dilaw.mohe.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** Code list LU_RUN_STATUS (V074); shape, colours and system flag come from {@link AbstractLuLookup}. */
@Entity
@Table(name = "LU_RUN_STATUS", schema = "MOHECORE")
public class LuRunStatus extends AbstractLuLookup {
}
