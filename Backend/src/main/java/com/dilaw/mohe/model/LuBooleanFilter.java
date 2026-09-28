package com.dilaw.mohe.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "LU_BOOLEAN_FILTER", schema = "MOHECORE")
public class LuBooleanFilter extends AbstractLuLookup {
}
