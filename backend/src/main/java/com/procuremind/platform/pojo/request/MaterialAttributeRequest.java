package com.procuremind.platform.pojo.request;

import java.util.List;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Min;

public record MaterialAttributeRequest(
            @NotBlank @Pattern(regexp = "[A-Za-z][A-Za-z0-9_]{1,49}", message = "属性编码格式不正确") String code,
            @NotBlank @Size(max = 100) String name,
            @NotBlank @Pattern(regexp = "TEXT|NUMBER|BOOLEAN|DATE|ENUM") String valueType,
            Boolean required,
            @Size(max = 50) List<@NotBlank @Size(max = 100) String> options,
            @Min(0) Integer sortOrder) {}
