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
    import org.springframework.beans.factory.annotation.Autowired;
    import org.springframework.context.annotation.Bean;
    import org.springframework.context.annotation.Configuration;
    import org.springframework.security.authentication.AuthenticationManager;
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
                    .csrf(csrf -> csrf.disable())
                    .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                    .authorizeHttpRequests(auth -> auth
                            .requestMatchers("/api/auth/**").permitAll() // Allow all auth requests
                            .requestMatchers("/api/doctor/**").hasRole("DOCTOR")
                            .requestMatchers("/api/patient/**").hasRole("PATIENT")
                            .anyRequest().authenticated() // All other requests must be authenticated
                    );

            // Add our custom JWT filter before the standard authentication filter
            http.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

            // Add a logging filter to trace requests
            http.addFilterBefore(new Filter() {
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
    }