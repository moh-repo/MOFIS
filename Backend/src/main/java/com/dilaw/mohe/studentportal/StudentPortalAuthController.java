package com.dilaw.mohe.studentportal;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

// Legacy auth controller — superseded by StudentUserAuthController (STUDENTCORE-based auth).
// @RestController removed to avoid URL mapping conflicts. Legacy session compatibility is
// handled transparently in StudentPortalTokenInterceptor via fallback to StudentPortalAuthService.
@org.springframework.stereotype.Component
public class StudentPortalAuthController {

    @Autowired
    private StudentPortalAuthService authService;

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody Map<String, String> body) {
        String loginKey = body.get("loginKey");
        String password = body.get("password");
        return authService.login(loginKey, password)
                .map(r -> ResponseEntity.ok(Map.of(
                        "token", r.getToken(),
                        "studentId", r.getStudentId(),
                        "studentName", r.getStudentName()
                )))
                .orElse(ResponseEntity.status(401).body(Map.of("error", "Invalid login key or password")));
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(@RequestHeader(value = StudentPortalTokenInterceptor.HEADER_TOKEN, required = false) String token) {
        authService.logout(token);
        return ResponseEntity.ok(Map.of("message", "Logged out"));
    }

    /** Step 1: Request OTP for the given loginKey. Returns a masked email hint. */
    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgotPassword(@RequestBody Map<String, String> body) {
        String loginKey = body.get("loginKey");
        String message = authService.requestPasswordReset(loginKey);
        return ResponseEntity.ok(Map.of("message", message));
    }

    /** Step 2: Validate OTP and set new password. */
    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(@RequestBody Map<String, String> body) {
        String loginKey = body.get("loginKey");
        String otp      = body.get("otp");
        String password = body.get("newPassword");
        authService.resetPassword(loginKey, otp, password);
        return ResponseEntity.ok(Map.of("message", "Password reset successfully. You can now log in."));
    }
}
