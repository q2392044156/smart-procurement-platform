package com.procuremind.platform.pojo;

import com.procuremind.platform.anno.CurrentUser;


public record TokenPair(String accessToken, String refreshToken, long expiresIn, CurrentUser user) {}
