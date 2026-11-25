package com.procuremind.platform.pojo.request;

import java.time.LocalDate;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Positive;

public record SupplierQualificationRequest(@NotBlank @Size(max=200) String name,@Positive Long fileId,LocalDate validFrom,LocalDate validUntil){}
