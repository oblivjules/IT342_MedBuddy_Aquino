package com.medbuddy.controller;

import java.util.Map;
import java.util.Locale;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.medbuddy.dto.AuthResponse;
import com.medbuddy.dto.LoginRequest;
import com.medbuddy.dto.RegisterRequest;
import com.medbuddy.dto.UserDto;
import com.medbuddy.service.UserService;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private static final String OAUTH2_REDIRECT_URI = "OAUTH2_REDIRECT_URI";
    private static final String OAUTH2_PORTAL = "OAUTH2_PORTAL";

    private final UserService userService;

    @Value("${spring.security.oauth2.client.registration.google.client-id:not-configured}")
    private String googleClientId;

    @Value("${spring.security.oauth2.client.registration.google.client-secret:not-configured}")
    private String googleClientSecret;

    /**
     * Register a new user (PATIENT or DOCTOR).
     * POST /api/auth/register
     */
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(
            @Valid @RequestBody RegisterRequest request) {
        AuthResponse response = userService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Authenticate an existing user and return a JWT.
     * POST /api/auth/login
     */
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(
            @Valid @RequestBody LoginRequest request) {
        AuthResponse response = userService.login(request);
        return ResponseEntity.ok(response);
    }

    /**
     * Return the authenticated user's profile.
     * GET /api/auth/me
     */
    @GetMapping("/me")
    public ResponseEntity<UserDto> me(
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(userService.getMe(userDetails.getUsername()));
    }

    /**
     * Stateless JWT logout for clients + invalidate OAuth2 session if present.
     * POST /api/auth/logout
     */
    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout(HttpSession session) {
        session.invalidate();
        return ResponseEntity.ok(Map.of("message", "Logged out successfully"));
    }

    /**
     * Render health check — must be publicly accessible (no auth required).
     * GET /api/auth/health
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "UP"));
    }

    /**
     * OAuth2 init — stores the post-login redirect URI in the session then
     * forwards the browser to Spring Security's Google OAuth2 entry point.
     *
     * Called by both clients:
     *   Web:    GET /api/auth/oauth2/init?redirect=http://localhost:5173/oauth-callback
     *   Mobile: GET /api/auth/oauth2/init?redirect=medbuddy://oauth-callback
     *
     * GET /api/auth/oauth2/init
     */
    @GetMapping("/oauth2/init")
    public void oauth2Init(
            @RequestParam String redirect,
            @RequestParam(defaultValue = "patient") String portal,
            HttpSession session,
            HttpServletResponse response) throws java.io.IOException {
        if (!isGoogleOauthConfigured()) {
            response.sendError(
                    HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                    "Google OAuth is not configured on the server. Set GOOGLE_CLIENT_ID and GOOGLE_CLIENT_SECRET.");
            return;
        }

        String normalizedPortal = normalizePortal(portal);
        session.setAttribute(OAUTH2_REDIRECT_URI, redirect);
        session.setAttribute(OAUTH2_PORTAL, normalizedPortal);
        response.sendRedirect("/oauth2/authorization/google");
    }

    /**
     * Compatibility alias for spec endpoint.
     * GET /api/auth/oauth2/google
     */
    @GetMapping("/oauth2/google")
    public void oauth2Google(
            @RequestParam String redirect,
            @RequestParam(defaultValue = "patient") String portal,
            HttpSession session,
            HttpServletResponse response) throws java.io.IOException {
        oauth2Init(redirect, portal, session, response);
    }

    /**
     * Doctor portal convenience endpoint.
     * GET /api/auth/oauth2/doctor
     */
    @GetMapping("/oauth2/doctor")
    public void oauth2Doctor(
            @RequestParam String redirect,
            HttpSession session,
            HttpServletResponse response) throws java.io.IOException {
        oauth2Init(redirect, "doctor", session, response);
    }

    /**
     * Patient portal convenience endpoint.
     * GET /api/auth/oauth2/patient
     */
    @GetMapping("/oauth2/patient")
    public void oauth2Patient(
            @RequestParam String redirect,
            HttpSession session,
            HttpServletResponse response) throws java.io.IOException {
        oauth2Init(redirect, "patient", session, response);
    }

    /**
     * Reserved endpoint for mobile token-exchange OAuth flows.
     * POST /api/auth/oauth2/google/token
     */
    @PostMapping("/oauth2/google/token")
    public ResponseEntity<Map<String, String>> oauth2GoogleToken() {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).body(
                Map.of("message", "Google token exchange is not enabled on this server."));
    }

    private boolean isGoogleOauthConfigured() {
        return StringUtils.hasText(googleClientId)
                && StringUtils.hasText(googleClientSecret)
                && !"not-configured".equalsIgnoreCase(googleClientId)
                && !"not-configured".equalsIgnoreCase(googleClientSecret);
    }

    private String normalizePortal(String portal) {
        String normalized = (portal == null ? "patient" : portal.trim().toLowerCase(Locale.ROOT));
        if (!"patient".equals(normalized) && !"doctor".equals(normalized)) {
            throw new IllegalArgumentException("Invalid portal. Allowed values are 'patient' or 'doctor'.");
        }
        return normalized;
    }
}
