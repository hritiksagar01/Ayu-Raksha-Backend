// src/main/java/com/ayu/raksha/card/Ayu/Raksha/Card/config/SecurityConfig.java
    package com.ayu.raksha.card.Ayu.Raksha.Card.config;

    import com.ayu.raksha.card.Ayu.Raksha.Card.security.jwt.JwtAuthenticationFilter;
    import com.ayu.raksha.card.Ayu.Raksha.Card.security.service.CustomUserDetailsService;
    import jakarta.servlet.Filter;
    import jakarta.servlet.FilterChain;
    import jakarta.servlet.ServletException;
    import jakarta.servlet.ServletRequest;
    import jakarta.servlet.ServletResponse;
    import jakarta.servlet.http.HttpServletRequest;
    import jakarta.servlet.http.HttpServletResponse;
    import org.springframework.beans.factory.annotation.Autowired;
    import org.springframework.context.annotation.Bean;
    import org.springframework.context.annotation.Configuration;
    import org.springframework.http.HttpMethod;
    import org.springframework.security.authentication.AuthenticationManager;
    import org.springframework.security.config.Customizer;
    import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
    import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
    import org.springframework.security.config.annotation.web.builders.HttpSecurity;
    import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
    import org.springframework.security.config.http.SessionCreationPolicy;
    import org.springframework.security.core.Authentication;
    import org.springframework.security.core.context.SecurityContextHolder;
    import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
    import org.springframework.security.crypto.password.PasswordEncoder;
    import org.springframework.security.web.SecurityFilterChain;
    import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
    import org.springframework.security.web.AuthenticationEntryPoint;

    import java.io.IOException;

    @Configuration
    @EnableWebSecurity
    @EnableMethodSecurity
    public class SecurityConfig {

        @Autowired
        private JwtAuthenticationFilter jwtAuthenticationFilter;

        @Autowired
        private CustomUserDetailsService customUserDetailsService;

        @Bean
        public PasswordEncoder passwordEncoder() {
            return new BCryptPasswordEncoder();
        }

        @Bean
        public AuthenticationManager authenticationManager(AuthenticationConfiguration authenticationConfiguration) throws Exception {
            return authenticationConfiguration.getAuthenticationManager();
        }

        @Bean
        public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
            http
                    .cors(Customizer.withDefaults())
                    .csrf(csrf -> csrf.disable())
                    .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                    .exceptionHandling(eh -> eh.authenticationEntryPoint(unauthorizedEntryPoint()))
                    .authorizeHttpRequests(auth -> auth
                            // Allow CORS preflight requests through without authentication
                            .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                            .requestMatchers("/api/auth/**").permitAll() // Allow all auth requests
                            .requestMatchers("/api/doctor/**").hasRole("DOCTOR")
                            // Patient-related endpoints for authenticated roles
                            .requestMatchers("/api/patients/**").hasAnyRole("PATIENT", "DOCTOR", "UPLOADER", "ADMIN", "AUTHENTICATED")
                            // Upload API paths
                            .requestMatchers(HttpMethod.POST, "/api/upload/presign", "/api/upload/metadata").hasAnyRole("PATIENT", "DOCTOR", "UPLOADER", "ADMIN")
                            .requestMatchers(HttpMethod.GET, "/api/upload/**").hasAnyRole("PATIENT", "DOCTOR", "UPLOADER", "ADMIN")
                            .requestMatchers(HttpMethod.POST, "/api/upload/**").hasAnyRole("ADMIN", "UPLOADER")
                            // Also support non-API upload route observed in logs
                            .requestMatchers(HttpMethod.GET, "/upload/**").hasAnyRole("PATIENT", "DOCTOR", "UPLOADER", "ADMIN")
                            .requestMatchers(HttpMethod.POST, "/upload/**").hasAnyRole("ADMIN", "UPLOADER")
                            .anyRequest().authenticated() // All other requests must be authenticated
                    );

            // Add our custom JWT filter before the standard authentication filter
            http.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

            // Add a logging filter to trace requests (after JWT so we can see authorities)
            http.addFilterAfter(new Filter() {
                @Override
                public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
                    HttpServletRequest httpRequest = (HttpServletRequest) request;
                    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

                    System.out.println("Request URI: " + httpRequest.getRequestURI());
                    if (authentication != null) {
                        System.out.println("Authentication: " + authentication.getName() + ", Authorities: " + authentication.getAuthorities());
                    } else {
                        System.out.println("No authentication found.");
                    }

                    chain.doFilter(request, response);
                }
            }, JwtAuthenticationFilter.class);

            return http.build();
        }

        @Bean
        public AuthenticationEntryPoint unauthorizedEntryPoint() {
            return (request, response, authException) -> {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json");
                String msg = authException != null && authException.getMessage() != null ? authException.getMessage() : "Unauthorized";
                response.getWriter().write("{\"success\":false,\"error\":\"" + msg.replace("\"", "'") + "\"}");
            };
        }
    }