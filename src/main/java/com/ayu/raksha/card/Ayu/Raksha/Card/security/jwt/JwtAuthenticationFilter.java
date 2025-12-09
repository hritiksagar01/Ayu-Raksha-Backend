package com.ayu.raksha.card.Ayu.Raksha.Card.security.jwt;

import com.ayu.raksha.card.Ayu.Raksha.Card.service.SupabaseTokenValidator;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    @Autowired
    private SupabaseTokenValidator supabaseTokenValidator;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) throws ServletException {
        String path = request.getRequestURI();
        if (path == null) return false;
        return path.startsWith("/api/auth")
                || path.startsWith("/auth")
                || path.startsWith("/error")
                || path.startsWith("/actuator")
                || path.startsWith("/static")
                || path.equals("/")
                || path.equals("/favicon.ico");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            String jwt = getJwtFromRequest(request);

            if (StringUtils.hasText(jwt)) {
                // Ask Supabase if this token is valid and get user info
                Map<String, Object> userInfo = supabaseTokenValidator.validateAndGetUser(jwt);

                if (userInfo != null && userInfo.get("email") != null) {
                    String username = (String) userInfo.get("email");

                    // Derive roles from Supabase "role" or user_metadata/app_metadata if present
                    List<String> roles = new ArrayList<>();
                    Object role = userInfo.get("role");
                    if (role instanceof String && StringUtils.hasText((String) role)) {
                        roles.add(((String) role).toUpperCase());
                    }

                    // Default to AUTHENTICATED role if nothing else is present
                    if (roles.isEmpty()) {
                        roles.add("AUTHENTICATED");
                    }

                    List<GrantedAuthority> authorities = roles.stream()
                            .map(r -> new SimpleGrantedAuthority("ROLE_" + r.replace("ROLE_", "")))
                            .collect(Collectors.toList());

                    UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                            username, null, authorities);
                    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            }
        } catch (Exception ex) {
            logger.error("Could not set user authentication in security context", ex);
        }

        filterChain.doFilter(request, response);
    }

    private String getJwtFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            String token = bearerToken.substring(7).trim();
            if (isUsableToken(token)) {
                System.out.println("Auth token source: Authorization header (" + mask(token) + ")");
                return token;
            }
        }
        // Check query parameter fallback
        String qp = request.getParameter("access_token");
        if (isUsableToken(qp)) {
            System.out.println("Auth token source: query param access_token (" + mask(qp) + ")");
            return qp;
        }
        // Check common cookie names used for auth
        if (request.getCookies() != null) {
            for (Cookie c : request.getCookies()) {
                if (c == null) continue;
                String name = c.getName();
                if (name == null) continue;
                if (name.equals("auth_token") || name.equals("sb-access-token") || name.equals("sb:token") ||
                        name.equals("supabase_access_token") || name.equals("access_token")) {
                    String v = c.getValue();
                    if (isUsableToken(v)) {
                        System.out.println("Auth token source: cookie '" + name + "' (" + mask(v) + ")");
                        return v;
                    }
                }
            }
        }
        return null;
    }

    private boolean isUsableToken(String token) {
        if (!StringUtils.hasText(token)) return false;
        String t = token.trim();
        if (t.equalsIgnoreCase("undefined") || t.equalsIgnoreCase("null")) return false;
        return true;
    }

    private String mask(String token) {
        try {
            if (token == null) return "";
            int n = token.length();
            if (n <= 10) return "***";
            return token.substring(0, 5) + "..." + token.substring(n - 5);
        } catch (Exception e) {
            return "***";
        }
    }
}