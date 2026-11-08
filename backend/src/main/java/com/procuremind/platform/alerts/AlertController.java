package com.procuremind.platform.alerts;

import com.procuremind.platform.common.ApiResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** 到期与超时提醒查询接口。 */
@RestController
@RequestMapping("/api/v1/alerts")
@PreAuthorize("hasAuthority('dashboard:view')")
public class AlertController {
    private final AlertService service;

    public AlertController(AlertService service) {
        this.service = service;
    }

    @GetMapping("/summary")
    public ApiResponse<Map<String, Object>> summary(@RequestParam(defaultValue = "30") int qualificationDays,
                                                    @RequestParam(defaultValue = "7") int deliveryDays,
                                                    @RequestParam(defaultValue = "24") int approvalHours) {
        return ApiResponse.ok(service.summary(qualificationDays, deliveryDays, approvalHours));
    }
}
