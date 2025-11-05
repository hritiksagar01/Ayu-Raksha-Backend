import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import javax.crypto.SecretKey;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Quick test to verify the Supabase JWT secret works with actual tokens
 */
public class QuickJwtTest {

    public static void main(String[] args) {
        // Your actual Supabase JWT secret from the dashboard
        String secret = "fGGMrHRJgYlq2of0zOBoOGud7s8j3eozoptUYvpsAVfmI7nB3t48wYls3SzDnPk5LaYwSY1gBxdc8hYVnRnauA==";

        // The actual token from your latest request
        String token = "eyJhbGciOiJIUzI1NiIsImtpZCI6Ii9yU0JRME9QNm94TlJ1UHIiLCJ0eXAiOiJKV1QifQ.eyJpc3MiOiJodHRwczovL2R0a2txb2x6b29zdmRweXV4bnFpLnN1cGFiYXNlLmNvL2F1dGgvdjEiLCJzdWIiOiI3NTA5NWIxOS04OTdiLTRkNGQtOTA0Yi1jMDA2OGU3YzNmYzYiLCJhdWQiOiJhdXRoZW50aWNhdGVkIiwiZXhwIjoxNzYyMjk5MzA5LCJpYXQiOjE3NjIyOTU3MDksImVtYWlsIjoiaHJpdGlrLnNyaXZhc3RhdmExNUBnbWFpbC5jb20iLCJwaG9uZSI6IiIsImFwcF9tZXRhZGF0YSI6eyJwcm92aWRlciI6ImVtYWlsIiwicHJvdmlkZXJzIjpbImVtYWlsIl19LCJ1c2VyX21ldGFkYXRhIjp7ImVtYWlsIjoiaHJpdGlrLnNyaXZhc3RhdmExNUBnbWFpbC5jb20iLCJlbWFpbF92ZXJpZmllZCI6dHJ1ZSwibmFtZSI6IlNhZ2FyIiwicGhvbmUiOiI5NDUzOTMwMTIxIiwicGhvbmVfdmVyaWZpZWQiOmZhbHNlLCJzdWIiOiI3NTA5NWIxOS04OTdiLTRkNGQtOTA0Yi1jMDA2OGU3YzNmYzYifSwicm9sZSI6ImF1dGhlbnRpY2F0ZWQiLCJhYWwiOiJhYWwxIiwiYW1yIjpbeyJtZXRob2QiOiJvdHAiLCJ0aW1lc3RhbXAiOjE3NjIyOTU3MDl9XSwic2Vzc2lvbl9pZCI6IjNiZDhkNmMxLTdmZjEtNDNiZi04YTQwLTA5Njg0MTY5ODAwZiIsImlzX2Fub255bW91cyI6ZmFsc2V9.qVJ47WK1xXzbxmV93TDrZMFNZfvNgvYQoN8GdXlQ0P4";

        System.out.println("Testing JWT validation with your Supabase secret...\n");

        // Test with Base64 decoded secret (standard for Supabase)
        try {
            byte[] decoded = Base64.getDecoder().decode(secret);
            SecretKey key = Keys.hmacShaKeyFor(decoded);

            Claims claims = Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody();

            System.out.println("✅ SUCCESS! Token validated successfully!");
            System.out.println("\nToken Details:");
            System.out.println("  Email: " + claims.get("email"));
            System.out.println("  Sub (User ID): " + claims.getSubject());
            System.out.println("  Name: " + ((java.util.Map)claims.get("user_metadata")).get("name"));
            System.out.println("  Phone: " + ((java.util.Map)claims.get("user_metadata")).get("phone"));
            System.out.println("  Role: " + claims.get("role"));
            System.out.println("\nThe JWT secret is CORRECT! ✅");
            System.out.println("Make sure to RESTART your Spring Boot application.");

        } catch (Exception e) {
            System.err.println("❌ FAILED: " + e.getClass().getSimpleName());
            System.err.println("Message: " + e.getMessage());
            System.err.println("\nThis means the secret is still incorrect.");
        }
    }
}

