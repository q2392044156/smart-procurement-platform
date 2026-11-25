package com.procuremind.platform.pojo.request;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.FutureOrPresent;

public record PurchaseOrderDirectRequest(Long supplierId, @FutureOrPresent LocalDate deliveryDate, @DecimalMin("0") @DecimalMax("1") BigDecimal taxRate, @Size(max=1000) String terms, @NotEmpty @Size(max=100) List<@Valid PurchaseOrderDirectItem> items) {}
