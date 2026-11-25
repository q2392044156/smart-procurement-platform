package com.procuremind.platform.pojo.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Pattern;

public record DepartmentRequest(
            @Positive Long parentId,
            @NotBlank @Pattern(regexp = "[A-Z][A-Z0-9_-]{1,49}", message = "部门编码需为大写字母开头") String code,
            @NotBlank @Size(max = 100) String name,
            Integer sortOrder) {}
