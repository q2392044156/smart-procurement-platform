package com.procuremind.platform.system;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.stream.Stream;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtService jwt;
    private final UserDirectory users;
    public JwtAuthenticationFilter(JwtService jwt, UserDirectory users) { this.jwt = jwt; this.users = users; }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String value = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (value != null && value.startsWith("Bearer ")) {
            try {
                Claims claims = jwt.parse(value.substring(7));
                if ("access".equals(claims.get("type", String.class))) {
                    CurrentUser user = users.current(Long.valueOf(claims.getSubject()));
                    var authorities = Stream.concat(user.roles().stream().map(v -> new SimpleGrantedAuthority("ROLE_" + v)),
                            user.permissions().stream().map(SimpleGrantedAuthority::new)).toList();
                    SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user, null, authorities));
                }
            } catch (Exception ignored) { SecurityContextHolder.clearContext(); }
        }
        chain.doFilter(request, response);
    }
}
