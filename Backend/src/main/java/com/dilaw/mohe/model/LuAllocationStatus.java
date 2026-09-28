package com.dilaw.mohe.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** Code list LU_ALLOCATION_STATUS (V073); shape, colours and system flag come from {@link AbstractLuLookup}. */
@Entity
@Table(name = "LU_ALLOCATION_STATUS", schema = "MOHECORE")
public class LuAllocationStatus extends AbstractLuLookup {
}
