package com.procuremind.platform.pojo.request;

import java.math.BigDecimal;
import java.util.List;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.DecimalMin;

public record IssueRequest(
            @NotNull @Positive Long warehouseId,
            @NotNull @Positive Long materialId,
            @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal quantity,
            @Size(max = 100) String batchNo,
            List<@Size(max = 200) String> serialNumbers,
            @NotBlank @Size(max = 200) String purpose,
            Long departmentId,
            @Size(max = 500) String remark) {}
