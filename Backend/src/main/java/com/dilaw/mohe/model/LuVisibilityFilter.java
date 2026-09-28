package com.dilaw.mohe.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "LU_VISIBILITY_FILTER", schema = "MOHECORE")
public class LuVisibilityFilter extends AbstractLuLookup {
}
