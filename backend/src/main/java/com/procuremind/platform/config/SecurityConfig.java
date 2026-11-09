package com.procuremind.platform.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.procuremind.platform.common.ApiResponse;
import com.procuremind.platform.system.JwtAuthenticationFilter;
import com.procuremind.platform.system.InternalAgentAuthenticationFilter;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {
    @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }
    @Bean SecurityFilterChain security(HttpSecurity http, JwtAuthenticationFilter jwt, InternalAgentAuthenticationFilter internalAgent, ObjectMapper mapper) throws Exception {
        return http.csrf(csrf -> csrf.disable()).cors(cors -> {})
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a
                        .dispatcherTypeMatchers(DispatcherType.ASYNC, DispatcherType.ERROR).permitAll()
                        .requestMatchers("/api/v1/auth/login", "/api/v1/auth/refresh", "/actuator/health", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((req, res, ex) -> write(res, mapper, 401, "登录已失效"))
                        .accessDeniedHandler((req, res, ex) -> write(res, mapper, 403, "无权执行此操作")))
                .addFilterBefore(jwt, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(internalAgent, JwtAuthenticationFilter.class).build();
    }
    private static void write(HttpServletResponse response, ObjectMapper mapper, int status, String message) throws java.io.IOException {
        response.setStatus(status); response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        mapper.writeValue(response.getOutputStream(), ApiResponse.error(status, message));
    }
}
