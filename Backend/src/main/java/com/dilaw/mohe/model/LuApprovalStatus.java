package com.dilaw.mohe.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "LU_APPROVAL_STATUS", schema = "MOHECORE")
public class LuApprovalStatus extends AbstractLuLookup {
}
