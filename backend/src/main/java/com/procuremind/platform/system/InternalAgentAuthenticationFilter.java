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
    private final UserDirectory users; private final byte[] expected; private final boolean enabled;
    public InternalAgentAuthenticationFilter(UserDirectory users,@Value("${procuremind.agent.internal-token}")String token,@Value("${procuremind.agent.enabled}")boolean enabled){
        this.users=users;this.enabled=enabled;
        if(enabled&&(token==null||token.length()<32||"change-me".equals(token)||token.startsWith("replace-with-")))throw new IllegalStateException("启用Agent时必须配置至少32位的随机AGENT_INTERNAL_TOKEN");
        this.expected=(token==null?"":token).getBytes(StandardCharsets.UTF_8);
    }
    @Override protected boolean shouldNotFilter(HttpServletRequest request){String path=request.getRequestURI();return path.startsWith("/api/v1/")||!path.startsWith("/api/");}
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)throws ServletException,IOException{
        if(!enabled){response.setStatus(HttpServletResponse.SC_NOT_FOUND);return;}
        String token=request.getHeader("X-Internal-Token");
        if(token==null){response.sendError(401,"Internal token required");return;}
        byte[] supplied=token.getBytes(StandardCharsets.UTF_8);
        if(!MessageDigest.isEqual(expected,supplied)){response.sendError(401,"Invalid internal token");return;}
        try{Long userId=Long.valueOf(request.getHeader("X-User-Id"));CurrentUser user=users.current(userId);if(!authorized(request,user)){response.sendError(403,"Business permission denied");return;}var authorities=Stream.concat(user.roles().stream().map(v->new SimpleGrantedAuthority("ROLE_"+v)),user.permissions().stream().map(SimpleGrantedAuthority::new)).toList();SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user,null,authorities));chain.doFilter(request,response);}catch(NumberFormatException e){response.sendError(401,"Invalid internal user identity");}catch(com.procuremind.platform.common.BusinessException e){response.sendError(401,"Invalid internal user identity");}finally{SecurityContextHolder.clearContext();}
    }
    private boolean authorized(HttpServletRequest request,CurrentUser user){String path=request.getRequestURI();boolean read="GET".equals(request.getMethod());String permission=path.startsWith("/api/suppliers/")?(read?"supplier:view":"supplier:edit"):path.startsWith("/api/parts/")?(read?"masterdata:view":"masterdata:edit"):path.startsWith("/api/orders/")?(read?"purchaseorder:view":"purchaseorder:edit"):path.startsWith("/api/inventory/")?(read?"inventory:view":"inventory:receive"):null;return permission!=null&&(user.roles().contains("ADMIN")||user.permissions().contains(permission));}
}
