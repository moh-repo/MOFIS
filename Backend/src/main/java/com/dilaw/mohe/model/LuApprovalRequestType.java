package com.dilaw.mohe.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "LU_APPROVAL_REQUEST_TYPE", schema = "MOHECORE")
public class LuApprovalRequestType extends AbstractLuLookup {
}
