package com.procuremind.platform.controller;

import com.procuremind.platform.service.DashboardService;

import com.procuremind.platform.pojo.ApiResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController @RequestMapping("/api/v1/dashboard") @PreAuthorize("hasAuthority('dashboard:view')")
public class DashboardController {
    private final DashboardService service;public DashboardController(DashboardService service){this.service=service;}
    @GetMapping("/summary") public ApiResponse<Map<String,Object>> summary(){return ApiResponse.ok(service.summary());}
}
