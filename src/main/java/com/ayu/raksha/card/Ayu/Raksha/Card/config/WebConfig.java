package com.ayu.raksha.card.Ayu.Raksha.Card.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                // Add the IP address of your frontend to the list
                .allowedOrigins("http://localhost:3000", "http://192.168.1.243:3000", "http://3.111.47.30:3000","http://192.168.29.203:3000","http://172.19.128.1:3000/", "http://13.233.98.216")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true);
    }
}