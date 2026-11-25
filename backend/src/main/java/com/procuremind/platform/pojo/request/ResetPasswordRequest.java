package com.procuremind.platform.pojo.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ResetPasswordRequest(
            @NotBlank @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z\\d]).{8,72}$", message = "密码至少8位且需包含大小写字母、数字和特殊字符") String password) {}
