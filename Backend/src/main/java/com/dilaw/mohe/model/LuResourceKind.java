package com.dilaw.mohe.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "LU_RESOURCE_KIND", schema = "MOHECORE")
public class LuResourceKind extends AbstractLuLookup {
}
