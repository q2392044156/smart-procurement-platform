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

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final AuthService auth;
    public AuthController(AuthService auth) { this.auth = auth; }
    @PostMapping("/login") ApiResponse<TokenPair> login(@Valid @RequestBody LoginRequest request) { return ApiResponse.ok(auth.login(request.username(), request.password())); }
    @PostMapping("/refresh") ApiResponse<TokenPair> refresh(@Valid @RequestBody RefreshRequest request) { return ApiResponse.ok(auth.refresh(request.refreshToken())); }
    @PostMapping("/logout") ApiResponse<Void> logout(@RequestBody RefreshRequest request) { auth.logout(request.refreshToken()); return ApiResponse.ok(null); }
    @GetMapping("/me") ApiResponse<CurrentUser> me() { return ApiResponse.ok(SecurityUtils.currentUser()); }
    
    
}
