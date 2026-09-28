package com.dilaw.mohe.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "LU_HTTP_METHOD", schema = "MOHECORE")
public class LuHttpMethod extends AbstractLuLookup {
}
