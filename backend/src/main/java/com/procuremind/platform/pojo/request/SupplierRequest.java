package com.procuremind.platform.pojo.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Email;

public record SupplierRequest(@NotBlank @Pattern(regexp="[A-Z][A-Z0-9_-]{1,49}") String code,@NotBlank @Size(max=200) String name,@Size(max=50) String unifiedCreditCode,@NotBlank @Pattern(regexp="A|B|C|D") String level,@NotBlank @Pattern(regexp="PENDING|QUALIFIED|SUSPENDED|BLACKLISTED") String status,@Size(max=100) String contactName,@Size(max=50) String contactPhone,@Email @Size(max=100) String email,@Size(max=300) String address){}
