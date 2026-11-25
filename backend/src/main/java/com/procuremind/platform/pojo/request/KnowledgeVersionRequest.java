package com.procuremind.platform.pojo.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Positive;

public record KnowledgeVersionRequest(@NotNull @Positive Long fileId, @Size(max = 300) String changeNote) {}
