package com.procuremind.platform.pojo;


public record UserRecord(Long id, String username, String passwordHash, String displayName, Long departmentId, boolean enabled) {}
