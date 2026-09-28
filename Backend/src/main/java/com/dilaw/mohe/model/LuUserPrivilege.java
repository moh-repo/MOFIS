package com.dilaw.mohe.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "LU_USER_PRIVILEGE", schema = "MOHECORE")
public class LuUserPrivilege extends AbstractLuLookup {
}
