package com.ayu.raksha.card.Ayu.Raksha.Card.security.jwt;

import com.ayu.raksha.card.Ayu.Raksha.Card.models.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.security.core.Authentication;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Map;

@Component
public class JwtTokenProvider {

    // Prefer a Supabase-specific JWT secret if set; otherwise fall back to the app's own JWT secret
    @Value("${supabase.jwt.secret:${app.jwt.secret}}")
    private String jwtSecret;

    @Value("${app.jwt.expiration-ms}")
    private int jwtExpirationMs;

    private SecretKey getSigningKey() {
        byte[] keyBytes = null;
        if (jwtSecret == null) jwtSecret = "";

        // Debug logging to verify the secret being used
        System.out.println("=== JWT Secret Debug ===");
        System.out.println("Secret length: " + jwtSecret.length());
        System.out.println("Secret starts with: " + (jwtSecret.length() > 20 ? jwtSecret.substring(0, 20) + "..." : jwtSecret));

        try {
            // Try Base64 decode first (Supabase secret may be base64-encoded)
            byte[] decoded = Base64.getDecoder().decode(jwtSecret);
            if (decoded != null && decoded.length >= 16) {
                keyBytes = decoded;
                System.out.println("Using Base64 decoded secret, decoded length: " + decoded.length + " bytes");
            }
        } catch (IllegalArgumentException e) {
            System.out.println("Not a valid Base64 string, will use as plain text");
        }

        if (keyBytes == null) {
            keyBytes = jwtSecret.getBytes(StandardCharsets.UTF_8);
            System.out.println("Using plain text secret, length: " + keyBytes.length + " bytes");
        }

        System.out.println("Final key bytes length: " + keyBytes.length);
        System.out.println("======================");

        return Keys.hmacShaKeyFor(keyBytes);
    }

    public String generateToken(Authentication authentication) {
        User userPrincipal = (User) authentication.getPrincipal();
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + jwtExpirationMs);

        return Jwts.builder()
                .setSubject(userPrincipal.getEmail())
                .setIssuedAt(now)
                .setExpiration(expiryDate)
                .signWith(getSigningKey(), SignatureAlgorithm.HS512)
                .compact();
    }

    public String generateTokenForUser(User user) {
        if (user == null || !StringUtils.hasText(user.getEmail())) {
            throw new IllegalArgumentException("Cannot create JWT for user with null or empty email.");
        }

        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + jwtExpirationMs);

        return Jwts.builder()
                .setSubject(user.getEmail())
                .setIssuedAt(now)
                .setExpiration(expiryDate)
                .signWith(getSigningKey(), SignatureAlgorithm.HS512)
                .compact();
    }

    public String generateBackendToken(User user) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + jwtExpirationMs);
        String type;
        try {
            String roleName = user.getRole() != null ? user.getRole().name() : "ROLE_PATIENT";
            if (roleName.startsWith("ROLE_")) roleName = roleName.substring(5);
            type = roleName.toLowerCase();
        } catch (Exception e) {
            type = "patient";
        }
        return Jwts.builder()
                .setSubject(user.getEmail())
                .claim("id", user.getId() != null ? user.getId().toString() : null)
                .claim("type", type)
                .claim("email", user.getEmail())
                .setIssuedAt(now)
                .setExpiration(expiryDate)
                .signWith(getSigningKey(), SignatureAlgorithm.HS512)
                .compact();
    }


    public String getUsernameFromJWT(String token) {
        Claims claims = getClaimsFromJWT(token);
        // Prefer subject, otherwise try common email claim
        String sub = claims.getSubject();
        if (StringUtils.hasText(sub)) return sub;
        Object email = claims.get("email");
        return email != null ? email.toString() : null;
    }

    public Claims getClaimsFromJWT(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    public List<String> getRolesFromJWT(String token) {
        List<String> roles = new ArrayList<>();
        try {
            Claims claims = getClaimsFromJWT(token);
            // Common places where roles might be stored in Supabase tokens
            Object roleClaim = claims.get("role");
            if (roleClaim instanceof String) {
                roles.add(((String) roleClaim).toUpperCase());
            }

            Object rolesClaim = claims.get("roles");
            if (rolesClaim instanceof List) {
                for (Object r : (List<?>) rolesClaim) {
                    if (r != null) roles.add(r.toString().toUpperCase());
                }
            }

            // user_metadata or app_metadata might contain role info as a map
            Object userMeta = claims.get("user_metadata");
            if (userMeta instanceof Map) {
                Object rm = ((Map<?, ?>) userMeta).get("role");
                if (rm != null) roles.add(rm.toString().toUpperCase());
            }

            Object appMeta = claims.get("app_metadata");
            if (appMeta instanceof Map) {
                Object rm = ((Map<?, ?>) appMeta).get("role");
                if (rm != null) roles.add(rm.toString().toUpperCase());
            }
        } catch (Exception ex) {
            // ignore and return what we've collected (possibly empty)
        }
        return roles;
    }

    public String getUserIdFromJWT(String token) {
        try {
            Claims claims = getClaimsFromJWT(token);
            return claims.getSubject();
        } catch (Exception e) {
            return null;
        }
    }

    public boolean validateToken(String authToken) {
        try {
            Jwts.parserBuilder().setSigningKey(getSigningKey()).build().parseClaimsJws(authToken);
            return true;
        } catch (io.jsonwebtoken.security.SignatureException ex) {
            System.err.println("Invalid JWT signature: " + ex.getMessage());
        } catch (io.jsonwebtoken.MalformedJwtException ex) {
            System.err.println("Invalid JWT token: " + ex.getMessage());
        } catch (io.jsonwebtoken.ExpiredJwtException ex) {
            System.err.println("Expired JWT token: " + ex.getMessage());
        } catch (io.jsonwebtoken.UnsupportedJwtException ex) {
            System.err.println("Unsupported JWT token: " + ex.getMessage());
        } catch (IllegalArgumentException ex) {
            System.err.println("JWT claims string is empty: " + ex.getMessage());
        } catch (Exception ex) {
            System.err.println("JWT validation error: " + ex.getMessage());
        }
        return false;
    }
}