package com.procuremind.platform.pojo.request;

import java.math.BigDecimal;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.DecimalMin;

public record PurchaseOrderUpdateItem(@NotNull Long id, @NotNull @DecimalMin("0") BigDecimal unitPrice, @Size(max=500) String remark) {}
