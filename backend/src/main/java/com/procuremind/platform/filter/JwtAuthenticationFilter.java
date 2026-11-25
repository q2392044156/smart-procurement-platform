package com.procuremind.platform.filter;

import com.procuremind.platform.anno.CurrentUser;
import com.procuremind.platform.service.JwtService;
import com.procuremind.platform.service.UserDirectory;

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

/**
 * JWT 认证过滤器：把 Authorization 头里的访问令牌还原成 SecurityContext 中的 CurrentUser。
 * 范围：对全部请求生效（含 SecurityConfig 放行的登录/刷新路径，只是没有 Authorization 头时不做事）。
 * 本过滤器只“填充身份”不做拦截：没有令牌或令牌非法时保持匿名并继续放行，拦截由安全链决定。
 * 放行条件：头以 "Bearer " 开头、签名与有效期校验通过、claims.type 为 access（refresh 令牌不能用）。
 * 失败时没有状态码与文案：异常一律清空上下文后放行，401 "登录已失效" 与 403 "无权执行此操作"
 * 由 SecurityConfig 的 authenticationEntryPoint 与 accessDeniedHandler 输出，文案不在这里维护。
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtService jwt;
    private final UserDirectory users;
    public JwtAuthenticationFilter(JwtService jwt, UserDirectory users) { this.jwt = jwt; this.users = users; }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        // parse 校验签名与过期时间；subject 是用户 id，再查一次目录是为了拿到最新角色/权限（改权限即时生效）。
        String value = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (value != null && value.startsWith("Bearer ")) {
            try {
                Claims claims = jwt.parse(value.substring(7));
                if ("access".equals(claims.get("type", String.class))) {
                    CurrentUser user = users.current(Long.valueOf(claims.getSubject()));
                    // ROLE_ 前缀给 hasRole 用，裸权限码给 hasAuthority 用，两种 authority 挂在同一个认证对象上。
                    var authorities = Stream.concat(user.roles().stream().map(v -> new SimpleGrantedAuthority("ROLE_" + v)),
                            user.permissions().stream().map(SimpleGrantedAuthority::new)).toList();
                    SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user, null, authorities));
                }
            } catch (Exception ignored) { SecurityContextHolder.clearContext(); }
        }
        chain.doFilter(request, response);
    }
}
