package com.procuremind.platform.dashboard;

import com.procuremind.platform.common.ApiResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController @RequestMapping("/api/v1/dashboard") @PreAuthorize("hasAuthority('dashboard:view')")
public class DashboardController {
    private final DashboardService service;public DashboardController(DashboardService service){this.service=service;}
    @GetMapping("/summary") public ApiResponse<Map<String,Object>> summary(){return ApiResponse.ok(service.summary());}
}
