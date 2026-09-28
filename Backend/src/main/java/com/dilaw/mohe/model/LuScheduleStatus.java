package com.dilaw.mohe.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** Code list LU_SCHEDULE_STATUS (V074); shape, colours and system flag come from {@link AbstractLuLookup}. */
@Entity
@Table(name = "LU_SCHEDULE_STATUS", schema = "MOHECORE")
public class LuScheduleStatus extends AbstractLuLookup {
}
