package com.dilaw.mohe.notification.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class SendMessageRequest {

    /**
     * The channels to send on: any of EMAIL, SMS, WHATSAPP. Each becomes its own
     * history row under one batch id.
     */
    private List<String> channels;

    /**
     * The single-channel form older clients send: EMAIL | SMS | WHATSAPP | BOTH
     * (e-mail and SMS). Used only when {@link #channels} is empty.
     */
    private String channel;

    /** STUDENT | EMPLOYEE */
    @NotNull
    private String recipientType;

    /** ONE | GROUP | ALL */
    @NotNull
    private String scope;

    /** When scope=ONE: the student id or UMS user id. This or {@link #recipientEmail} is required. */
    private Integer recipientId;

    /**
     * When scope=ONE and no id is given: the email of a student or employee on file
     * (review R4). It is looked up, not sent to as typed, so the caller's country
     * scope still decides who can be reached.
     */
    private String recipientEmail;

    /** Optional filters when scope=GROUP and recipientType=STUDENT */
    private StudentFilterDto studentFilter;

    /** Optional filters when scope=GROUP and recipientType=EMPLOYEE */
    private EmployeeFilterDto employeeFilter;

    /** Required when EMAIL is among the channels */
    private String subject;

    @NotBlank
    private String body;
}
