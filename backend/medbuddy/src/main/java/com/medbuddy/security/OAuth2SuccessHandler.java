package com.medbuddy.security;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medbuddy.dto.UserDto;
import com.medbuddy.model.Doctor;
import com.medbuddy.model.Patient;
import com.medbuddy.model.Provider;
import com.medbuddy.model.Role;
import com.medbuddy.model.Specialization;
import com.medbuddy.model.User;
import com.medbuddy.repository.DoctorRepository;
import com.medbuddy.repository.PatientRepository;
import com.medbuddy.repository.UserRepository;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Handles the successful completion of the Google OAuth2 flow.
 *
 * <p>After Spring Security validates the Google token and our
 * {@code OAuth2UserService} has find-or-created the local user, control reaches
 * this handler.  We:
 * <ol>
 *   <li>Look up the local {@link User} by the email returned by Google.</li>
 *   <li>Issue our own JWT using {@link JwtUtil} (same token used by the rest of the API).</li>
 *   <li>Serialize the {@link UserDto} to JSON.</li>
 *   <li>Redirect the browser to the React frontend's OAuth callback page with
 *       {@code token} and {@code user} as query parameters.</li>
 * </ol>
 *
 * <p>The frontend ({@code OAuthCallback.jsx}) reads these params, stores them in
 * {@code AuthContext} / {@code localStorage}, and navigates to the appropriate
 * role dashboard — exactly the same flow as a normal email/password login.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OAuth2SuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

        private static final String OAUTH2_REDIRECT_URI = "OAUTH2_REDIRECT_URI";
        private static final String OAUTH2_PORTAL = "OAUTH2_PORTAL";

    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;
    private final PatientRepository patientRepository;
    private final DoctorRepository doctorRepository;
    private final ObjectMapper objectMapper;

    @Value("${app.frontend-url:http://localhost:5173}")
    private String frontendUrl;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException {

        OAuth2AuthenticationToken oauthToken = (OAuth2AuthenticationToken) authentication;
        String email = oauthToken.getPrincipal().getAttribute("email");
        if (email == null || email.isBlank()) {
            throw new IllegalStateException("Google account did not return an email address");
        }
                final String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);

        HttpSession session = request.getSession(false);
        String redirectBase = (session != null)
                ? (String) session.getAttribute(OAUTH2_REDIRECT_URI)
                : null;
        String portal = resolvePortal(session, redirectBase);

        User user = userRepository.findByEmail(normalizedEmail)
                .orElseGet(() -> createOauthUser(oauthToken, normalizedEmail, portal));

        // Generate our own JWT — same mechanism used by the password login path
        String jwt = jwtUtil.generateToken(user.getEmail());

        // Build the UserDto the same way the REST login endpoint does
        UserDto userDto = buildUserDto(user);

        // Encode the JSON payload so it is safe as a URL query parameter
        String userJson = URLEncoder.encode(
                objectMapper.writeValueAsString(userDto),
                StandardCharsets.UTF_8);

                // Use the redirect URI stored by /api/auth/oauth2/init, falling back to config
        if (redirectBase == null || redirectBase.isBlank()) {
            redirectBase = frontendUrl + "/oauth-callback";
        }

                if (session != null) {
                        session.removeAttribute(OAUTH2_REDIRECT_URI);
                        session.removeAttribute(OAUTH2_PORTAL);
                }

        String redirectUrl = redirectBase
                + "?token=" + jwt
                + "&user=" + userJson;

                log.info("OAuth2 login success for {} – redirecting to {}", normalizedEmail, redirectBase);
        getRedirectStrategy().sendRedirect(request, response, redirectUrl);
    }

        private User createOauthUser(OAuth2AuthenticationToken oauthToken, String email, String portal) {
                Role role = "doctor".equals(portal) ? Role.DOCTOR : Role.PATIENT;
                Map<String, Object> attributes = oauthToken.getPrincipal().getAttributes();

                String givenName = attr(attributes, "given_name", "Google");
                String familyName = attr(attributes, "family_name", "User");

                User created = userRepository.save(User.builder()
                                .email(email)
                                .passwordHash(null)
                                .role(role)
                                .provider(Provider.GOOGLE)
                                .build());

                if (role == Role.DOCTOR) {
                        doctorRepository.save(Doctor.builder()
                                        .firstName(givenName)
                                        .lastName(familyName)
                                        .phoneNumber(null)
                                        .user(created)
                                        .build());
                } else {
                        patientRepository.save(Patient.builder()
                                        .firstName(givenName)
                                        .lastName(familyName)
                                        .phoneNumber("")
                                        .user(created)
                                        .build());
                }

                return created;
        }

        private String resolvePortal(HttpSession session, String redirectBase) {
                if (session != null) {
                        Object portalObj = session.getAttribute(OAUTH2_PORTAL);
                        if (portalObj instanceof String portal) {
                                String normalized = portal.trim().toLowerCase(Locale.ROOT);
                                if ("doctor".equals(normalized) || "patient".equals(normalized)) {
                                        return normalized;
                                }
                        }
                }

                if (redirectBase != null && redirectBase.toLowerCase(Locale.ROOT).contains("doctor")) {
                        return "doctor";
                }
                return "patient";
        }

        private String attr(Map<String, Object> attributes, String key, String fallback) {
                Object value = attributes.get(key);
                if (value instanceof String s && !s.isBlank()) {
                        return s;
                }
                return fallback;
        }

    // ── Helper — mirrors UserService.buildUserDto() ───────────────────────

    private UserDto buildUserDto(User user) {
        UserDto.UserDtoBuilder builder = UserDto.builder()
                .id(user.getId())
                .email(user.getEmail())
                .role(user.getRole());

        if (user.getRole() == Role.PATIENT) {
            patientRepository.findByUser_Id(user.getId()).ifPresent(p ->
                    builder.profileId(p.getId())
                           .firstName(p.getFirstName())
                           .lastName(p.getLastName())
                           .phoneNumber(p.getPhoneNumber()));
        } else if (user.getRole() == Role.DOCTOR) {
                        doctorRepository.findByUser_IdWithSpecializations(user.getId()).ifPresent(d ->
                    builder.profileId(d.getId())
                           .firstName(d.getFirstName())
                           .lastName(d.getLastName())
                           .phoneNumber(d.getPhoneNumber())
                           .specializations(d.getSpecializations().stream()
                                   .map(Specialization::getName)
                                   .collect(Collectors.toList())));
        }

        return builder.build();
    }
}
