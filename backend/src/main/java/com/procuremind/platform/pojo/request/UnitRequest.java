package com.procuremind.platform.pojo.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;

public record UnitRequest(
            @NotBlank @Pattern(regexp = "[A-Z][A-Z0-9_-]{0,29}", message = "单位编码格式不正确") String code,
            @NotBlank @Size(max = 50) String name,
            @NotNull @Min(0) @Max(6) Integer precisionScale) {}
