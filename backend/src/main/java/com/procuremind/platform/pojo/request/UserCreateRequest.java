package com.procuremind.platform.pojo.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Pattern;
import java.util.Set;

public record UserCreateRequest(
            @NotBlank @Pattern(regexp = "[A-Za-z][A-Za-z0-9._-]{2,63}", message = "用户名格式不正确") String username,
            @NotBlank @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z\\d]).{8,72}$", message = "密码至少8位且需包含大小写字母、数字和特殊字符") String password,
            @NotBlank @Size(max = 100) String displayName,
            @NotNull @Positive Long departmentId,
            @NotEmpty Set<@Positive Long> roleIds) {}
