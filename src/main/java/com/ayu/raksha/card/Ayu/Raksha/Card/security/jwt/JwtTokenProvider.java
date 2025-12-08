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
import jakarta.annotation.PostConstruct;

@Component
public class JwtTokenProvider {

    // Prefer a Supabase-specific JWT secret if set; otherwise fall back to the app's own JWT secret.
    // Make the property resolution tolerant by providing an empty default so missing env vars won't fail startup.
    @Value("${supabase.jwt.secret:${app.jwt.secret:}}")
    private String jwtSecret;

    // Provide a default expiration so missing config doesn't break injection
    @Value("${app.jwt.expiration-ms:86400000}")
    private int jwtExpirationMs;

    // Flag to indicate whether JWT operations are enabled (i.e., a usable secret is configured)
    private boolean enabled = false;

    // Actual key used for signing/verification. If configured secret is weak or missing,
    // we generate a secure random key in-memory so that token creation still works and
    // does not crash endpoints like /api/auth/sync.
    private SecretKey signingKey;

    // We use HS256 which only requires a 256-bit key and is sufficient for backend-issued tokens.
    private static final SignatureAlgorithm SIGNATURE_ALG = SignatureAlgorithm.HS256;

    @PostConstruct
    private void init() {
        if (jwtSecret == null) jwtSecret = "";

        // Try to build a key from the configured secret
        try {
            byte[] keyBytes = tryBuildKeyBytes(jwtSecret);
            if (keyBytes != null && keyBytes.length * 8 >= SIGNATURE_ALG.getMinKeyLength()) {
                signingKey = Keys.hmacShaKeyFor(keyBytes);
                enabled = true;
                return;
            }
        } catch (Exception ignored) {
            // fall through to random key
        }

        // Fallback: generate a secure random key in-memory so signing never fails
        signingKey = Keys.secretKeyFor(SIGNATURE_ALG);
        enabled = true;
        System.out.println("JwtTokenProvider: configured secret is missing or too weak; using generated in-memory key for backend tokens.");
    }

    private byte[] tryBuildKeyBytes(String secret) {
        if (!StringUtils.hasText(secret)) return null;
        try {
            byte[] decoded = Base64.getDecoder().decode(secret);
            if (decoded != null && decoded.length > 0) {
                return decoded;
            }
        } catch (IllegalArgumentException ignored) {
            // not base64, treat as plain text
        }
        return secret.getBytes(StandardCharsets.UTF_8);
    }

    private SecretKey getSigningKey() {
        if (!enabled || signingKey == null) {
            throw new IllegalStateException("JWT secret not configured; JwtTokenProvider is disabled.");
        }
        return signingKey;
    }

    public String generateToken(Authentication authentication) {
        if (!enabled) throw new IllegalStateException("Cannot generate JWT: no secret configured.");
        User userPrincipal = (User) authentication.getPrincipal();
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + jwtExpirationMs);

        return Jwts.builder()
                .setSubject(userPrincipal.getEmail())
                .setIssuedAt(now)
                .setExpiration(expiryDate)
                .signWith(getSigningKey(), SIGNATURE_ALG)
                .compact();
    }

    public String generateTokenForUser(User user) {
        if (!enabled) throw new IllegalStateException("Cannot generate JWT: no secret configured.");
        if (user == null || !StringUtils.hasText(user.getEmail())) {
            throw new IllegalArgumentException("Cannot create JWT for user with null or empty email.");
        }

        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + jwtExpirationMs);

        return Jwts.builder()
                .setSubject(user.getEmail())
                .setIssuedAt(now)
                .setExpiration(expiryDate)
                .signWith(getSigningKey(), SIGNATURE_ALG)
                .compact();
    }

    public String generateBackendToken(User user) {
        if (!enabled) throw new IllegalStateException("Cannot generate JWT: no secret configured.");
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
                .signWith(getSigningKey(), SIGNATURE_ALG)
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
        if (!enabled) return false;
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