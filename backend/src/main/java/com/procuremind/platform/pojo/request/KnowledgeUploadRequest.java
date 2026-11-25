package com.procuremind.platform.pojo.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Max;

public record KnowledgeUploadRequest(
            @NotBlank @Size(max = 255) String fileName,
            @NotBlank @Pattern(regexp = "application/pdf|application/msword"
                    + "|application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                    + "|application/vnd.ms-excel|application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                    + "|application/vnd.ms-powerpoint|application/vnd.openxmlformats-officedocument.presentationml.presentation"
                    + "|text/plain|text/markdown") String contentType,
            @NotNull @Positive @Max(52428800) Long size) {}
