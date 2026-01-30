package com.centralizesys.config;

import com.centralizesys.security.JwtAuthenticationFilter;
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

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true) // Allows using @PreAuthorize in our Controllers/Services
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
}