package com.procuremind.platform.pojo.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Pattern;
import java.util.Set;

public record RoleRequest(
            @NotBlank @Pattern(regexp = "[A-Z][A-Z0-9_]{2,63}", message = "角色编码需为大写字母和下划线") String code,
            @NotBlank @Size(max = 100) String name,
            @NotBlank String dataScope,
            @NotNull Set<@Positive Long> permissionIds) {}
