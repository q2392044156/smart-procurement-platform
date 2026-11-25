package com.procuremind.platform.pojo.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Positive;
import java.util.Set;

public record UserUpdateRequest(
            @NotBlank @Size(max = 100) String displayName,
            @NotNull @Positive Long departmentId,
            @NotEmpty Set<@Positive Long> roleIds) {}
