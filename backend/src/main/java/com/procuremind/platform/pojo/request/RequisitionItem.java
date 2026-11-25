package com.procuremind.platform.pojo.request;

import java.math.BigDecimal;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.DecimalMin;

public record RequisitionItem(@NotNull Long materialId,
                       @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal quantity,
                       @NotNull @DecimalMin("0") BigDecimal estimatedUnitPrice,
                       @Size(max = 500) String remark) {}
