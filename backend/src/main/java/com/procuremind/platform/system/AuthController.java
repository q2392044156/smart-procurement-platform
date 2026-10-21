package com.procuremind.platform.system;

import com.procuremind.platform.common.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final AuthService auth;
    public AuthController(AuthService auth) { this.auth = auth; }
    @PostMapping("/login") ApiResponse<AuthService.TokenPair> login(@Valid @RequestBody LoginRequest request) { return ApiResponse.ok(auth.login(request.username(), request.password())); }
    @PostMapping("/refresh") ApiResponse<AuthService.TokenPair> refresh(@Valid @RequestBody RefreshRequest request) { return ApiResponse.ok(auth.refresh(request.refreshToken())); }
    @PostMapping("/logout") ApiResponse<Void> logout(@RequestBody RefreshRequest request) { auth.logout(request.refreshToken()); return ApiResponse.ok(null); }
    @GetMapping("/me") ApiResponse<CurrentUser> me() { return ApiResponse.ok(SecurityUtils.currentUser()); }
    public record LoginRequest(@NotBlank String username, @NotBlank String password) {}
    public record RefreshRequest(@NotBlank String refreshToken) {}
}
