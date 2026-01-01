package com.centralizesys.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /**
     * Configures the security filter chain.
     * We DISABLE standard protections (CSRF, default login form) because
     * this is a local desktop-like app, and we want to handle Login manually via our Controller.
     */
    // TODO: Consider for the future implementing proper CRSF protection and enable it
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable) // Disable CSRF for simple REST API usage
                .authorizeHttpRequests(auth -> auth
                        .anyRequest().permitAll() // Allow ALL requests (We handle auth logic manually in code)
                );

        return http.build();
    }

    /**
     * The tool used to hash passwords.
     * 10 rounds is a good balance between security and speed.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(10);
    }
}