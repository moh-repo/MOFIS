package com.dilaw.mohe.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** Code list LU_APPROPRIATION_STATUS (V073); shape, colours and system flag come from {@link AbstractLuLookup}. */
@Entity
@Table(name = "LU_APPROPRIATION_STATUS", schema = "MOHECORE")
public class LuAppropriationStatus extends AbstractLuLookup {
}
