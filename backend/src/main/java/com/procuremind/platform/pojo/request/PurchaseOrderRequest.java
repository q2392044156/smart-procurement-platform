package com.procuremind.platform.pojo.request;

import java.math.BigDecimal;
import java.time.LocalDate;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.FutureOrPresent;
import java.util.Map;

public record PurchaseOrderRequest(@NotNull Long supplierId, @NotNull @FutureOrPresent LocalDate deliveryDate, @NotNull @DecimalMin("0") @DecimalMax("1") BigDecimal taxRate, @Size(max=1000) String terms, Map<String,BigDecimal> priceOverrides) {}
