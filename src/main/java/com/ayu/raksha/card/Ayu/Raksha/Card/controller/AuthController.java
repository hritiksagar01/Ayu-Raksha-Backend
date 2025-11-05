package com.ayu.raksha.card.Ayu.Raksha.Card.controller;

import com.ayu.raksha.card.Ayu.Raksha.Card.dto.AuthResponse;
import com.ayu.raksha.card.Ayu.Raksha.Card.dto.GoogleAuthRequest;
import com.ayu.raksha.card.Ayu.Raksha.Card.dto.LoginRequest;
import com.ayu.raksha.card.Ayu.Raksha.Card.dto.SignupRequest;
import com.ayu.raksha.card.Ayu.Raksha.Card.dto.SyncRequest;
import com.ayu.raksha.card.Ayu.Raksha.Card.models.Role;
import com.ayu.raksha.card.Ayu.Raksha.Card.models.User;
import com.ayu.raksha.card.Ayu.Raksha.Card.repository.UserRepository;
import com.ayu.raksha.card.Ayu.Raksha.Card.security.jwt.JwtTokenProvider;
import com.ayu.raksha.card.Ayu.Raksha.Card.service.AuthService;
import com.ayu.raksha.card.Ayu.Raksha.Card.service.SupabaseAdminService;
import com.ayu.raksha.card.Ayu.Raksha.Card.service.SupabaseTokenValidator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private AuthService authService;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SupabaseAdminService supabaseAdminService;

    @Autowired
    private SupabaseTokenValidator supabaseTokenValidator;

    @PostMapping("/login/{userType}")
    public ResponseEntity<?> login(@RequestBody LoginRequest loginRequest, @PathVariable String userType) {
        // Deprecated: use Supabase auth on the client. Provide instructions to callers.
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED)
                .body(Map.of("message", "Please use Supabase for authentication. Sign in on the client using Supabase and send the access_token to /api/auth/sync to create or update a local user."));
    }

    @PostMapping("/signup/{userType}")
    public ResponseEntity<?> signup(@RequestBody SignupRequest signupRequest, @PathVariable String userType) {
        // Deprecated: use Supabase auth on the client. Provide instructions to callers.
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED)
                .body(Map.of("message", "Please sign up using Supabase on the client. After signing up, call /api/auth/sync with Authorization header to create a local user record."));
    }

    // New: sync endpoint — accepts Authorization: Bearer <supabase_token> OR body.accessToken
    @PostMapping("/sync")
    public ResponseEntity<?> syncUser(@RequestHeader HttpHeaders headers, @RequestBody(required = false) SyncRequest req) {
        try {
            System.out.println("=== /api/auth/sync called ===");

            String token = null;
            if (req != null && req.getAccessToken() != null && !req.getAccessToken().isBlank()) {
                token = req.getAccessToken();
                System.out.println("Token from request body");
            }
            if (token == null) {
                List<String> auth = headers.get("Authorization");
                if (auth != null && !auth.isEmpty() && auth.get(0).startsWith("Bearer ")) {
                    token = auth.get(0).substring(7);
                    System.out.println("Token from Authorization header");
                }
            }
            if (token == null) {
                System.err.println("ERROR: Missing access token");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("success", false, "error", "Missing access token"));
            }

            System.out.println("Token received (first 20 chars): " + token.substring(0, Math.min(20, token.length())) + "...");
            System.out.println("Validating token with Supabase Auth server...");

            // Use Supabase Auth server validation (recommended approach)
            Map<String, Object> supabaseUser = supabaseTokenValidator.validateAndGetUser(token);

            if (supabaseUser == null || !Boolean.TRUE.equals(supabaseUser.get("valid"))) {
                System.err.println("ERROR: Token validation failed");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("success", false, "error", "Invalid or expired token"));
            }

            System.out.println("✓ Token validated successfully!");
            System.out.println("User email: " + supabaseUser.get("email"));

            // Extract user info from Supabase validation response
            String emailFromToken = (String) supabaseUser.get("email");
            if (emailFromToken == null || emailFromToken.isBlank()) {
                // Fall back to request email if provided
                String fallback = req != null ? req.getEmail() : null;
                if (fallback == null || fallback.isBlank()) {
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("success", false, "error", "Token does not contain an email"));
                }
                emailFromToken = fallback;
            }

            String name = (String) supabaseUser.get("name");
            if (name == null || name.isBlank()) {
                name = req != null && req.getName() != null && !req.getName().isBlank() ? req.getName() : null;
            }

            String phone = (String) supabaseUser.get("phone");
            if (phone == null || phone.isBlank()) {
                phone = req != null ? req.getPhone() : null;
            }

            // Determine role/type from token or request
            String roleFromToken = (String) supabaseUser.get("role");
            String type = req != null && req.getUserType() != null ? req.getUserType().toLowerCase() : null;
            if ((type == null || type.isBlank()) && roleFromToken != null) {
                type = roleFromToken.toLowerCase();
            }
            if (type == null || type.isBlank()) type = "patient"; // default

            Role roleEnum = switch (type) {
                case "doctor" -> Role.ROLE_DOCTOR;
                case "uploader" -> Role.ROLE_UPLOADER;
                case "admin" -> Role.ROLE_ADMIN;
                default -> Role.ROLE_PATIENT;
            };

            // Upsert user
            Optional<User> existing = userRepository.findByEmail(emailFromToken);
            User user;
            if (existing.isPresent()) {
                System.out.println("User exists, updating...");
                user = existing.get();
                user.setRole(roleEnum); // keep in sync with Supabase/userType
                if (name != null) user.setName(name);
                if (phone != null) user.setPhone(phone);
                if (roleEnum == Role.ROLE_PATIENT && (user.getPatientId() == null || user.getPatientId().isBlank())) {
                    user.setPatientId(generatePatientCode());
                }
                userRepository.save(user);
                System.out.println("User updated successfully. ID: " + user.getId());
            } else {
                System.out.println("Creating new user...");
                user = new User();
                user.setEmail(emailFromToken);
                user.setName(name != null ? name : emailFromToken);
                user.setPhone(phone);
                user.setRole(roleEnum);
                // Password is irrelevant under Supabase-auth; store a random placeholder
                user.setPassword(UUID.randomUUID().toString());
                if (roleEnum == Role.ROLE_PATIENT) {
                    user.setPatientId(generatePatientCode());
                }
                userRepository.save(user);
                System.out.println("User created successfully. ID: " + user.getId());
            }

            System.out.println("Generating backend JWT token...");
            // Mint a backend JWT that your frontend can use thereafter
            String backendToken = jwtTokenProvider.generateBackendToken(user);
            System.out.println("Backend token generated successfully");

            // Mirror patientCode to Supabase user metadata if possible
            try {
                if (roleEnum == Role.ROLE_PATIENT && user.getPatientId() != null) {
                    System.out.println("Mirroring patientCode to Supabase...");
                    String supabaseUserId = (String) supabaseUser.get("userId");
                    if (supabaseUserId != null && !supabaseUserId.isBlank()) {
                        java.util.Map<String, Object> meta = new java.util.HashMap<>();
                        meta.put("patientCode", user.getPatientId());
                        supabaseAdminService.updateUserMetadata(supabaseUserId, meta);
                        System.out.println("PatientCode mirrored to Supabase");
                    }
                }
            } catch (Exception e) {
                System.err.println("Failed to mirror patientCode: " + e.getMessage());
            }

            System.out.println("Building response...");
            // Build response shape using HashMap to allow potential nulls
            Map<String, Object> userMap = new HashMap<>();
            userMap.put("id", String.valueOf(user.getId()));
            userMap.put("name", user.getName());
            userMap.put("email", user.getEmail());
            userMap.put("type", type);
            userMap.put("phone", user.getPhone());
            userMap.put("patientCode", user.getPatientId());

            Map<String, Object> data = new HashMap<>();
            data.put("token", backendToken);
            data.put("user", userMap);

            Map<String, Object> resp = new HashMap<>();
            resp.put("success", true);
            resp.put("data", data);
            resp.put("message", "Login successful");

            System.out.println("Sending 200 OK response");
            return ResponseEntity.ok(resp);

        } catch (Exception e) {
            System.err.println("ERROR in /api/auth/sync: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "success", false,
                    "error", "Server error during sync",
                    "details", e.getMessage()
            ));
        }
    }

    private String generatePatientCode() {
        // 12-digit: generate random digits and ensure not taken
        for (int attempt = 0; attempt < 10; attempt++) {
            String code = String.format("%012d", (long) (Math.random() * 1_000_000_000_000L));
            if (userRepository.findByPatientId(code).isEmpty()) return code;
        }
        // Fallback: timestamp-based
        String fallback = String.format("%012d", System.currentTimeMillis() % 1_000_000_000_000L);
        return fallback;
    }

    // Placeholder for Google Signup — deprecated
    @PostMapping("/google/{userType}")
    public ResponseEntity<?> googleLogin(@RequestBody GoogleAuthRequest googleAuthRequest, @PathVariable String userType) {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED)
                .body(Map.of("message", "Use Supabase social login on the client. After login, call /api/auth/sync with the access token."));
    }
}