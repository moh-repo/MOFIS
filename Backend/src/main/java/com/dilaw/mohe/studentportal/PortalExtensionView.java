package com.dilaw.mohe.studentportal;

import com.dilaw.mohe.model.StudentExtension;
import com.dilaw.mohe.service.StudentExtensionLocalizationService;
import com.fasterxml.jackson.annotation.JsonUnwrapped;

/**
 * One of the student's extensions as the portal shows it: the row as stored, its
 * type and status resolved to their lookup codes, and both named in the reader's
 * language.
 *
 * <p>EXTENSION_TYPE and EXTENSION_STATUS hold whatever the staff form's option value
 * was at the time — a legacy English name ("Academic Extension", "In Progress") or a
 * code ("REGULAR", "APPROVED") — and the portal printed the status as it stood in both
 * languages (QA SP-009). The codes let the portal name both from the shared
 * {@code extension-type} / {@code extension-status} code lists, which follow its
 * in-place language switch; the labels are the server's reading in the request
 * language, for a client whose code lists did not load. A value no active lookup row
 * matches has a null code and is labelled as itself.
 */
public class PortalExtensionView {

    @JsonUnwrapped
    private final StudentExtension extension;

    private final String extensionTypeCode;
    private final String extensionTypeLabel;
    private final String extensionStatusCode;
    private final String extensionStatusLabel;

    public PortalExtensionView(StudentExtension extension, String extensionTypeCode, String extensionTypeLabel,
                               String extensionStatusCode, String extensionStatusLabel) {
        this.extension = extension;
        this.extensionTypeCode = extensionTypeCode;
        this.extensionTypeLabel = extensionTypeLabel;
        this.extensionStatusCode = extensionStatusCode;
        this.extensionStatusLabel = extensionStatusLabel;
    }

    /** The row through the lookup tables as loaded for one request. */
    public static PortalExtensionView of(StudentExtension extension, StudentExtensionLocalizationService.Localizer names) {
        return new PortalExtensionView(extension,
                names.typeCode(extension.getExtensionType()), names.typeLabel(extension.getExtensionType()),
                names.statusCode(extension.getExtensionStatus()), names.statusLabel(extension.getExtensionStatus()));
    }

    public StudentExtension getExtension() {
        return extension;
    }

    public String getExtensionTypeCode() {
        return extensionTypeCode;
    }

    public String getExtensionTypeLabel() {
        return extensionTypeLabel;
    }

    public String getExtensionStatusCode() {
        return extensionStatusCode;
    }

    public String getExtensionStatusLabel() {
        return extensionStatusLabel;
    }
}
