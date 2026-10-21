package com.procuremind.platform.system;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class SecurityUtils {
    private SecurityUtils() {}
    public static CurrentUser currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof CurrentUser user)) throw new IllegalStateException("未登录");
        return user;
    }
    public static Long userIdOrSystem() {
        try { return currentUser().id(); } catch (Exception ignored) { return 0L; }
    }
}
