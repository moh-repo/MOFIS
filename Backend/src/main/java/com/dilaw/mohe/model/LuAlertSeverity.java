package com.dilaw.mohe.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** Code list LU_ALERT_SEVERITY; shape, colours and system flag come from {@link AbstractLuLookup}. */
@Entity
@Table(name = "LU_ALERT_SEVERITY", schema = "MOHECORE")
public class LuAlertSeverity extends AbstractLuLookup {
}
