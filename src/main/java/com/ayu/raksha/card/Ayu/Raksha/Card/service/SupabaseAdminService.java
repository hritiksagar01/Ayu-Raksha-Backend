package com.ayu.raksha.card.Ayu.Raksha.Card.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Service
public class SupabaseAdminService {

    @Value("${supabase.url:}")
    private String supabaseUrl;

    @Value("${supabase.service-role-key:}")
    private String serviceRoleKey;

    private final RestTemplate restTemplate = new RestTemplate();

    public void updateUserMetadata(String supabaseUserId, Map<String, Object> userMetadata) {
        try {
            if (supabaseUrl == null || supabaseUrl.isBlank() || serviceRoleKey == null || serviceRoleKey.isBlank()) {
                return; // not configured; skip silently
            }
            String url = supabaseUrl.replaceAll("/+$", "") + "/auth/v1/admin/users/" + supabaseUserId;

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("apikey", serviceRoleKey);
            headers.set("Authorization", "Bearer " + serviceRoleKey);

            Map<String, Object> body = new HashMap<>();
            body.put("user_metadata", userMetadata);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
            restTemplate.put(url, entity);
        } catch (Exception ignored) {
            // don't break auth flow if admin update fails
        }
    }
}

