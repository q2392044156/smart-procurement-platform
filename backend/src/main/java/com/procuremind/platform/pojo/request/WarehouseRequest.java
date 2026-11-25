package com.procuremind.platform.pojo.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;

public record WarehouseRequest(
            @NotBlank @Pattern(regexp = "[A-Z][A-Z0-9_-]{1,49}", message = "仓库编码格式不正确") String code,
            @NotBlank @Size(max = 100) String name,
            @Size(max = 300) String address,
            @Size(max = 100) String manager) {}
