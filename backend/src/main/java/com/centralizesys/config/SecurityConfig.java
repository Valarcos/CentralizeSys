package com.centralizesys.config;

import com.centralizesys.security.JwtAuthenticationFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity // Allows using @PreAuthorize in our Controllers/Services
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    /**
     * Configures the main security filter chain.
     * <p>
     * Architecture choices:
     * 1. **Stateless**: We use JWTs, so we don't need server-side sessions
     * (JSESSIONID).
     * 2. **Disable Defaults**: We disable CSRF (not needed for non-browser-session
     * APIs),
     * HttpBasic, and FormLogin because we handle authentication manually via
     * AuthController.
     * 3. **JWT Filter**: Injected BEFORE the standard
     * UsernamePasswordAuthenticationFilter to
     * intercept requests and authenticate via the "Authorization" header.
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // Disable CSRF: Not required for stateless REST APIs using JWT
                // Disable CSRF: Safe because we are stateless and use 'Authorization' headers
                // (not cookies)
                .csrf(AbstractHttpConfigurer::disable)

                // Enable CORS with defaults (looks for a CorsConfigurationSource bean)
                .cors(Customizer.withDefaults())

                // Disable standard browser-based login forms and prompts
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)

                // FORCE Stateless: Spring will *never* create a session.
                // All auth state must come from the JWT for every request.
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // Authorization Rules
                .authorizeHttpRequests(auth -> auth
                        // Public Endpoints: login, register (if exists)
                        .requestMatchers("/api/auth/**").permitAll()

                        // All other requests require a valid JWT
                        .anyRequest().authenticated())

                // Add our custom JWT filter before the standard authentication filter
                // This ensures we extract the user from the token before Spring Security tries
                // to authenticate.
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * Exposes the AuthenticationManager as a Bean.
     * This is required for our AuthController to programmatically trigger
     * authentication checks.
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authenticationConfiguration)
            throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }

    /**
     * Password Encoder: BCrypt.
     * 10 rounds is the current industry standard balance for security vs
     * performance.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(10);
    }

    @Value("${app.cors.allowed-origins:http://localhost:3000}")
    private String allowedOrigins;

    /**
     * Global CORS Configuration.
     * Prepares backend for Frontend Integration (Phase 3).
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        // Allowed Origins: Injected from application.properties
        // Supports comma-separated values (e.g.
        // "http://localhost:3000,http://192.168.1.5:3000")
        configuration.setAllowedOriginPatterns(List.of(allowedOrigins.split(",")));

        // Allowed Methods
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));

        // Allowed Headers
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "Cache-Control"));

        // Allow Credentials (if needed for future cookie usage, safe with
        // OriginPatterns)
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}