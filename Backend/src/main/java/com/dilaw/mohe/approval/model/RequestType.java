package com.dilaw.mohe.approval.model;

import com.dilaw.mohe.codes.CodeList;

/**
 * The operation an approval request carries, persisted as a string. Names
 * live in {@code MOHECORE.LU_APPROVAL_REQUEST_TYPE}.
 */
@CodeList("request-type")
public enum RequestType {
    INSERT,
    UPDATE,
    DELETE
}
