package com.dilaw.mohe.studentportal;

import java.time.Instant;

public record StudentSessionDto(
        Integer sessionId,
        Integer studentUserId,
        Integer studentId,
        String  studentName,
        String  token,
        Instant expiresAt,
        Instant createdAt,
        Instant loggedOutAt
) {}
