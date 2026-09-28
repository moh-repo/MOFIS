package com.dilaw.mohe.codes;

/** EXTENSION_STATUS.EXTENSION_STATUS_CODE: the buckets of the extension statistics report. */
@CodeList(value = "extension-status", mode = CodeList.Mode.SUBSET)
public final class ExtensionStatusCode {

    private ExtensionStatusCode() {
    }

    public static final String PENDING = "PENDING";
    public static final String APPROVED = "APPROVED";
    public static final String REJECTED = "REJECTED";
}
