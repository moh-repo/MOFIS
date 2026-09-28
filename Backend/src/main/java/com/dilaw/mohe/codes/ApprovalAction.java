package com.dilaw.mohe.codes;

/** LU_APPROVAL_ACTION (V073): ApprovalHistory.action as ApprovalWorkflowService records it. */
@CodeList(value = "approval-action", mode = CodeList.Mode.EXACT)
public final class ApprovalAction {

    private ApprovalAction() {
    }

    public static final String CREATED = "CREATED";
    public static final String SUBMITTED = "SUBMITTED";
    public static final String UPDATED = "UPDATED";
    public static final String APPROVED = "APPROVED";
    public static final String REJECTED = "REJECTED";
    public static final String EXECUTED = "EXECUTED";
}
