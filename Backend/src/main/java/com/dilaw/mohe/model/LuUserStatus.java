package com.dilaw.mohe.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "LU_USER_STATUS", schema = "MOHECORE")
public class LuUserStatus extends AbstractLuLookup {
}
