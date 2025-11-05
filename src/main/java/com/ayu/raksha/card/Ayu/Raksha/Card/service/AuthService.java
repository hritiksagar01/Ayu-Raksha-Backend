package com.ayu.raksha.card.Ayu.Raksha.Card.service;

import com.ayu.raksha.card.Ayu.Raksha.Card.dto.AuthResponse;
import com.ayu.raksha.card.Ayu.Raksha.Card.dto.GoogleAuthRequest;
import com.ayu.raksha.card.Ayu.Raksha.Card.dto.LoginRequest;
import com.ayu.raksha.card.Ayu.Raksha.Card.dto.SignupRequest;
import com.ayu.raksha.card.Ayu.Raksha.Card.models.Role;
import com.ayu.raksha.card.Ayu.Raksha.Card.models.User;
import com.ayu.raksha.card.Ayu.Raksha.Card.repository.UserRepository;
import com.ayu.raksha.card.Ayu.Raksha.Card.security.jwt.JwtTokenProvider;
// --- START: ADD THESE IMPORTS ---
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import org.springframework.beans.factory.annotation.Value;
import java.time.Instant;
import java.util.Collections;
import java.util.concurrent.ThreadLocalRandom;
// --- END: ADD THESE IMPORTS ---
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {
    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtTokenProvider tokenProvider;

    @Value("${app.google.client-id}")
    private String googleClientId;

    // Generate a unique 12-digit numeric patient ID
    private String generateUniquePatientId() {
        for (int i = 0; i < 20; i++) { // try up to 20 times
            long min = 100_000_000_000L; // 12 digits minimum
            long max = 999_999_999_999L;
            long random = ThreadLocalRandom.current().nextLong(min, max + 1);
            String candidate = String.valueOf(random);
            if (!userRepository.findByPatientId(candidate).isPresent()) {
                return candidate;
            }
        }
        throw new RuntimeException("Unable to generate unique patient ID after multiple attempts");
    }

    public AuthResponse loginWithGoogle(String idTokenString, String userType) {
        try {
            // Step 1: Verify the Google ID token
            GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), new GsonFactory())
                    .setAudience(Collections.singletonList(googleClientId))
                    .build();

            GoogleIdToken idToken = verifier.verify(idTokenString);
            if (idToken == null) {
                throw new RuntimeException("Invalid Google ID token.");
            }

            // Step 2: Extract user information from the token
            GoogleIdToken.Payload payload = idToken.getPayload();
            String email = payload.getEmail();
            String name = (String) payload.get("name");

            // Step 3: Find or create the user in your database
            User user = userRepository.findByEmail(email)
                    .orElseGet(() -> {
                        // User doesn't exist, create a new one (sign them up)
                        User newUser = new User();
                        newUser.setEmail(email);
                        newUser.setName(name);
                        // For Google sign-ups, we can generate a secure random password
                        // as they won't use it to log in directly.
                        newUser.setPassword(passwordEncoder.encode("a-very-secure-random-password"));

                        if ("doctor".equalsIgnoreCase(userType)) {
                            newUser.setRole(Role.ROLE_DOCTOR);
                        } else {
                            newUser.setRole(Role.ROLE_PATIENT);
                            // assign patientId
                            newUser.setPatientId(generateUniquePatientId());
                        }
                        return userRepository.save(newUser);
                    });

            // Step 4: Generate your application's own JWT
            String jwt = tokenProvider.generateTokenForUser(user);

            return new AuthResponse(jwt, user);

        } catch (Exception e) {
            // You can add more specific exception handling here
            throw new RuntimeException("Google authentication failed: " + e.getMessage());
        }
    }

    public AuthResponse loginUser(LoginRequest loginRequest) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        loginRequest.getEmail(),
                        loginRequest.getPassword()
                )
        );

        SecurityContextHolder.getContext().setAuthentication(authentication);
        User user = (User) authentication.getPrincipal();
        String jwt = tokenProvider.generateToken(authentication);

        return new AuthResponse(jwt, user);
    }

    public AuthResponse registerUser(SignupRequest signUpRequest, String userType) {
        if (userRepository.existsByEmail(signUpRequest.getEmail())) {
            throw new RuntimeException("Error: Email is already taken!");
        }

        User user = new User();
        user.setName(signUpRequest.getName());
        user.setEmail(signUpRequest.getEmail());
        user.setPassword(passwordEncoder.encode(signUpRequest.getPassword()));
        user.setPhone(signUpRequest.getPhone());
        user.setDateOfBirth(signUpRequest.getDateOfBirth());
        user.setGender(signUpRequest.getGender());

        // Set role based on the path variable
        if ("doctor".equalsIgnoreCase(userType)) {
            user.setRole(Role.ROLE_DOCTOR);
        } else if ("admin".equalsIgnoreCase(userType) || "uploader".equalsIgnoreCase(userType)) {
            // allow registering admin/uploader via this endpoint if needed
            if ("admin".equalsIgnoreCase(userType)) {
                user.setRole(Role.ROLE_ADMIN);
            } else {
                user.setRole(Role.ROLE_UPLOADER);
            }
        } else {
            user.setRole(Role.ROLE_PATIENT);
            // generate and assign patientId
            user.setPatientId(generateUniquePatientId());
        }

        User savedUser = userRepository.save(user);

        // Automatically log the user in after successful registration
        String jwt = tokenProvider.generateTokenForUser(user);

        return new AuthResponse(jwt, savedUser);
    }
}