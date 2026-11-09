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
import java.util.Arrays;
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
        if(!enabled){reject(response,HttpServletResponse.SC_NOT_FOUND,"Agent 内部接口未启用");return;}
        String token=request.getHeader("X-Internal-Token");
        if(token==null){reject(response,HttpServletResponse.SC_UNAUTHORIZED,"缺少内部令牌");return;}
        byte[] supplied=token.getBytes(StandardCharsets.UTF_8);
        if(!MessageDigest.isEqual(expected,supplied)){reject(response,HttpServletResponse.SC_UNAUTHORIZED,"内部令牌无效");return;}
        try{Long userId=Long.valueOf(request.getHeader("X-User-Id"));CurrentUser user=users.current(userId);if(!authorized(request,user)){reject(response,HttpServletResponse.SC_FORBIDDEN,"无权访问该业务接口");return;}var authorities=Stream.concat(user.roles().stream().map(v->new SimpleGrantedAuthority("ROLE_"+v)),user.permissions().stream().map(SimpleGrantedAuthority::new)).toList();SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user,null,authorities));chain.doFilter(request,response);}catch(NumberFormatException e){reject(response,HttpServletResponse.SC_UNAUTHORIZED,"内部用户身份无效");}catch(com.procuremind.platform.common.BusinessException e){reject(response,HttpServletResponse.SC_UNAUTHORIZED,"内部用户身份无效");}finally{SecurityContextHolder.clearContext();}
    }
    /** 直接写回 JSON，避免 sendError 触发 /error 二次派发后被安全链改写成 401。 */
    private void reject(HttpServletResponse response,int status,String message)throws IOException{
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":"+status+",\"message\":\""+message+"\",\"data\":null}");
    }
    private boolean authorized(HttpServletRequest request,CurrentUser user){
        String path=request.getRequestURI();boolean read="GET".equals(request.getMethod());
        String[] permissions;
        if(path.startsWith("/api/suppliers/"))permissions=read?new String[]{"supplier:view"}:new String[]{"supplier:edit","supplier:profile:edit"};
        else if(path.startsWith("/api/parts/"))permissions=read?new String[]{"masterdata:view"}:new String[]{"masterdata:edit","masterdata:material:edit"};
        else if(path.startsWith("/api/orders/"))permissions=read?new String[]{"purchaseorder:view"}:new String[]{"purchaseorder:edit"};
        else if(path.startsWith("/api/inventory/"))permissions=read?new String[]{"inventory:view"}:new String[]{"inventory:receive"};
        else if(path.startsWith("/api/requisitions/")&&path.endsWith("/convert"))permissions=new String[]{"purchaseorder:edit"};
        else if(path.equals("/api/requisitions")||path.startsWith("/api/requisitions/"))permissions=read?new String[]{"requisition:view"}:new String[]{"requisition:create"};
        else if(path.startsWith("/api/workflow/"))permissions=new String[]{"workflow:approve"};
        else if(path.startsWith("/api/receipts/"))permissions=new String[]{"inventory:view"};
        else if(path.startsWith("/api/dashboard/"))permissions=new String[]{"dashboard:view"};
        else if(path.startsWith("/api/alerts/"))permissions=new String[]{"dashboard:view"};
        else if(path.startsWith("/api/kb/"))permissions=new String[]{"kb:view"};
        else if(path.startsWith("/api/analytics/"))permissions=new String[]{"dashboard:view"};
        else if(path.equals("/api/warehouses")||path.startsWith("/api/warehouses/"))permissions=new String[]{"masterdata:view"};
        else if(path.equals("/api/budgets")||path.startsWith("/api/budgets/"))permissions=new String[]{"budget:view"};
        else if(path.startsWith("/api/contracts/"))permissions=new String[]{"contract:view"};
        else permissions=null;
        return permissions!=null&&(user.roles().contains("ADMIN")||Arrays.stream(permissions).anyMatch(user.permissions()::contains));
    }
}
