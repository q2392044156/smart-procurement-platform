package com.procuremind.platform.pojo.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;

public record BrandRequest(
            @NotBlank @Pattern(regexp = "[A-Z][A-Z0-9_-]{1,49}", message = "品牌编码格式不正确") String code,
            @NotBlank @Size(max = 100) String name) {}
