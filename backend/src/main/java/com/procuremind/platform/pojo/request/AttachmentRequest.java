package com.procuremind.platform.pojo.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Pattern;

public record AttachmentRequest(@NotNull @Positive Long fileId,@NotBlank @Pattern(regexp="REQUEST|QUOTATION|CONTRACT|TECHNICAL|OTHER") String category,@Size(max=300) String note){}
