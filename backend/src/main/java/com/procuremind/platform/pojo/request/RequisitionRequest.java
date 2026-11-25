package com.procuremind.platform.pojo.request;

import java.time.LocalDate;
import java.util.List;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.FutureOrPresent;

public record RequisitionRequest(@NotNull @FutureOrPresent LocalDate requiredDate,
                          @NotBlank @Size(max = 500) String purpose,
                          @NotEmpty @Size(max = 100) List<@Valid RequisitionItem> items) {}
