package com.dilaw.mohe.config;

/**
 * A row that belongs to one staff account, for {@link FieldPermission#ownerVisible()}:
 * the account always sees its own masked fields.
 */
public interface OwnedByUser {

    Long ownerUserId();
}
