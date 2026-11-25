package com.procuremind.platform.pojo.request;

import jakarta.validation.constraints.NotNull;

public record SystemStatusRequest(@NotNull Boolean enabled) {}
