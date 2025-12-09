package com.ayu.raksha.card.Ayu.Raksha.Card.service;

import com.ayu.raksha.card.Ayu.Raksha.Card.dto.AuthResponse;
import com.ayu.raksha.card.Ayu.Raksha.Card.dto.GoogleAuthRequest;
import com.ayu.raksha.card.Ayu.Raksha.Card.dto.LoginRequest;
import com.ayu.raksha.card.Ayu.Raksha.Card.dto.SignupRequest;
import com.ayu.raksha.card.Ayu.Raksha.Card.models.Role;
import com.ayu.raksha.card.Ayu.Raksha.Card.models.User;
import com.ayu.raksha.card.Ayu.Raksha.Card.repository.UserRepository;
// Removed JwtTokenProvider import
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

    // Removed JwtTokenProvider; tokens are managed exclusively by Supabase

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

    // Deprecated: Google login handled on client via Supabase
    public AuthResponse loginWithGoogle(String idTokenString, String userType) {
        throw new UnsupportedOperationException("Use Supabase client-side authentication and call /api/auth/sync.");
    }

    // Deprecated: local login disabled; use Supabase
    public AuthResponse loginUser(LoginRequest loginRequest) {
        throw new UnsupportedOperationException("Use Supabase client-side authentication and call /api/auth/sync.");
    }

    // Deprecated: local signup disabled; use Supabase
    public AuthResponse registerUser(SignupRequest signUpRequest, String userType) {
        throw new UnsupportedOperationException("Use Supabase client-side signup and call /api/auth/sync.");
    }
}