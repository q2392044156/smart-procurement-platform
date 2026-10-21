package com.procuremind.platform.system;

import java.io.Serializable;
import java.util.Set;

public record CurrentUser(Long id, String username, String displayName, Long departmentId,
                          Set<String> roles, Set<String> permissions) implements Serializable {}
