package com.dilaw.mohe.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "LU_PERMISSION_ACTION_TYPE", schema = "MOHECORE")
public class LuPermissionActionType extends AbstractLuLookup {
}
