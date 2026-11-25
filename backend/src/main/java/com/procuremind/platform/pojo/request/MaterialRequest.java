package com.procuremind.platform.pojo.request;

import java.math.BigDecimal;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Pattern;
import java.util.Map;

public record MaterialRequest(
            @NotBlank @Pattern(regexp = "[A-Z0-9][A-Z0-9._-]{1,49}", message = "物资编码格式不正确") String code,
            @NotBlank @Size(max = 200) String name,
            @NotNull @Positive Long categoryId,
            @NotNull @Positive Long unitId,
            @Positive Long brandId,
            @Size(max = 500) String specification,
            @NotBlank @Pattern(regexp = "QUANTITY|BATCH|SERIAL") String trackingMode,
            @DecimalMin("0") BigDecimal minStock,
            @Size(max = 30) Map<String, Object> attributes) {}
