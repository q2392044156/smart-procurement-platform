package com.procuremind.platform.pojo.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;

public record KnowledgeAclEntry(@NotBlank @Pattern(regexp = "USER|ROLE") String subjectType, @NotBlank @Size(max = 64) String subjectKey) {}
