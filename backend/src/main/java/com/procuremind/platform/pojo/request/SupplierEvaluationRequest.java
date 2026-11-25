package com.procuremind.platform.pojo.request;

import java.math.BigDecimal;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Pattern;

public record SupplierEvaluationRequest(@NotBlank @Pattern(regexp="20\\d{2}-Q[1-4]") String period,@NotNull @DecimalMin("0") @DecimalMax("100") BigDecimal qualityScore,@NotNull @DecimalMin("0") @DecimalMax("100") BigDecimal deliveryScore,@NotNull @DecimalMin("0") @DecimalMax("100") BigDecimal serviceScore,@Size(max=500) String comment){}
