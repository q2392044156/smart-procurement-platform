package com.procuremind.platform.pojo.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record SupplierStatusRequest(@NotBlank @Pattern(regexp="PENDING|QUALIFIED|SUSPENDED|BLACKLISTED") String status){}
