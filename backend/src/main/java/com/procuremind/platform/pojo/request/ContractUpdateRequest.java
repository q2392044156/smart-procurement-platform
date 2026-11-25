package com.procuremind.platform.pojo.request;

import java.math.BigDecimal;
import java.time.LocalDate;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Pattern;

public record ContractUpdateRequest(
            @NotBlank @Size(max = 200) String name,
            @NotNull @Positive Long supplierId,
            Long purchaseOrderId,
            @NotBlank @Pattern(regexp = "FRAMEWORK|PURCHASE|SERVICE|NDA") String type,
            @NotNull @DecimalMin("0") BigDecimal amount,
            LocalDate signedDate, LocalDate startDate, LocalDate endDate,
            Long fileId,
            @Size(max = 500) String remark) {}
