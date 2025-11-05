package com.ayu.raksha.card.Ayu.Raksha.Card.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.Map;

/**
 * Service to validate Supabase JWT tokens using the recommended approach:
 * calling the Supabase Auth server directly instead of local signature verification.
 *
 * This is the recommended approach per Supabase docs when using legacy JWT secrets
 * or shared secret (HS256) signing keys.
 */
@Service
public class SupabaseTokenValidator {

    @Value("${supabase.project.url:https://dtkkqolzoosvdpyuxnqi.supabase.co}")
    private String supabaseUrl;

    @Value("${supabase.anon.key:}")
    private String anonKey;

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Validates a Supabase access token by calling the Auth server.
     * Returns user data if valid, null if invalid.
     */
    public Map<String, Object> validateAndGetUser(String accessToken) {
        try {
            String url = supabaseUrl + "/auth/v1/user";

            System.out.println("=== Supabase Token Validation ===");
            System.out.println("URL: " + url);
            System.out.println("Anon key configured: " + (anonKey != null && !anonKey.isEmpty()));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Bearer " + accessToken)
                    .header("apikey", anonKey)
                    .GET()
                    .build();

            System.out.println("Sending request to Supabase Auth server...");
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            System.out.println("Response status: " + response.statusCode());
            System.out.println("Response body: " + response.body().substring(0, Math.min(200, response.body().length())));

            if (response.statusCode() == 200) {
                // Token is valid, parse user data
                JsonNode userData = objectMapper.readTree(response.body());

                Map<String, Object> result = new HashMap<>();
                result.put("valid", true);
                result.put("userId", userData.has("id") ? userData.get("id").asText() : null);
                result.put("email", userData.has("email") ? userData.get("email").asText() : null);
                result.put("phone", userData.has("phone") ? userData.get("phone").asText() : null);
                result.put("role", userData.has("role") ? userData.get("role").asText() : null);

                // Extract user_metadata if present
                if (userData.has("user_metadata")) {
                    JsonNode userMeta = userData.get("user_metadata");
                    result.put("name", userMeta.has("name") ? userMeta.get("name").asText() : null);
                    result.put("phone", userMeta.has("phone") ? userMeta.get("phone").asText() : result.get("phone"));
                }

                System.out.println("✓ Token is VALID");
                System.out.println("User: " + result.get("email"));
                return result;
            } else {
                System.err.println("Supabase token validation failed with status: " + response.statusCode());
                System.err.println("Response: " + response.body());
                return null;
            }

        } catch (Exception e) {
            System.err.println("Error validating Supabase token: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Simple boolean check if token is valid
     */
    public boolean isTokenValid(String accessToken) {
        return validateAndGetUser(accessToken) != null;
    }
}
