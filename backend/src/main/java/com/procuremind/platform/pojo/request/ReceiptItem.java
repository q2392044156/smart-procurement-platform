package com.procuremind.platform.pojo.request;

import java.math.BigDecimal;
import java.util.List;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.DecimalMin;

public record ReceiptItem(@NotNull Long orderItemId,
                       @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal quantity,
                       @Size(max = 100) String batchNo, List<@Size(max = 200) String> serialNumbers) {
    }
