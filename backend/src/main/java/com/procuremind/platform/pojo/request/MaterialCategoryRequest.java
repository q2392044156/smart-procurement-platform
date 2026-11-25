package com.procuremind.platform.pojo.request;

import java.util.List;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Min;

public record MaterialCategoryRequest(
            @Positive Long parentId,
            @NotBlank @Pattern(regexp = "[A-Z][A-Z0-9_-]{1,49}", message = "分类编码需为大写字母开头") String code,
            @NotBlank @Size(max = 100) String name,
            @Size(max = 500) String description,
            @Min(0) Integer sortOrder,
            @Size(max = 30) List<@Valid MaterialAttributeRequest> attributes) {}
