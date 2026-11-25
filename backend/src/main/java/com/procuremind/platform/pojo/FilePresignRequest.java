package com.procuremind.platform.pojo;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 预签名上传请求：文件名、内容类型、字节数。
 * 放在 pojo 而不是 Controller 内部，是为了让 Service 的入参不依赖 Web 层。
 */
public record FilePresignRequest(
        @NotBlank @Size(max = 255) String fileName,
        @NotBlank @Pattern(regexp = "application/pdf|image/jpeg|image/png|application/octet-stream") String contentType,
        @NotNull @Min(1) @Max(52428800) Long size) {
}
