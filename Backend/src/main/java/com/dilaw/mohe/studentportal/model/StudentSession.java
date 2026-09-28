package com.dilaw.mohe.studentportal.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "student_sessions", schema = "STUDENTCORE")
public class StudentSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "session_id")
    private Integer sessionId;

    @Column(name = "student_user_id", nullable = false)
    private Integer studentUserId;

    @Column(name = "student_id", nullable = false)
    private Integer studentId;

    @Column(name = "token", nullable = false, unique = true, length = 100)
    private String token;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "logged_out_at")
    private Instant loggedOutAt;

    /**
     * The student's study country as it was when this session was issued (V036).
     *
     * <p>STUDENTCORE holds no country of its own, so before this the portal had to
     * read STUDENT_DATA under elevation on every request to learn which country
     * the caller belonged to. It is pinned here instead: the interceptor already
     * loads this row to validate the token, so the scope costs nothing extra.
     *
     * <p>Deliberately a snapshot, not a live view. A student moving country
     * mid-session should end that session rather than silently move its scope;
     * sessions last {@code SESSION_DAYS}, so the window is bounded.
     */
    @Column(name = "study_country_in_id")
    private Integer studyCountryInId;
}
