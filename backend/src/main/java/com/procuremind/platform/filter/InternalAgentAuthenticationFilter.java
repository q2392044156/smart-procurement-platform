package com.procuremind.platform.filter;

import com.procuremind.platform.exception.BusinessException;
import com.procuremind.platform.anno.CurrentUser;
import com.procuremind.platform.service.UserDirectory;

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

/**
 * Agent 内部接口的令牌认证与路径级权限矩阵，负责 /api/** 这批给 Agent 调的业务路由。
 * 范围：只处理以 /api/ 开头且不以 /api/v1/ 开头的路径，其余跳过——浏览器用的 /api/v1 仍走 JWT。
 * 放行条件（依次）：agent.enabled=true、X-Internal-Token 与配置常量时间相等、X-User-Id 解析成真实用户、
 * 且该用户权限满足路径矩阵（ADMIN 角色直接放行，见 authorized()）。
 * 失败时的状态码与文案全部来自本类常量，不经过全局异常处理器：
 * 未启用 → 404 "Agent 内部接口未启用"；缺令牌 → 401 "缺少内部令牌"；令牌不符 → 401 "内部令牌无效"；
 * 用户身份解析失败 → 401 "内部用户身份无效"；路径权限不足 → 403 "无权访问该业务接口"。
 * 构造器是启动期自检：启用时令牌为空、短于 32 位、或为占位值（change-me / replace-with-*）直接启动失败。
 */
@Component
public class InternalAgentAuthenticationFilter extends OncePerRequestFilter {
    // 令牌以 byte[] 保存，比较走 MessageDigest.isEqual 做常量时间比较，避免按字节短路泄漏前缀。
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
        try{Long userId=Long.valueOf(request.getHeader("X-User-Id"));CurrentUser user=users.current(userId);if(!authorized(request,user)){reject(response,HttpServletResponse.SC_FORBIDDEN,"无权访问该业务接口");return;}var authorities=Stream.concat(user.roles().stream().map(v->new SimpleGrantedAuthority("ROLE_"+v)),user.permissions().stream().map(SimpleGrantedAuthority::new)).toList();SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user,null,authorities));chain.doFilter(request,response);}catch(NumberFormatException e){reject(response,HttpServletResponse.SC_UNAUTHORIZED,"内部用户身份无效");}catch(com.procuremind.platform.exception.BusinessException e){reject(response,HttpServletResponse.SC_UNAUTHORIZED,"内部用户身份无效");}finally{SecurityContextHolder.clearContext();}
    }
    /** 直接写回 JSON，避免 sendError 触发 /error 二次派发后被安全链改写成 401。 */
    private void reject(HttpServletResponse response,int status,String message)throws IOException{
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":"+status+",\"message\":\""+message+"\",\"data\":null}");
    }
    private boolean authorized(HttpServletRequest request,CurrentUser user){
        String path=request.getRequestURI();boolean read="GET".equals(request.getMethod());
        // 下面是路径 → 权限矩阵：读用 *:view，写用各域的编辑/创建码。
        String[] permissions;
        if(path.startsWith("/api/suppliers/"))permissions=read?new String[]{"supplier:view"}:new String[]{"supplier:edit","supplier:profile:edit"};
        else if(path.startsWith("/api/parts/"))permissions=read?new String[]{"masterdata:view"}:new String[]{"masterdata:edit","masterdata:material:edit"};
        else if(path.startsWith("/api/orders/"))permissions=read?new String[]{"purchaseorder:view"}:new String[]{"purchaseorder:edit"};
        else if(path.startsWith("/api/inventory/"))permissions=read?new String[]{"inventory:view"}:new String[]{"inventory:receive"};
        // convert 分支必须先于下面的 /api/requisitions/ 通用判断，否则会被“申请创建”权限吞掉。
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
        // 未列出的路径 permissions 为 null，一律拒绝；ADMIN 角色跳过矩阵。
        return permissions!=null&&(user.roles().contains("ADMIN")||Arrays.stream(permissions).anyMatch(user.permissions()::contains));
    }
}
