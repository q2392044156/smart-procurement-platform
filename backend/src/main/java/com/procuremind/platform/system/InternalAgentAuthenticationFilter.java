package com.procuremind.platform.system;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.stream.Stream;

@Component
public class InternalAgentAuthenticationFilter extends OncePerRequestFilter {
    private final UserDirectory users; private final byte[] expected;
    public InternalAgentAuthenticationFilter(UserDirectory users,@Value("${procuremind.agent.internal-token}")String token){this.users=users;this.expected=token.getBytes(StandardCharsets.UTF_8);}
    @Override protected boolean shouldNotFilter(HttpServletRequest request){String path=request.getRequestURI();return path.startsWith("/api/v1/")||!path.startsWith("/api/");}
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)throws ServletException,IOException{
        String token=request.getHeader("X-Internal-Token");
        if(token==null){response.sendError(401,"Internal token required");return;}
        byte[] supplied=token.getBytes(StandardCharsets.UTF_8);
        if(!MessageDigest.isEqual(expected,supplied)){response.sendError(401,"Invalid internal token");return;}
        try{Long userId=Long.valueOf(request.getHeader("X-User-Id"));CurrentUser user=users.current(userId);var authorities=Stream.concat(user.roles().stream().map(v->new SimpleGrantedAuthority("ROLE_"+v)),user.permissions().stream().map(SimpleGrantedAuthority::new)).toList();SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user,null,authorities));chain.doFilter(request,response);}catch(Exception e){response.sendError(401,"Invalid internal user identity");}finally{SecurityContextHolder.clearContext();}
    }
}
