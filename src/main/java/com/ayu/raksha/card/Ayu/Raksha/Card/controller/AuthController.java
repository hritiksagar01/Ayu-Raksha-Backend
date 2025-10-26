package com.ayu.raksha.card.Ayu.Raksha.Card.controller;

import com.ayu.raksha.card.Ayu.Raksha.Card.dto.AuthResponse;
import com.ayu.raksha.card.Ayu.Raksha.Card.dto.GoogleAuthRequest;
import com.ayu.raksha.card.Ayu.Raksha.Card.dto.LoginRequest;
import com.ayu.raksha.card.Ayu.Raksha.Card.dto.SignupRequest;
import com.ayu.raksha.card.Ayu.Raksha.Card.service.AuthService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private AuthService authService;

    @PostMapping("/login/{userType}")
    public ResponseEntity<AuthResponse> login(@RequestBody LoginRequest loginRequest, @PathVariable String userType) {
        // TEMPORARY: See what the backend is actually receiving.
        System.out.println("--- LOGIN ATTEMPT ---");
        System.out.println("Email: " + loginRequest.getEmail());
        System.out.println("Password: '" + loginRequest.getPassword() + "'"); // The quotes help see if it's blank

        AuthResponse authResponse = authService.loginUser(loginRequest);
        return ResponseEntity.ok(authResponse);
    }

    @PostMapping("/signup/{userType}")
    public ResponseEntity<?> signup(@RequestBody SignupRequest signupRequest, @PathVariable String userType) {
        try {
            AuthResponse authResponse = authService.registerUser(signupRequest, userType);
            return ResponseEntity.ok(authResponse);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // Placeholder for Google Signup
    @PostMapping("/google/{userType}")
    public ResponseEntity<?> googleLogin(@RequestBody GoogleAuthRequest googleAuthRequest, @PathVariable String userType) {
        try {
            AuthResponse authResponse = authService.loginWithGoogle(googleAuthRequest.getIdToken(), userType);
            return ResponseEntity.ok(authResponse);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}