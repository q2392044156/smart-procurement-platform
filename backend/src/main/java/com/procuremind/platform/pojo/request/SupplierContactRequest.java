package com.procuremind.platform.pojo.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Email;

public record SupplierContactRequest(@NotBlank @Size(max=100) String name,@Size(max=100) String position,@Size(max=50) String phone,@Email @Size(max=100) String email,@NotNull Boolean primaryContact){}
