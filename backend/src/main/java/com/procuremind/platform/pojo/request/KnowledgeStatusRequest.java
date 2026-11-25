package com.procuremind.platform.pojo.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record KnowledgeStatusRequest(@NotBlank @Pattern(regexp = "DRAFT|PUBLISHED|ARCHIVED") String status) {}
