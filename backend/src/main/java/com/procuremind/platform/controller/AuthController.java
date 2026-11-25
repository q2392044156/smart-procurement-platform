package com.procuremind.platform.controller;

import com.procuremind.platform.service.AuthService;
import com.procuremind.platform.anno.CurrentUser;
import com.procuremind.platform.utils.SecurityUtils;

import com.procuremind.platform.pojo.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.*;
import com.procuremind.platform.pojo.TokenPair;
import com.procuremind.platform.pojo.request.LoginRequest;
import com.procuremind.platform.pojo.request.RefreshRequest;

/**
 * 认证入口：登录换发令牌、刷新、登出与当前身份回显，路由前缀 /api/v1/auth。
 * login 与 refresh 在 SecurityConfig 中 permitAll，logout 与 me 仍需已认证；本类不校验密码策略、不签发令牌、
 * 不保存会话——那部分在 AuthService 与 JwtService，这里只做参数绑定与响应包装。
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final AuthService auth;
    public AuthController(AuthService auth) { this.auth = auth; }
    // 登录：匿名可调，用户名+密码换 access/refresh 令牌对；认证失败由 AuthService 抛 BusinessException(401)。
    @PostMapping("/login") ApiResponse<TokenPair> login(@Valid @RequestBody LoginRequest request) { return ApiResponse.ok(auth.login(request.username(), request.password())); }
    // 刷新：匿名可调，用 refresh token 换新令牌对；已吊销或过期的 token 由 Service 拒绝。
    @PostMapping("/refresh") ApiResponse<TokenPair> refresh(@Valid @RequestBody RefreshRequest request) { return ApiResponse.ok(auth.refresh(request.refreshToken())); }
    // 登出：需已认证，作废该 refresh token；access token 无状态、到期前仍有效，因此不是即时下线。
    @PostMapping("/logout") ApiResponse<Void> logout(@RequestBody RefreshRequest request) { auth.logout(request.refreshToken()); return ApiResponse.ok(null); }
    // 当前身份：返回 CurrentUser（id、用户名、显示名、部门、角色与权限码），前端据此渲染菜单与按钮。
    @GetMapping("/me") ApiResponse<CurrentUser> me() { return ApiResponse.ok(SecurityUtils.currentUser()); }
    
    
}
